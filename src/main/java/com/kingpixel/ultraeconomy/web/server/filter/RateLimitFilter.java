package com.kingpixel.ultraeconomy.web.server.filter;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.WebSecurityConfig;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP rate limiting using the Token Bucket algorithm.
 * <p>
 * Features:
 * <ul>
 *   <li>Separate limits for API and static requests</li>
 *   <li>Auto-ban for repeat offenders</li>
 *   <li>X-Forwarded-For support (configurable)</li>
 *   <li>Periodic cleanup of stale entries to prevent memory leaks</li>
 *   <li>Returns 429 (Too Many Requests) with Retry-After header</li>
 *   <li>Returns 403 (Forbidden) for banned IPs</li>
 * </ul>
 *
 * @author Carlos Varas Alonso
 */
public class RateLimitFilter implements Filter {

  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String HEADER_RETRY_AFTER = "Retry-After";
  private static final String JSON_RATE_LIMITED = "{\"error\":\"Too many requests\",\"retryAfter\":%d}";
  private static final String JSON_BANNED = "{\"error\":\"Temporarily banned due to excessive requests\"}";

  private final ConcurrentHashMap<String, TokenBucket> apiBuckets = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, TokenBucket> staticBuckets = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicInteger> violations = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> banned = new ConcurrentHashMap<>();

  private ScheduledExecutorService cleanupExecutor;

  @Override
  public void init(FilterConfig filterConfig) {
    // Schedule cleanup every 60 seconds to evict stale buckets and expired bans
    cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "UltraEconomy-RateLimit-Cleanup");
      t.setDaemon(true);
      return t;
    });
    cleanupExecutor.scheduleWithFixedDelay(this::cleanup, 60, 60, TimeUnit.SECONDS);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {

    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;

    WebSecurityConfig config = getConfig();
    if (config == null || !config.isEnabled()) {
      chain.doFilter(request, response);
      return;
    }

    String clientIp = extractClientIp(req, config.isTrustProxy());

    // ─── Check ban ───
    if (handleBan(clientIp, resp)) return;

    // ─── Rate limit check ───
    String path = req.getRequestURI();
    boolean isApi = path.startsWith("/api/");

    if (!checkRateLimit(clientIp, isApi, config, resp)) return;

    // ─── Allowed — proceed ───
    chain.doFilter(request, response);
  }

  /**
   * Check if the IP is banned. Returns true if request was blocked (caller should return).
   */
  private boolean handleBan(String clientIp, HttpServletResponse resp) throws IOException {
    Long banExpiry = banned.get(clientIp);
    if (banExpiry == null) return false;

    if (System.currentTimeMillis() < banExpiry) {
      long remainingSeconds = (banExpiry - System.currentTimeMillis()) / 1000;
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      resp.setContentType(CONTENT_TYPE_JSON);
      resp.setHeader(HEADER_RETRY_AFTER, String.valueOf(remainingSeconds));
      resp.getWriter().write(JSON_BANNED);
      return true;
    }

    // Ban expired, clean up
    banned.remove(clientIp);
    violations.remove(clientIp);
    return false;
  }

  /**
   * Apply token bucket rate limiting. Returns false if request was blocked (caller should return).
   */
  private boolean checkRateLimit(String clientIp, boolean isApi,
                                 WebSecurityConfig config, HttpServletResponse resp) throws IOException {
    int rateLimit = isApi ? config.getApiRateLimit() : config.getStaticRateLimit();
    if (rateLimit <= 0) return true; // Rate limiting disabled for this type

    ConcurrentHashMap<String, TokenBucket> buckets = isApi ? apiBuckets : staticBuckets;
    int burstCapacity = isApi ? config.getApiBurstCapacity() : config.getStaticBurstCapacity();

    TokenBucket bucket = buckets.computeIfAbsent(clientIp, k ->
      new TokenBucket(burstCapacity, rateLimit));

    if (bucket.tryConsume()) return true; // Allowed

    // ─── Rate limit exceeded ───
    if (config.getBanThreshold() > 0) {
      int count = violations.computeIfAbsent(clientIp, k -> new AtomicInteger(0)).incrementAndGet();
      if (count >= config.getBanThreshold()) {
        long banUntil = System.currentTimeMillis() + (long) config.getBanDurationMinutes() * 60_000L;
        banned.put(clientIp, banUntil);
        UltraEconomy.LOGGER.warn("[WebSecurity] Auto-banned IP {} for {} minutes (exceeded {} violations)",
          clientIp, config.getBanDurationMinutes(), config.getBanThreshold());

        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        resp.setContentType(CONTENT_TYPE_JSON);
        resp.setHeader(HEADER_RETRY_AFTER, String.valueOf(config.getBanDurationMinutes() * 60L));
        resp.getWriter().write(JSON_BANNED);
        return false;
      }
    }

    // Return 429
    int retryAfter = Math.max(1, (int) Math.ceil(1.0 / rateLimit));
    resp.setStatus(429); // SC_TOO_MANY_REQUESTS
    resp.setContentType(CONTENT_TYPE_JSON);
    resp.setHeader(HEADER_RETRY_AFTER, String.valueOf(retryAfter));
    resp.setHeader("X-RateLimit-Limit", String.valueOf(rateLimit));
    resp.setHeader("X-RateLimit-Remaining", "0");
    resp.getWriter().write(String.format(JSON_RATE_LIMITED, retryAfter));
    return false;
  }

  @Override
  public void destroy() {
    if (cleanupExecutor != null) {
      cleanupExecutor.shutdownNow();
    }
    apiBuckets.clear();
    staticBuckets.clear();
    violations.clear();
    banned.clear();
  }

  // =============================
  // Private Helpers
  // =============================

  private WebSecurityConfig getConfig() {
    return UltraEconomy.config != null ? UltraEconomy.config.getWebSecurity() : null;
  }

  /**
   * Extract client IP, optionally trusting X-Forwarded-For for reverse proxy setups.
   */
  private String extractClientIp(HttpServletRequest req, boolean trustProxy) {
    if (trustProxy) {
      String xff = req.getHeader("X-Forwarded-For");
      if (xff != null && !xff.isBlank()) {
        // X-Forwarded-For: client, proxy1, proxy2 → take first (leftmost = original client)
        String clientIp = xff.split(",")[0].trim();
        if (!clientIp.isEmpty()) {
          return clientIp;
        }
      }
      // Also check X-Real-IP (nginx)
      String realIp = req.getHeader("X-Real-IP");
      if (realIp != null && !realIp.isBlank()) {
        return realIp.trim();
      }
    }
    return req.getRemoteAddr();
  }

  /**
   * Periodic cleanup to prevent memory leaks from IPs that haven't been seen in a while.
   */
  private void cleanup() {
    long now = System.currentTimeMillis();

    // Remove stale buckets (no activity for 5 minutes)
    long staleNanoThreshold = System.nanoTime() - TimeUnit.MINUTES.toNanos(5);
    cleanupBuckets(apiBuckets, staleNanoThreshold);
    cleanupBuckets(staticBuckets, staleNanoThreshold);

    // Remove expired bans
    banned.entrySet().removeIf(entry -> now >= entry.getValue());

    // Remove violations for non-banned IPs
    violations.keySet().removeIf(ip -> !banned.containsKey(ip));
  }

  private void cleanupBuckets(ConcurrentHashMap<String, TokenBucket> buckets, long staleNanoThreshold) {
    buckets.entrySet().removeIf(entry -> entry.getValue().getLastAccessNano() < staleNanoThreshold);
  }

  // =============================
  // Token Bucket Implementation
  // =============================

  /**
   * Thread-safe token bucket with configurable capacity and refill rate.
   */
  static final class TokenBucket {
    private final int maxTokens;
    private final double refillRate; // tokens per second
    private double tokens;
    private long lastRefillNano;
    private volatile long lastAccessNano;

    TokenBucket(int maxTokens, double refillRate) {
      this.maxTokens = maxTokens;
      this.refillRate = refillRate;
      this.tokens = maxTokens; // Start full
      this.lastRefillNano = System.nanoTime();
      this.lastAccessNano = System.nanoTime();
    }

    synchronized boolean tryConsume() {
      refill();
      lastAccessNano = System.nanoTime();
      if (tokens >= 1.0) {
        tokens -= 1.0;
        return true;
      }
      return false;
    }

    long getLastAccessNano() {
      return lastAccessNano;
    }

    private void refill() {
      long now = System.nanoTime();
      double elapsedSeconds = (now - lastRefillNano) / 1_000_000_000.0;
      tokens = Math.min(maxTokens, tokens + elapsedSeconds * refillRate);
      lastRefillNano = now;
    }
  }

  // =============================
  // Registration Helper
  // =============================

  /**
   * Convenience method to register this filter on a ServletContextHandler.
   */
  public static void register(org.eclipse.jetty.servlet.ServletContextHandler context) {
    org.eclipse.jetty.servlet.FilterHolder holder = new org.eclipse.jetty.servlet.FilterHolder(new RateLimitFilter());
    context.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));
  }
}






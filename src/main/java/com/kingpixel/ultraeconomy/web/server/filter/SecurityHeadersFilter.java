package com.kingpixel.ultraeconomy.web.server.filter;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.WebSecurityConfig;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Adds essential HTTP security headers to all responses.
 * <p>
 * Headers applied:
 * <ul>
 *   <li><b>X-Content-Type-Options: nosniff</b> — Prevents MIME type sniffing</li>
 *   <li><b>X-Frame-Options: DENY</b> — Prevents clickjacking via iframe embedding</li>
 *   <li><b>X-XSS-Protection: 0</b> — Disables legacy XSS auditor (CSP replaces it)</li>
 *   <li><b>Referrer-Policy: strict-origin-when-cross-origin</b> — Controls referrer information</li>
 *   <li><b>Content-Security-Policy</b> — Controls resource loading origins</li>
 *   <li><b>Permissions-Policy</b> — Restricts browser feature access</li>
 *   <li><b>Cache-Control</b> — Proper caching for API vs static assets</li>
 * </ul>
 *
 * @author Carlos Varas Alonso
 */
public class SecurityHeadersFilter implements Filter {

  private static final String HEADER_CACHE_CONTROL = "Cache-Control";

  /**
   * CSP policy for the SPA:
   * - default-src 'self': only load from same origin by default
   * - script-src 'self' cdn.jsdelivr.net: allow Chart.js CDN
   * - style-src 'self' 'unsafe-inline': allow inline styles (CSS custom properties, dynamic styles)
   * - img-src 'self' minotar.net mc-heads.net crafatar.com: Minecraft avatar services
   * - connect-src 'self': only fetch from same origin
   * - font-src 'self': only local fonts
   * - object-src 'none': block plugins (Flash, Java applets)
   * - base-uri 'self': restrict <base> tag
   * - form-action 'self': restrict form submissions
   */
  private static final String CSP_POLICY =
    "default-src 'self'; " +
      "script-src 'self' https://cdn.jsdelivr.net; " +
      "style-src 'self' 'unsafe-inline'; " +
      "img-src 'self' https://minotar.net https://mc-heads.net https://crafatar.com data:; " +
      "connect-src 'self'; " +
      "font-src 'self'; " +
      "object-src 'none'; " +
      "base-uri 'self'; " +
      "form-action 'self'";

  /**
   * Permissions policy: restrict browser APIs that this app doesn't need.
   */
  private static final String PERMISSIONS_POLICY =
    "camera=(), microphone=(), geolocation=(), payment=(), usb=(), " +
      "magnetometer=(), gyroscope=(), accelerometer=()";

  @Override
  public void init(FilterConfig filterConfig) {
    // No init needed
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {

    WebSecurityConfig config = getConfig();
    if (config == null || !config.isEnabled()) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletResponse resp = (HttpServletResponse) response;

    // ─── Anti-sniffing / Anti-clickjacking ───
    resp.setHeader("X-Content-Type-Options", "nosniff");
    resp.setHeader("X-Frame-Options", "DENY");
    resp.setHeader("X-XSS-Protection", "0"); // Deprecated; CSP replaces it

    // ─── Referrer control ───
    resp.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

    // ─── Content Security Policy ───
    resp.setHeader("Content-Security-Policy", CSP_POLICY);

    // ─── Permissions Policy ───
    resp.setHeader("Permissions-Policy", PERMISSIONS_POLICY);

    // ─── Cache control ───
    String path = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
    if (path.startsWith("/api/")) {
      // API responses should not be cached by shared caches
      resp.setHeader(HEADER_CACHE_CONTROL, "no-store, no-cache, must-revalidate");
      resp.setHeader("Pragma", "no-cache");
    } else if (path.endsWith(".css") || path.endsWith(".js")) {
      // Static assets: cache for 1 hour, revalidate
      resp.setHeader(HEADER_CACHE_CONTROL, "public, max-age=3600, must-revalidate");
    } else if (path.endsWith(".html") || path.equals("/")) {
      // HTML: don't cache (SPA routing)
      resp.setHeader(HEADER_CACHE_CONTROL, "no-cache, must-revalidate");
    }

    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {
    // No cleanup needed
  }

  private WebSecurityConfig getConfig() {
    return UltraEconomy.config != null ? UltraEconomy.config.getWebSecurity() : null;
  }

  // =============================
  // Registration Helper
  // =============================

  /**
   * Convenience method to register this filter on a ServletContextHandler.
   */
  public static void register(org.eclipse.jetty.servlet.ServletContextHandler context) {
    org.eclipse.jetty.servlet.FilterHolder holder =
      new org.eclipse.jetty.servlet.FilterHolder(new SecurityHeadersFilter());
    context.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));
  }
}



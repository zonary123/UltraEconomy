package com.kingpixel.ultraeconomy.web.server.filter;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.WebSecurityConfig;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Adds security headers to every HTTP response.
 * The filter keeps browser behavior predictable by disabling MIME sniffing, blocking
 * clickjacking, narrowing resource origins through CSP, and setting cache policy by route.
 */
public class SecurityHeadersFilter implements Filter {

  private static final String HEADER_CACHE_CONTROL = "Cache-Control";

  /**
   * Content Security Policy for the SPA.
     * The app only allows the CDN used for Chart.js plus the avatar hosts used by the player page.
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

  /** Restrict browser APIs that the web UI does not use. */
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

    resp.setHeader("X-Content-Type-Options", "nosniff");
    resp.setHeader("X-Frame-Options", "DENY");
    resp.setHeader("X-XSS-Protection", "0"); // Deprecated; CSP replaces it

    resp.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

    resp.setHeader("Content-Security-Policy", CSP_POLICY);

    resp.setHeader("Permissions-Policy", PERMISSIONS_POLICY);

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


  /**
   * Convenience method to register this filter on a ServletContextHandler.
   */
  public static void register(org.eclipse.jetty.servlet.ServletContextHandler context) {
    org.eclipse.jetty.servlet.FilterHolder holder =
      new org.eclipse.jetty.servlet.FilterHolder(new SecurityHeadersFilter());
    context.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));
  }
}



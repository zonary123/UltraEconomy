package com.kingpixel.ultraeconomy.web.server.filter;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.WebSecurityConfig;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Bearer-token authentication filter for all {@code /api/*} endpoints.
 *
 * <p>When {@code webSecurity.apiToken} is non-empty, every API request must include:
 * <pre>Authorization: Bearer &lt;token&gt;</pre>
 * Requests without a valid token receive {@code 401 Unauthorized}.
 *
 * <p>When {@code apiToken} is empty or null the filter is a no-op, which allows
 * local/dev setups to skip authentication entirely.
 */
public class ApiAuthFilter implements Filter {

  private static final String HEADER_AUTH = "Authorization";
  private static final String JSON_UNAUTHORIZED = "{\"error\":\"Unauthorized — provide a valid Bearer token\"}";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {

    WebSecurityConfig config = getConfig();
    String token = config != null ? config.getApiToken() : null;

    // Auth disabled when token is not configured
    if (token == null || token.isBlank()) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;

    String authHeader = req.getHeader(HEADER_AUTH);
    String expected = "Bearer " + token;

    if (authHeader == null || !authHeader.equals(expected)) {
      UltraEconomy.LOGGER.debug("[ApiAuth] Unauthorized request from {}", req.getRemoteAddr());
      resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      resp.setContentType("application/json");
      resp.setHeader("WWW-Authenticate", "Bearer realm=\"UltraEconomy\"");
      resp.getWriter().write(JSON_UNAUTHORIZED);
      return;
    }

    chain.doFilter(request, response);
  }

  private WebSecurityConfig getConfig() {
    return UltraEconomy.config != null ? UltraEconomy.config.getWebSecurity() : null;
  }

  /** Register this filter on {@code /api/*} only. */
  public static void register(ServletContextHandler context) {
    FilterHolder holder = new FilterHolder(new ApiAuthFilter());
    context.addFilter(holder, "/api/*", EnumSet.of(DispatcherType.REQUEST));
  }
}


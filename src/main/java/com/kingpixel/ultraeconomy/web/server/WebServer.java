package com.kingpixel.ultraeconomy.web.server;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.WebSecurityConfig;
import com.kingpixel.ultraeconomy.web.server.api.PlayerApiServlet;
import com.kingpixel.ultraeconomy.web.server.api.PlayersApiServlet;
import com.kingpixel.ultraeconomy.web.server.api.StatsApiServlet;
import com.kingpixel.ultraeconomy.web.server.api.TransactionPlayerApiServlet;
import com.kingpixel.ultraeconomy.web.server.filter.ApiAuthFilter;
import com.kingpixel.ultraeconomy.web.server.filter.RateLimitFilter;
import com.kingpixel.ultraeconomy.web.server.filter.SecurityHeadersFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Objects;

public class WebServer {

  private final Server server;
  private final int port;

  public WebServer(int port) {
    this.server = new Server();
    this.port = port;
  }

  /**
   * Start Jetty with security filters, API servlets, static assets, and SPA fallback.
   * The filter order matters: rate limiting must run before any other work.
   */
  public void start() {
    try {
      configureConnector();

      ServletContextHandler context = new ServletContextHandler();
      context.setContextPath("/");

      registerSecurityFilters(context);

      if (UltraEconomy.config.isDebug()) {
        registerCors(context);
      }

      registerApiServlets(context);
      registerStaticFiles(context);
      registerSpaFallback(context);

      server.setHandler(context);
      server.start();
      UltraEconomy.LOGGER.info("[Web] Server started at http://localhost:{} with security filters enabled", port);
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("[Web] Failed to start web server on port {}", port, e);
    }
  }

  /**
   * Stop Jetty and report shutdown failures through the mod logger.
   */
  public void stop() {
    try {
      server.stop();
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("[Web] Error stopping web server", e);
    }
  }

  /**
   * Configure the connector to limit idle time and avoid exposing Jetty version details.
   */
  private void configureConnector() {
    WebSecurityConfig security = UltraEconomy.config.getWebSecurity();
    int idleTimeout = (security != null && security.isEnabled())
      ? security.getIdleTimeoutSeconds() * 1000
      : 30_000;
    int maxRequestBody = (security != null && security.isEnabled())
      ? security.getMaxRequestBodyBytes()
      : 8192;

    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setRequestHeaderSize(8192);
    httpConfig.setResponseHeaderSize(8192);
    httpConfig.setSendServerVersion(false);
    httpConfig.setSendDateHeader(true);

    ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
    connector.setPort(port);
    connector.setIdleTimeout(idleTimeout);
    server.addConnector(connector);

    server.setAttribute("org.eclipse.jetty.server.Request.maxFormContentSize", maxRequestBody);
  }

  /**
   * Register security middleware before API/static handlers.
   * Rate limiting must execute first so abusive requests are rejected immediately.
   */
  private void registerSecurityFilters(ServletContextHandler context) {
    WebSecurityConfig security = UltraEconomy.config.getWebSecurity();
    if (security == null || !security.isEnabled()) {
      UltraEconomy.LOGGER.warn("[WebSecurity] Security filters are DISABLED. " +
        "Set webSecurity.enabled=true in config.json for production use.");
      return;
    }

    RateLimitFilter.register(context);
    SecurityHeadersFilter.register(context);
    // Token auth runs after rate-limiting so abusive IPs are rejected before auth processing
    ApiAuthFilter.register(context);

    UltraEconomy.LOGGER.info("[WebSecurity] Filters active — API rate: {}/s (burst {}), " +
        "Static rate: {}/s (burst {}), Ban after {} violations for {} min",
      security.getApiRateLimit(), security.getApiBurstCapacity(),
      security.getStaticRateLimit(), security.getStaticBurstCapacity(),
      security.getBanThreshold(), security.getBanDurationMinutes());
  }

  /**
   * Register public JSON endpoints used by the SPA.
   */
  private void registerApiServlets(ServletContextHandler context) {
    context.addServlet(StatsApiServlet.class, "/api/stats");
    context.addServlet(PlayersApiServlet.class, "/api/players");
    context.addServlet(TransactionPlayerApiServlet.class, "/api/transactions/player/*");
    context.addServlet(PlayerApiServlet.class, "/api/player/*");
  }

  /**
   * Enable CORS only in debug mode so production keeps the same-origin policy.
   */
  private void registerCors(ServletContextHandler context) {
    FilterHolder cors = context.addFilter(CrossOriginFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
    cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
    cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,PUT,DELETE,OPTIONS");
    cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");
  }

  /**
   * Serve the SPA assets from `src/main/resources/web`.
   */
  private void registerStaticFiles(ServletContextHandler context) {
    context.setResourceBase(Objects.requireNonNull(
      getClass().getClassLoader().getResource("web"),
      "web resources not found"
    ).toExternalForm());
    context.addServlet(DefaultServlet.class, "/");
  }

  /**
   * Let the SPA handle client routes while preserving real files and `/api/*`.
   */
  private void registerSpaFallback(ServletContextHandler context) {
    FilterHolder spaFallback = new FilterHolder(new Filter() {
      @Override
      public void doFilter(ServletRequest request,
                           ServletResponse response,
                           FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI();

        if (path.startsWith("/api/") || resourceExists(path)) {
          chain.doFilter(request, response);
          return;
        }

        resp.setContentType("text/html");
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("web/index.html")) {
          if (input == null) {
            throw new IOException("web/index.html not found");
          }
          resp.getWriter().write(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
      }


      private boolean resourceExists(String path) {
        return getClass().getClassLoader().getResource("web" + path) != null;
      }
    });

    context.addFilter(spaFallback, "/*", EnumSet.of(DispatcherType.REQUEST));
  }
}

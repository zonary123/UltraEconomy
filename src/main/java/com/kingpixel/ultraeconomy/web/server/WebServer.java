package com.kingpixel.ultraeconomy.web.server;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.WebSecurityConfig;
import com.kingpixel.ultraeconomy.web.server.api.PlayerApiServlet;
import com.kingpixel.ultraeconomy.web.server.api.PlayersApiServlet;
import com.kingpixel.ultraeconomy.web.server.api.StatsApiServlet;
import com.kingpixel.ultraeconomy.web.server.api.TransactionPlayerApiServlet;
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
import java.util.EnumSet;

public class WebServer {

  private final Server server;
  private final int port;

  public WebServer(int port) {
    this.server = new Server();
    this.port = port;
  }

  public void start() {
    try {
      // -----------------------------
      // Configurar connector con timeouts de seguridad
      // -----------------------------
      configureConnector();

      ServletContextHandler context = new ServletContextHandler();
      context.setContextPath("/");

      // -----------------------------
      // Filtros de seguridad (PRIMERO — antes de todo)
      // -----------------------------
      registerSecurityFilters(context);

      // -----------------------------
      // Configurar CORS si está en modo debug
      // -----------------------------
      if (UltraEconomy.config.isDebug()) {
        registerCors(context);
      }

      // -----------------------------
      // Configurar API
      // -----------------------------
      registerApiServlets(context);

      // -----------------------------
      // Servir archivos estáticos
      // -----------------------------
      registerStaticFiles(context);

      // -----------------------------
      // Filtro SPA fallback
      // -----------------------------
      registerSpaFallback(context);

      // -----------------------------
      // Iniciar servidor
      // -----------------------------
      server.setHandler(context);
      server.start();
      UltraEconomy.LOGGER.info("[Web] Server started on port {} with security filters enabled", port);
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("[Web] Failed to start web server on port {}", port, e);
    }
  }

  public void stop() {
    try {
      server.stop();
    } catch (Exception e) {
      UltraEconomy.LOGGER.error("[Web] Error stopping web server", e);
    }
  }

  // =============================
  // MÉTODOS PRIVADOS
  // =============================

  /**
   * Configure Jetty connector with security-oriented timeouts.
   * <p>
   * - Idle timeout: closes slow/abandoned connections (slow-loris protection)
   * - Request header size: limits oversized headers
   * - Response header size: sane default
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
    httpConfig.setSendServerVersion(false); // Don't expose Jetty version
    httpConfig.setSendDateHeader(true);

    ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
    connector.setPort(port);
    connector.setIdleTimeout(idleTimeout);
    server.addConnector(connector);

    // Limit request body size (this server only handles GET, so keep it small)
    server.setAttribute("org.eclipse.jetty.server.Request.maxFormContentSize", maxRequestBody);
  }

  /**
   * Register security filters in correct order:
   * 1. Rate Limiting (reject abusive traffic ASAP)
   * 2. Security Headers (add protective headers to all responses)
   */
  private void registerSecurityFilters(ServletContextHandler context) {
    WebSecurityConfig security = UltraEconomy.config.getWebSecurity();
    if (security == null || !security.isEnabled()) {
      UltraEconomy.LOGGER.warn("[WebSecurity] Security filters are DISABLED. " +
        "Set webSecurity.enabled=true in config.json for production use.");
      return;
    }

    // Order matters: rate limit FIRST to reject abuse before any processing
    RateLimitFilter.register(context);
    SecurityHeadersFilter.register(context);

    UltraEconomy.LOGGER.info("[WebSecurity] Filters active — API rate: {}/s (burst {}), " +
        "Static rate: {}/s (burst {}), Ban after {} violations for {} min",
      security.getApiRateLimit(), security.getApiBurstCapacity(),
      security.getStaticRateLimit(), security.getStaticBurstCapacity(),
      security.getBanThreshold(), security.getBanDurationMinutes());
  }

  private void registerApiServlets(ServletContextHandler context) {
    context.addServlet(StatsApiServlet.class, "/api/stats");
    context.addServlet(PlayersApiServlet.class, "/api/players");
    context.addServlet(TransactionPlayerApiServlet.class, "/api/transactions/player/*");
    context.addServlet(PlayerApiServlet.class, "/api/player/*");
  }

  private void registerCors(ServletContextHandler context) {
    FilterHolder cors = context.addFilter(CrossOriginFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
    cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
    cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,PUT,DELETE,OPTIONS");
    cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");
  }

  private void registerStaticFiles(ServletContextHandler context) {
    context.setResourceBase(getClass().getClassLoader().getResource("web").toExternalForm());
    context.addServlet(DefaultServlet.class, "/"); // Sirve todos los archivos existentes
  }

  private void registerSpaFallback(ServletContextHandler context) {
    FilterHolder spaFallback = new FilterHolder(new Filter() {
      @Override
      public void init(FilterConfig filterConfig) {
      }

      @Override
      public void doFilter(ServletRequest request,
                           ServletResponse response,
                           FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI();

        // Si es API o archivo existente, dejamos pasar
        if (path.startsWith("/api/") || resourceExists(path)) {
          chain.doFilter(request, response);
          return;
        }

        // Devolver index.html para SPA
        resp.setContentType("text/html");
        resp.getWriter().write(
          new String(getClass().getClassLoader()
            .getResourceAsStream("web/index.html")
            .readAllBytes())
        );
      }

      @Override
      public void destroy() {
      }

      private boolean resourceExists(String path) {
        return getClass().getClassLoader().getResource("web" + path) != null;
      }
    });

    context.addFilter(spaFallback, "/*", EnumSet.of(DispatcherType.REQUEST));
  }
}

package com.kingpixel.ultraeconomy.config;

import lombok.Data;

/**
 * Web security settings: rate limiting, auto-ban, headers, and proxy trust.
 */
@Data
public class WebSecurityConfig {

  /**
   * Enable or disable all web security filters.
   * When false, no rate limiting, banning, or security headers are applied.
   */
  private boolean enabled;

  /**
   * Sustained request rate per IP for `/api/*` endpoints.
   * Set to 0 to disable rate limiting for the API.
   */
  private int apiRateLimit;

  /**
   * Burst capacity for API requests per IP.
   * Should be equal to or greater than `apiRateLimit`.
   */
  private int apiBurstCapacity;

  /**
   * Sustained request rate per IP for static assets such as CSS and JS.
   */
  private int staticRateLimit;

  /**
   * Burst capacity for static asset requests per IP.
   */
  private int staticBurstCapacity;

  /**
   * Number of rate-limit violations before an IP is automatically banned.
   * Set to 0 to disable auto-banning.
   */
  private int banThreshold;

  /**
   * Duration in minutes that a banned IP stays blocked.
   */
  private int banDurationMinutes;

  /**
   * Trust `X-Forwarded-For` and `X-Real-IP` only when the server sits behind a reverse proxy.
   */
  private boolean trustProxy;

  /**
   * Maximum idle time in seconds before Jetty closes a connection.
   * This helps reduce slow-loris style connections.
   */
  private int idleTimeoutSeconds;

  /**
   * Maximum request body size in bytes.
   * The web server only serves GET routes, so this stays small.
   */
  private int maxRequestBodyBytes;

  /**
   * Static bearer token required in the {@code Authorization: Bearer <token>} header for all
   * {@code /api/*} requests. Leave empty to disable authentication (useful for local-only setups).
   * Generate a random token (e.g. UUID) and set it here for production use.
   */
  private String apiToken;

  /**
   * Sensible defaults for the built-in web UI and API.
   */
  public WebSecurityConfig() {
    enabled = true;
    apiRateLimit = 20;
    apiBurstCapacity = 40;
    staticRateLimit = 60;
    staticBurstCapacity = 120;
    banThreshold = 10;
    banDurationMinutes = 15;
    trustProxy = false;
    idleTimeoutSeconds = 30;
    maxRequestBodyBytes = 8192;
    apiToken = ""; // Empty = disabled; set a UUID or random string for production
  }
}


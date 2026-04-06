package com.kingpixel.ultraeconomy.config;

import lombok.Data;

/**
 * Web security settings: rate limiting, auto-ban, headers, and proxy trust.
 *
 * @author Carlos Varas Alonso
 */
@Data
public class WebSecurityConfig {

  /**
   * Enable or disable all web security filters.
   * When false, no rate limiting, banning, or security headers are applied.
   */
  private boolean enabled;

  // ─── Rate Limiting (Token Bucket) ───

  /**
   * Maximum requests per second per IP for API endpoints (/api/*).
   * This is the sustained rate. Set to 0 to disable rate limiting.
   */
  private int apiRateLimit;

  /**
   * Burst capacity for API requests per IP.
   * Allows short bursts above the sustained rate before throttling kicks in.
   * Should be >= apiRateLimit.
   */
  private int apiBurstCapacity;

  /**
   * Maximum requests per second per IP for static assets (css, js, html).
   * Static assets are less expensive, so this can be higher.
   */
  private int staticRateLimit;

  /**
   * Burst capacity for static asset requests per IP.
   */
  private int staticBurstCapacity;

  // ─── Auto-Ban ───

  /**
   * Number of rate-limit violations (429 responses) before an IP is auto-banned.
   * Set to 0 to disable auto-banning.
   */
  private int banThreshold;

  /**
   * Duration in minutes that a banned IP stays blocked.
   */
  private int banDurationMinutes;

  // ─── Proxy Trust ───

  /**
   * Trust X-Forwarded-For header for real IP extraction.
   * Enable ONLY if the server is behind a reverse proxy (nginx, Cloudflare, etc.).
   * If false, the direct connection IP is used.
   */
  private boolean trustProxy;

  // ─── Timeouts ───

  /**
   * Maximum idle time (in seconds) for a connection before Jetty closes it.
   * Protects against slow-loris attacks.
   */
  private int idleTimeoutSeconds;

  /**
   * Maximum request body size in bytes.
   * This server only handles GET requests, so a small value is fine.
   */
  private int maxRequestBodyBytes;

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
  }
}


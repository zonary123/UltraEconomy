package com.kingpixel.ultraeconomy.web;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.web.server.WebServer;

public class WebModule {
  private boolean started = false;
  private WebServer webServer;

  public synchronized void start() {
    if (started) return;
    webServer = new WebServer(UltraEconomy.config.getWebPort());
    webServer.start();
    started = true;
  }

  public synchronized void stop() {
    if (webServer != null) webServer.stop();
    started = false;
  }
}

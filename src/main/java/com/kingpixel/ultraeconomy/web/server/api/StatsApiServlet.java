package com.kingpixel.ultraeconomy.web.server.api;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.config.Currencies;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Lightweight stats endpoint for the web dashboard.
 * Returns online players, max players, and currency IDs.
 */
public class StatsApiServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("application/json");
    resp.setHeader("Cache-Control", "no-cache");

    int online = 0;
    int max = 0;
    if (UltraEconomy.server != null) {
      online = UltraEconomy.server.getPlayerManager().getPlayerList().size();
      max = UltraEconomy.server.getMaxPlayerCount();
    }

    String[] currencies = Currencies.CURRENCY_IDS != null ? Currencies.CURRENCY_IDS : new String[0];

    StringBuilder sb = new StringBuilder(128);
    sb.append("{\"onlinePlayers\":").append(online);
    sb.append(",\"maxPlayers\":").append(max);
    sb.append(",\"currencies\":[");
    for (int i = 0; i < currencies.length; i++) {
      if (i > 0) sb.append(',');
      sb.append('"').append(currencies[i]).append('"');
    }
    sb.append("]}");

    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write(sb.toString());
  }
}


package com.kingpixel.ultraeconomy.web.server.api;

import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

public class TransactionPlayerApiServlet extends HttpServlet {

  @Override
  protected void doGet(
    HttpServletRequest req,
    HttpServletResponse resp
  ) throws IOException {

    resp.setContentType("application/json");

    // Obtener /{uuid}
    String pathInfo = req.getPathInfo();

    if (pathInfo == null || pathInfo.length() <= 1) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().write("{\"error\":\"UUID del jugador requerido\"}");
      return;
    }

    UUID playerUuid;
    try {
      playerUuid = UUID.fromString(pathInfo.substring(1));
    } catch (IllegalArgumentException e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().write("{\"error\":\"UUID inválido\"}");
      return;
    }

    var transactions = DatabaseFactory.INSTANCE.getTransactions(
      playerUuid,
      parseLimit(req.getParameter("limit"), 500, 2000)
    );

    var json = UtilsFile.getGson().toJson(transactions);

    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write(json);
  }

  private int parseLimit(String value, int defaultVal, int max) {
    if (value == null) return defaultVal;
    try {
      return Math.clamp(Integer.parseInt(value), 1, max);
    } catch (NumberFormatException e) {
      return defaultVal;
    }
  }
}

package com.kingpixel.ultraeconomy.web.server.api;

import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

public class PlayerApiServlet extends HttpServlet {

  @Override
  protected void doGet(
    HttpServletRequest req,
    HttpServletResponse resp
  ) throws IOException {

    resp.setContentType("application/json");

    // Obtener /{uuidOrName}
    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.length() <= 1) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().write("{\"error\":\"UUID or player name required\"}");
      return;
    }

    String identifier = pathInfo.substring(1); // quitar "/"
    Object account = null;

    // Primero intentamos parsear como UUID
    try {
      UUID playerUuid = UUID.fromString(identifier);
      account = DatabaseFactory.INSTANCE.getAccount(playerUuid);
    } catch (IllegalArgumentException e) {
      // Si no es UUID, lo tratamos como nombre
      account = DatabaseFactory.INSTANCE.getAccountByName(identifier);
    }

    if (account == null) {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      resp.getWriter().write("{\"error\":\"Player not found\"}");
      return;
    }

    String json = UtilsFile.getGson().toJson(account);
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getWriter().write(json);
  }
}

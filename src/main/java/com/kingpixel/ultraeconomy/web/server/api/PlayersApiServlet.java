package com.kingpixel.ultraeconomy.web.server.api;

import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class PlayersApiServlet extends HttpServlet {

  @Override
  protected void doGet(
    HttpServletRequest req,
    HttpServletResponse resp
  ) throws IOException {

    resp.setContentType("application/json");
    resp.setStatus(HttpServletResponse.SC_OK);
    int page = parseInt(req.getParameter("page"), 1, 1, Integer.MAX_VALUE);
    var accounts = DatabaseFactory.INSTANCE.getAccounts(50, page);
    var json = UtilsFile.getGson().toJson(accounts);
    resp.getWriter().write(json);
  }

  private int parseInt(String value, int def) {
    try {
      return value == null ? def : Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return def;
    }
  }

  private int parseInt(String value, int def, int min, int max) {
    int parsed = parseInt(value, def);
    return Math.clamp(parsed, min, max);
  }
}

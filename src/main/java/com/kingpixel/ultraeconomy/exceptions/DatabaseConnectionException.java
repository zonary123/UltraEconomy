package com.kingpixel.ultraeconomy.exceptions;

/**
 * @author Carlos Varas Alonso - 28/09/2025 7:23
 */
public class DatabaseConnectionException extends RuntimeException {
  public DatabaseConnectionException(String dbType) {
    super("Failed to connect to database of type: " + dbType);
  }

  public DatabaseConnectionException(String dbType, Throwable cause) {
    super("Failed to connect to database of type: " + dbType, cause);
  }
}


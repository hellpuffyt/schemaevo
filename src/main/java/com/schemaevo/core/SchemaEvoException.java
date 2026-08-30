package com.schemaevo.core;

/** Thrown when a schema cannot be parsed or a check cannot proceed for a well-understood reason. */
public class SchemaEvoException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SchemaEvoException(String message) {
    super(message);
  }

  public SchemaEvoException(String message, Throwable cause) {
    super(message, cause);
  }
}

package com.schemaevo.model;

/** How serious a compatibility finding is. */
public enum Severity {
  /** The change breaks the requested compatibility guarantee. */
  ERROR,
  /** The change is allowed but worth a human's attention. */
  WARNING,
  /** Purely informational, does not affect compatibility. */
  INFO
}

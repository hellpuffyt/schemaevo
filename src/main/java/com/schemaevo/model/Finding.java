package com.schemaevo.model;

import java.util.Objects;

/**
 * A single compatibility observation, explained in reader/writer terms.
 *
 * @param severity how serious the finding is
 * @param direction which reader/writer pairing produced this finding
 * @param ruleId a stable machine-readable identifier for the rule that fired, e.g. {@code
 *     "avro.field.added-without-default"}
 * @param path a JSON-pointer-like location of the offending element, e.g. {@code "#/fields/status"}
 * @param message a human explanation of the break in reader/writer terms
 * @param suggestion a concrete fix, or {@code null} if none applies (e.g. purely informational
 *     findings)
 */
public record Finding(
    Severity severity,
    Direction direction,
    String ruleId,
    String path,
    String message,
    String suggestion) {

  public Finding {
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(message, "message");
  }

  public boolean isBlocking() {
    return severity == Severity.ERROR;
  }
}

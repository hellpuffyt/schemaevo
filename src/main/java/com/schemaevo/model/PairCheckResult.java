package com.schemaevo.model;

import java.util.List;
import java.util.Objects;

/**
 * The result of comparing the candidate schema against a single prior version in one direction.
 *
 * @param baseLabel a human label for the prior version, e.g. {@code "v2"} or a file name
 * @param candidateLabel a human label for the candidate (new) schema
 * @param direction which reader/writer pairing this check used
 * @param findings all findings from this single directional comparison
 */
public record PairCheckResult(
    String baseLabel, String candidateLabel, Direction direction, List<Finding> findings) {

  public PairCheckResult {
    Objects.requireNonNull(baseLabel, "baseLabel");
    Objects.requireNonNull(candidateLabel, "candidateLabel");
    Objects.requireNonNull(direction, "direction");
    findings = List.copyOf(findings);
  }

  public boolean isCompatible() {
    return findings.stream().noneMatch(Finding::isBlocking);
  }
}

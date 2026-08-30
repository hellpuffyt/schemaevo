package com.schemaevo.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The aggregate result of checking one candidate schema against one or more prior versions under a
 * {@link CompatibilityMode}.
 *
 * @param format the schema language that was checked
 * @param mode the compatibility guarantee that was requested
 * @param candidateLabel a human label for the candidate (new) schema
 * @param pairResults one entry per (prior version, direction) pair that was evaluated
 */
public record CompatibilityResult(
    SchemaFormat format,
    CompatibilityMode mode,
    String candidateLabel,
    List<PairCheckResult> pairResults) {

  public CompatibilityResult {
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(candidateLabel, "candidateLabel");
    pairResults = List.copyOf(pairResults);
  }

  public boolean isCompatible() {
    return pairResults.stream().allMatch(PairCheckResult::isCompatible);
  }

  public List<Finding> allFindings() {
    return pairResults.stream().flatMap(p -> p.findings().stream()).collect(Collectors.toList());
  }

  public List<Finding> blockingFindings() {
    return allFindings().stream().filter(Finding::isBlocking).collect(Collectors.toList());
  }
}

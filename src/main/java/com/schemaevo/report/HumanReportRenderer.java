package com.schemaevo.report;

import com.schemaevo.model.CompatibilityResult;
import com.schemaevo.model.Finding;
import com.schemaevo.model.PairCheckResult;

/** Renders a {@link CompatibilityResult} as a readable, colorless plain-text report. */
public final class HumanReportRenderer {

  private HumanReportRenderer() {}

  public static String render(CompatibilityResult result) {
    StringBuilder sb = new StringBuilder();
    sb.append("schemaevo compatibility report\n");
    sb.append("  format:    ").append(result.format()).append('\n');
    sb.append("  mode:      ").append(result.mode()).append('\n');
    sb.append("  candidate: ").append(result.candidateLabel()).append('\n');
    sb.append('\n');

    for (PairCheckResult pair : result.pairResults()) {
      String verb =
          pair.direction() == com.schemaevo.model.Direction.BACKWARD ? "BACKWARD" : "FORWARD";
      sb.append("[")
          .append(verb)
          .append("] ")
          .append(pair.candidateLabel())
          .append(" vs ")
          .append(pair.baseLabel())
          .append(": ")
          .append(pair.isCompatible() ? "COMPATIBLE" : "INCOMPATIBLE")
          .append('\n');

      if (pair.findings().isEmpty()) {
        sb.append("  (no findings)\n");
      }
      for (Finding finding : pair.findings()) {
        sb.append("  - [")
            .append(finding.severity())
            .append("] ")
            .append(finding.ruleId())
            .append(" at ")
            .append(finding.path())
            .append('\n');
        sb.append("      ").append(finding.message()).append('\n');
        if (finding.suggestion() != null) {
          sb.append("      fix: ").append(finding.suggestion()).append('\n');
        }
      }
      sb.append('\n');
    }

    sb.append(
        result.isCompatible()
            ? "RESULT: compatible\n"
            : "RESULT: incompatible ("
                + result.blockingFindings().size()
                + " blocking finding(s))\n");
    return sb.toString();
  }
}

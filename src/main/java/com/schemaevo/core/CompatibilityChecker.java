package com.schemaevo.core;

import com.schemaevo.model.CompatibilityMode;
import com.schemaevo.model.CompatibilityResult;
import java.util.List;

/**
 * Checks a candidate schema against one or more prior versions of the same schema, in the same
 * format.
 */
public interface CompatibilityChecker {

  /**
   * @param priorVersions the schema history, oldest first, not including the candidate. For a
   *     non-transitive mode only the last element is used as the immediate predecessor.
   * @param priorLabels human labels for each entry in {@code priorVersions}, same size and order
   * @param candidate the new schema being evaluated
   * @param candidateLabel a human label for the candidate schema
   * @param mode the compatibility guarantee to check
   */
  CompatibilityResult check(
      List<String> priorVersions,
      List<String> priorLabels,
      String candidate,
      String candidateLabel,
      CompatibilityMode mode);
}

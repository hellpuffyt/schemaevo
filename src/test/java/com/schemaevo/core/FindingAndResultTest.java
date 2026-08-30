package com.schemaevo.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schemaevo.model.CompatibilityMode;
import com.schemaevo.model.CompatibilityResult;
import com.schemaevo.model.Direction;
import com.schemaevo.model.Finding;
import com.schemaevo.model.PairCheckResult;
import com.schemaevo.model.SchemaFormat;
import com.schemaevo.model.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class FindingAndResultTest {

  @Test
  void finding_rejectsNullSeverity() {
    assertThatThrownBy(() -> new Finding(null, Direction.BACKWARD, "rule", "#", "msg", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void finding_errorSeverity_isBlocking() {
    Finding finding = new Finding(Severity.ERROR, Direction.BACKWARD, "rule", "#", "msg", "fix");
    assertThat(finding.isBlocking()).isTrue();
  }

  @Test
  void finding_warningSeverity_isNotBlocking() {
    Finding finding = new Finding(Severity.WARNING, Direction.BACKWARD, "rule", "#", "msg", null);
    assertThat(finding.isBlocking()).isFalse();
  }

  @Test
  void pairCheckResult_compatibleWhenNoBlockingFindings() {
    PairCheckResult result = new PairCheckResult("v1", "v2", Direction.BACKWARD, List.of());
    assertThat(result.isCompatible()).isTrue();
  }

  @Test
  void pairCheckResult_incompatibleWithErrorFinding() {
    Finding error = new Finding(Severity.ERROR, Direction.BACKWARD, "rule", "#", "msg", null);
    PairCheckResult result = new PairCheckResult("v1", "v2", Direction.BACKWARD, List.of(error));
    assertThat(result.isCompatible()).isFalse();
  }

  @Test
  void compatibilityResult_aggregatesAcrossPairs() {
    Finding error = new Finding(Severity.ERROR, Direction.BACKWARD, "rule", "#", "msg", null);
    PairCheckResult ok = new PairCheckResult("v1", "v3", Direction.BACKWARD, List.of());
    PairCheckResult broken = new PairCheckResult("v2", "v3", Direction.BACKWARD, List.of(error));

    CompatibilityResult result =
        new CompatibilityResult(
            SchemaFormat.AVRO, CompatibilityMode.BACKWARD_TRANSITIVE, "v3", List.of(ok, broken));

    assertThat(result.isCompatible()).isFalse();
    assertThat(result.allFindings()).hasSize(1);
    assertThat(result.blockingFindings()).hasSize(1);
  }

  @Test
  void compatibilityResult_compatibleWhenAllPairsCompatible() {
    PairCheckResult ok1 = new PairCheckResult("v1", "v3", Direction.BACKWARD, List.of());
    PairCheckResult ok2 = new PairCheckResult("v2", "v3", Direction.BACKWARD, List.of());

    CompatibilityResult result =
        new CompatibilityResult(
            SchemaFormat.JSON_SCHEMA,
            CompatibilityMode.BACKWARD_TRANSITIVE,
            "v3",
            List.of(ok1, ok2));

    assertThat(result.isCompatible()).isTrue();
    assertThat(result.blockingFindings()).isEmpty();
  }
}

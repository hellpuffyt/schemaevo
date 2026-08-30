package com.schemaevo.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.schemaevo.model.CompatibilityMode;
import com.schemaevo.model.CompatibilityResult;
import com.schemaevo.model.Direction;
import com.schemaevo.model.Finding;
import com.schemaevo.model.PairCheckResult;
import com.schemaevo.model.SchemaFormat;
import com.schemaevo.model.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportRendererTest {

  private CompatibilityResult brokenResult() {
    Finding finding =
        new Finding(
            Severity.ERROR,
            Direction.BACKWARD,
            "avro.record.field-missing-no-default",
            "#/fields/x",
            "field x has no default",
            "add a default");
    PairCheckResult pair = new PairCheckResult("v1", "v2", Direction.BACKWARD, List.of(finding));
    return new CompatibilityResult(
        SchemaFormat.AVRO, CompatibilityMode.BACKWARD, "v2", List.of(pair));
  }

  private CompatibilityResult okResult() {
    PairCheckResult pair = new PairCheckResult("v1", "v2", Direction.BACKWARD, List.of());
    return new CompatibilityResult(
        SchemaFormat.AVRO, CompatibilityMode.BACKWARD, "v2", List.of(pair));
  }

  @Test
  void humanReport_includesRuleAndSuggestion() {
    String report = HumanReportRenderer.render(brokenResult());
    assertThat(report).contains("avro.record.field-missing-no-default");
    assertThat(report).contains("add a default");
    assertThat(report).contains("INCOMPATIBLE");
    assertThat(report).contains("RESULT: incompatible");
  }

  @Test
  void humanReport_forCompatibleResult_saysCompatible() {
    String report = HumanReportRenderer.render(okResult());
    assertThat(report).contains("RESULT: compatible");
    assertThat(report).contains("COMPATIBLE");
  }

  @Test
  void jsonReport_containsExpectedFields() {
    String json = JsonReportRenderer.render(brokenResult());
    assertThat(json).contains("\"format\"");
    assertThat(json).contains("\"AVRO\"");
    assertThat(json).contains("\"compatible\" : false");
    assertThat(json).contains("avro.record.field-missing-no-default");
  }

  @Test
  void jsonReport_forCompatibleResult() {
    String json = JsonReportRenderer.render(okResult());
    assertThat(json).contains("\"compatible\" : true");
  }
}

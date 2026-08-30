package com.schemaevo.avro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schemaevo.core.SchemaEvoException;
import com.schemaevo.model.CompatibilityMode;
import com.schemaevo.model.CompatibilityResult;
import com.schemaevo.model.Direction;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvroCompatibilityCheckerTest {

  private final AvroCompatibilityChecker checker = new AvroCompatibilityChecker();

  private static final String V1 =
      "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"a\",\"type\":\"string\"}]}";
  private static final String V2 =
      "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
          + "{\"name\":\"a\",\"type\":\"string\"},"
          + "{\"name\":\"b\",\"type\":\"string\",\"default\":\"x\"}]}";
  private static final String V3_BREAKING =
      "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
          + "{\"name\":\"a\",\"type\":\"string\"},"
          + "{\"name\":\"b\",\"type\":\"string\",\"default\":\"x\"},"
          + "{\"name\":\"c\",\"type\":\"string\"}]}";

  @Test
  void backwardMode_onlyChecksImmediatePredecessor() {
    CompatibilityResult result =
        checker.check(
            List.of(V1, V2), List.of("v1", "v2"), V3_BREAKING, "v3", CompatibilityMode.BACKWARD);

    assertThat(result.pairResults()).hasSize(1);
    assertThat(result.pairResults().get(0).baseLabel()).isEqualTo("v2");
    assertThat(result.pairResults().get(0).direction()).isEqualTo(Direction.BACKWARD);
    assertThat(result.isCompatible()).isFalse();
  }

  @Test
  void forwardMode_producesForwardDirectionOnly() {
    CompatibilityResult result =
        checker.check(List.of(V1), List.of("v1"), V2, "v2", CompatibilityMode.FORWARD);

    assertThat(result.pairResults()).hasSize(1);
    assertThat(result.pairResults().get(0).direction()).isEqualTo(Direction.FORWARD);
  }

  @Test
  void fullMode_checksBothDirections() {
    CompatibilityResult result =
        checker.check(List.of(V1), List.of("v1"), V2, "v2", CompatibilityMode.FULL);

    assertThat(result.pairResults()).hasSize(2);
    assertThat(result.pairResults().stream().map(p -> p.direction()).distinct().toList())
        .containsExactlyInAnyOrder(Direction.BACKWARD, Direction.FORWARD);
  }

  @Test
  void backwardTransitiveMode_checksEveryPriorVersion() {
    CompatibilityResult result =
        checker.check(
            List.of(V1, V2),
            List.of("v1", "v2"),
            V3_BREAKING,
            "v3",
            CompatibilityMode.BACKWARD_TRANSITIVE);

    assertThat(result.pairResults()).hasSize(2);
    assertThat(result.pairResults().stream().map(p -> p.baseLabel()).toList())
        .containsExactlyInAnyOrder("v1", "v2");
  }

  @Test
  void fullTransitiveMode_checksEveryVersionInBothDirections() {
    CompatibilityResult result =
        checker.check(
            List.of(V1, V2), List.of("v1", "v2"), V2, "v2b", CompatibilityMode.FULL_TRANSITIVE);

    assertThat(result.pairResults()).hasSize(4);
  }

  @Test
  void compatibleChain_reportsCompatible() {
    CompatibilityResult result =
        checker.check(
            List.of(V1, V2),
            List.of("v1", "v2"),
            V2,
            "v2again",
            CompatibilityMode.BACKWARD_TRANSITIVE);

    assertThat(result.isCompatible()).isTrue();
    assertThat(result.blockingFindings()).isEmpty();
  }

  @Test
  void malformedSchema_throwsClearException_notStackTrace() {
    assertThatThrownBy(
            () ->
                checker.check(
                    List.of(V1),
                    List.of("v1"),
                    "{ not valid json",
                    "bad",
                    CompatibilityMode.BACKWARD))
        .isInstanceOf(SchemaEvoException.class)
        .hasMessageContaining("bad");
  }

  @Test
  void mismatchedLabelsAndVersions_throws() {
    assertThatThrownBy(
            () ->
                checker.check(
                    List.of(V1, V2),
                    List.of("only-one-label"),
                    V2,
                    "v3",
                    CompatibilityMode.BACKWARD))
        .isInstanceOf(SchemaEvoException.class);
  }

  @Test
  void emptyPriorVersions_throws() {
    assertThatThrownBy(
            () -> checker.check(List.of(), List.of(), V1, "v1", CompatibilityMode.BACKWARD))
        .isInstanceOf(SchemaEvoException.class);
  }
}

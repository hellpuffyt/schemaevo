package com.schemaevo.jsonschema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schemaevo.core.SchemaEvoException;
import com.schemaevo.model.CompatibilityMode;
import com.schemaevo.model.CompatibilityResult;
import com.schemaevo.model.Direction;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonSchemaCompatibilityCheckerTest {

  private final JsonSchemaCompatibilityChecker checker = new JsonSchemaCompatibilityChecker();

  private static final String V1 =
      "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}";
  private static final String V2 =
      "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},"
          + "\"name\":{\"type\":\"string\"}}}";
  private static final String V3_BREAKING =
      "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},"
          + "\"name\":{\"type\":\"string\"}},\"required\":[\"id\",\"name\"]}";

  @Test
  void backwardMode_flagsNewlyRequiredProperty() {
    CompatibilityResult result =
        checker.check(
            List.of(V1, V2), List.of("v1", "v2"), V3_BREAKING, "v3", CompatibilityMode.BACKWARD);

    assertThat(result.pairResults()).hasSize(1);
    assertThat(result.isCompatible()).isFalse();
  }

  @Test
  void backwardTransitiveMode_checksAllPriorVersions() {
    CompatibilityResult result =
        checker.check(
            List.of(V1, V2),
            List.of("v1", "v2"),
            V3_BREAKING,
            "v3",
            CompatibilityMode.BACKWARD_TRANSITIVE);

    assertThat(result.pairResults()).hasSize(2);
    assertThat(result.isCompatible()).isFalse();
    // Both v1 and v2 lack "name" as required, so both pairs must report the break.
    assertThat(result.blockingFindings()).hasSizeGreaterThanOrEqualTo(2);
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
  void compatibleChange_reportsCompatible() {
    CompatibilityResult result =
        checker.check(List.of(V1), List.of("v1"), V2, "v2", CompatibilityMode.FULL);

    assertThat(result.isCompatible()).isTrue();
  }

  @Test
  void malformedJson_throwsClearException() {
    assertThatThrownBy(
            () ->
                checker.check(
                    List.of(V1),
                    List.of("v1"),
                    "{ this is not json",
                    "bad",
                    CompatibilityMode.BACKWARD))
        .isInstanceOf(SchemaEvoException.class)
        .hasMessageContaining("bad");
  }

  @Test
  void nonObjectRoot_throwsClearException() {
    assertThatThrownBy(
            () ->
                checker.check(List.of(V1), List.of("v1"), "42", "bad", CompatibilityMode.BACKWARD))
        .isInstanceOf(SchemaEvoException.class);
  }

  @Test
  void emptyPriorVersions_throws() {
    assertThatThrownBy(
            () -> checker.check(List.of(), List.of(), V1, "v1", CompatibilityMode.BACKWARD))
        .isInstanceOf(SchemaEvoException.class);
  }
}

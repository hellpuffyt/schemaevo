package com.schemaevo.jsonschema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemaevo.model.Direction;
import com.schemaevo.model.Finding;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonSchemaComparatorTest {

  private final JsonSchemaComparator comparator = new JsonSchemaComparator();
  private final ObjectMapper mapper = new ObjectMapper();

  private JsonNode node(String json) {
    try {
      return mapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private List<Finding> errors(List<Finding> findings) {
    return findings.stream().filter(Finding::isBlocking).toList();
  }

  // ---- required --------------------------------------------------------------------------

  @Test
  void addingRequiredProperty_breaksBackward() {
    JsonNode oldSchema =
        node("{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}");
    JsonNode newSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}},"
                + "\"required\":[\"id\"]}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("jsonschema.required.not-guaranteed");
  }

  @Test
  void addingOptionalProperty_isBackwardCompatible() {
    JsonNode oldSchema =
        node("{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}");
    JsonNode newSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},"
                + "\"nickname\":{\"type\":\"string\"}}}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void requiredPropertyAlreadyRequired_isCompatible() {
    JsonNode oldSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}},\"required\":[\"id\"]}");
    JsonNode newSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},"
                + "\"email\":{\"type\":\"string\"}},\"required\":[\"id\"]}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void removingRequiredProperty_isBackwardCompatible() {
    // Removing a requirement can only make the new schema more permissive; old data still fits.
    JsonNode oldSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}},\"required\":[\"id\"]}");
    JsonNode newSchema =
        node("{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  // ---- type ------------------------------------------------------------------------------

  @Test
  void looseningType_breaksForwardCompatibility() {
    // FORWARD: validating = old (string only), data = new (string or integer).
    JsonNode oldSchema = node("{\"type\":\"string\"}");
    JsonNode newSchema = node("{\"type\":[\"string\",\"integer\"]}");

    List<Finding> findings = comparator.compare(oldSchema, newSchema, Direction.FORWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("jsonschema.type.not-accepted");
  }

  @Test
  void narrowingType_breaksBackwardCompatibility() {
    // BACKWARD: validating = new (string only), data = old (string or integer).
    JsonNode oldSchema = node("{\"type\":[\"string\",\"integer\"]}");
    JsonNode newSchema = node("{\"type\":\"string\"}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("jsonschema.type.not-accepted");
  }

  @Test
  void sameType_isCompatible() {
    JsonNode oldSchema = node("{\"type\":\"string\"}");
    JsonNode newSchema = node("{\"type\":\"string\"}");

    assertThat(errors(comparator.compare(newSchema, oldSchema, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void wideningType_isBackwardCompatible() {
    // BACKWARD: validating = new (string or integer, wider), data = old (string only) -> fine.
    JsonNode oldSchema = node("{\"type\":\"string\"}");
    JsonNode newSchema = node("{\"type\":[\"string\",\"integer\"]}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  // ---- enum ------------------------------------------------------------------------------

  @Test
  void narrowingEnum_breaksBackwardCompatibility() {
    JsonNode oldSchema = node("{\"type\":\"string\",\"enum\":[\"A\",\"B\"]}");
    JsonNode newSchema = node("{\"type\":\"string\",\"enum\":[\"A\"]}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("jsonschema.enum.value-not-allowed");
  }

  @Test
  void wideningEnum_isBackwardCompatible() {
    JsonNode oldSchema = node("{\"type\":\"string\",\"enum\":[\"A\"]}");
    JsonNode newSchema = node("{\"type\":\"string\",\"enum\":[\"A\",\"B\"]}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void wideningEnum_breaksForwardCompatibility() {
    JsonNode oldSchema = node("{\"type\":\"string\",\"enum\":[\"A\"]}");
    JsonNode newSchema = node("{\"type\":\"string\",\"enum\":[\"A\",\"B\"]}");

    List<Finding> findings = comparator.compare(oldSchema, newSchema, Direction.FORWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("jsonschema.enum.value-not-allowed");
  }

  @Test
  void addingEnumOverUnconstrainedField_breaksBackwardCompatibility() {
    JsonNode oldSchema = node("{\"type\":\"string\"}");
    JsonNode newSchema = node("{\"type\":\"string\",\"enum\":[\"A\",\"B\"]}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("jsonschema.enum.unconstrained-source");
  }

  // ---- additionalProperties ---------------------------------------------------------------

  @Test
  void closingAdditionalProperties_breaksBackwardWhenOldDataHadExtras() {
    JsonNode oldSchema =
        node("{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}");
    JsonNode newSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}},"
                + "\"additionalProperties\":false}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId())
        .isEqualTo("jsonschema.additional-properties.closed");
  }

  @Test
  void closedSchemas_withSameProperties_isCompatible() {
    JsonNode oldSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}},"
                + "\"additionalProperties\":false}");
    JsonNode newSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}},"
                + "\"additionalProperties\":false}");

    assertThat(errors(comparator.compare(newSchema, oldSchema, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void newPropertyUnknownToClosedOldSchema_breaksForwardCompatibility() {
    JsonNode oldSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}},"
                + "\"additionalProperties\":false}");
    JsonNode newSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},"
                + "\"nickname\":{\"type\":\"string\"}},\"additionalProperties\":false}");

    List<Finding> findings = comparator.compare(oldSchema, newSchema, Direction.FORWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId())
        .isEqualTo("jsonschema.additional-properties.closed");
  }

  @Test
  void openSchema_toleratesExtraProperties_forwardCompatible() {
    JsonNode oldSchema =
        node("{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}");
    JsonNode newSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},"
                + "\"nickname\":{\"type\":\"string\"}}}");

    assertThat(errors(comparator.compare(oldSchema, newSchema, Direction.FORWARD))).isEmpty();
  }

  // ---- numeric constraints ----------------------------------------------------------------

  @Test
  void tighteningMinimum_breaksBackwardCompatibility() {
    JsonNode oldSchema = node("{\"type\":\"number\",\"minimum\":0}");
    JsonNode newSchema = node("{\"type\":\"number\",\"minimum\":10}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("jsonschema.number.range-narrower");
  }

  @Test
  void looseningMinimum_isBackwardCompatible() {
    JsonNode oldSchema = node("{\"type\":\"number\",\"minimum\":10}");
    JsonNode newSchema = node("{\"type\":\"number\",\"minimum\":0}");

    assertThat(errors(comparator.compare(newSchema, oldSchema, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void tighteningMaximum_breaksBackwardCompatibility() {
    JsonNode oldSchema = node("{\"type\":\"number\",\"maximum\":100}");
    JsonNode newSchema = node("{\"type\":\"number\",\"maximum\":50}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("jsonschema.number.range-narrower");
  }

  @Test
  void addingMinimumOverUnboundedField_breaksBackwardCompatibility() {
    JsonNode oldSchema = node("{\"type\":\"number\"}");
    JsonNode newSchema = node("{\"type\":\"number\",\"minimum\":0}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
  }

  // ---- string length ----------------------------------------------------------------------

  @Test
  void tighteningMinLength_breaksBackwardCompatibility() {
    JsonNode oldSchema = node("{\"type\":\"string\",\"minLength\":1}");
    JsonNode newSchema = node("{\"type\":\"string\",\"minLength\":5}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("jsonschema.string.length-narrower");
  }

  @Test
  void tighteningMaxLength_breaksBackwardCompatibility() {
    JsonNode oldSchema = node("{\"type\":\"string\",\"maxLength\":100}");
    JsonNode newSchema = node("{\"type\":\"string\",\"maxLength\":20}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("jsonschema.string.length-narrower");
  }

  @Test
  void looseningLengthConstraints_isBackwardCompatible() {
    JsonNode oldSchema = node("{\"type\":\"string\",\"minLength\":5,\"maxLength\":20}");
    JsonNode newSchema = node("{\"type\":\"string\",\"minLength\":1,\"maxLength\":100}");

    assertThat(errors(comparator.compare(newSchema, oldSchema, Direction.BACKWARD))).isEmpty();
  }

  // ---- recursion / structure --------------------------------------------------------------

  @Test
  void nestedPropertyTypeChange_isDetectedWithPath() {
    JsonNode oldSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"address\":{\"type\":\"object\","
                + "\"properties\":{\"zip\":{\"type\":\"string\"}}}}}");
    JsonNode newSchema =
        node(
            "{\"type\":\"object\",\"properties\":{\"address\":{\"type\":\"object\","
                + "\"properties\":{\"zip\":{\"type\":\"integer\"}}}}}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).path()).isEqualTo("#/properties/address/properties/zip");
  }

  @Test
  void arrayItemsTypeChange_isDetected() {
    JsonNode oldSchema = node("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}");
    JsonNode newSchema = node("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).path()).isEqualTo("#/items");
  }

  @Test
  void arrayItemsSameType_isCompatible() {
    JsonNode oldSchema = node("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}");
    JsonNode newSchema = node("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}");

    assertThat(errors(comparator.compare(newSchema, oldSchema, Direction.BACKWARD))).isEmpty();
  }
}

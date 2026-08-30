package com.schemaevo.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.schemaevo.model.Direction;
import com.schemaevo.model.Finding;
import com.schemaevo.model.Severity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Compares two JSON Schema documents to determine whether every document that validates against one
 * ("data schema") is guaranteed to also validate against the other ("validating schema").
 *
 * <p>Both {@link com.schemaevo.model.CompatibilityMode#BACKWARD} and {@link
 * com.schemaevo.model.CompatibilityMode#FORWARD} reduce to this question:
 *
 * <ul>
 *   <li>BACKWARD: does every document the <b>old</b> schema could have produced still validate
 *       against the <b>new</b> schema? (validating = new, data = old)
 *   <li>FORWARD: does every document the <b>new</b> schema could produce still validate against the
 *       <b>old</b> schema? (validating = old, data = new)
 * </ul>
 */
public final class JsonSchemaComparator {

  public List<Finding> compare(JsonNode validating, JsonNode data, Direction direction) {
    List<Finding> findings = new ArrayList<>();
    compare(validating, data, "#", direction, findings);
    return findings;
  }

  private void compare(
      JsonNode validating, JsonNode data, String path, Direction direction, List<Finding> out) {
    checkType(validating, data, path, direction, out);
    checkRequired(validating, data, path, direction, out);
    checkAdditionalProperties(validating, data, path, direction, out);
    checkEnum(validating, data, path, direction, out);
    checkNumericRange(validating, data, path, direction, out);
    checkStringLength(validating, data, path, direction, out);
    recurseProperties(validating, data, path, direction, out);
    recurseItems(validating, data, path, direction, out);
  }

  private void checkType(
      JsonNode validating, JsonNode data, String path, Direction direction, List<Finding> out) {
    Set<String> validatingTypes = typesOf(validating);
    Set<String> dataTypes = typesOf(data);
    if (validatingTypes.isEmpty() || dataTypes.isEmpty()) {
      return;
    }
    for (String dataType : dataTypes) {
      if (!validatingTypes.contains(dataType)) {
        out.add(
            error(
                direction,
                JsonSchemaRuleIds.TYPE_NOT_ACCEPTED,
                path,
                validatingLabel(direction)
                    + " only accepts type(s) "
                    + validatingTypes
                    + " at "
                    + path
                    + ", but "
                    + dataLabel(direction)
                    + " allows type '"
                    + dataType
                    + "'.",
                "align the 'type' constraints, or widen the validating schema's 'type' to include '"
                    + dataType
                    + "'"));
      }
    }
  }

  private void checkRequired(
      JsonNode validating, JsonNode data, String path, Direction direction, List<Finding> out) {
    Set<String> validatingRequired = arrayOfStrings(validating.get("required"));
    Set<String> dataRequired = arrayOfStrings(data.get("required"));
    for (String property : validatingRequired) {
      if (!dataRequired.contains(property)) {
        out.add(
            error(
                direction,
                JsonSchemaRuleIds.REQUIRED_NOT_GUARANTEED,
                path + "/required/" + property,
                validatingLabel(direction)
                    + " requires property '"
                    + property
                    + "' at "
                    + path
                    + ", but "
                    + dataLabel(direction)
                    + " does not guarantee it is present.",
                "do not add '"
                    + property
                    + "' to 'required' unless every producer already guarantees it, or provide"
                    + " a default"));
      }
    }
  }

  private void checkAdditionalProperties(
      JsonNode validating, JsonNode data, String path, Direction direction, List<Finding> out) {
    if (!isExplicitlyFalse(validating.get("additionalProperties"))) {
      return;
    }
    Set<String> validatingProps = fieldNames(validating.get("properties"));
    boolean dataAllowsArbitraryExtra = !isExplicitlyFalse(data.get("additionalProperties"));
    Set<String> dataProps = fieldNames(data.get("properties"));

    if (dataAllowsArbitraryExtra) {
      Set<String> unknownToValidating = new HashSet<>(dataProps);
      unknownToValidating.removeAll(validatingProps);
      String detail =
          unknownToValidating.isEmpty()
              ? "any property name"
              : "properties such as " + unknownToValidating;
      out.add(
          error(
              direction,
              JsonSchemaRuleIds.ADDITIONAL_PROPERTIES_CLOSED,
              path,
              validatingLabel(direction)
                  + " forbids additional properties at "
                  + path
                  + ", but "
                  + dataLabel(direction)
                  + " permits "
                  + detail
                  + " beyond the declared schema.",
              "set 'additionalProperties: false' on the data-producing schema as well, or drop it"
                  + " from the validating schema"));
      return;
    }

    for (String property : dataProps) {
      if (!validatingProps.contains(property)) {
        out.add(
            error(
                direction,
                JsonSchemaRuleIds.ADDITIONAL_PROPERTIES_CLOSED,
                path + "/properties/" + property,
                validatingLabel(direction)
                    + " forbids additional properties at "
                    + path
                    + ", but "
                    + dataLabel(direction)
                    + " declares property '"
                    + property
                    + "' which is unknown to the validating schema.",
                "add '" + property + "' to the validating schema's 'properties'"));
      }
    }
  }

  private void checkEnum(
      JsonNode validating, JsonNode data, String path, Direction direction, List<Finding> out) {
    JsonNode validatingEnum = validating.get("enum");
    if (validatingEnum == null || !validatingEnum.isArray()) {
      return;
    }
    Set<String> allowed = new HashSet<>();
    validatingEnum.forEach(node -> allowed.add(node.toString()));

    JsonNode dataEnum = data.get("enum");
    if (dataEnum == null || !dataEnum.isArray()) {
      out.add(
          error(
              direction,
              JsonSchemaRuleIds.ENUM_UNCONSTRAINED,
              path,
              validatingLabel(direction)
                  + " restricts values to an enum at "
                  + path
                  + ", but "
                  + dataLabel(direction)
                  + " does not restrict values to that same set.",
              "restrict the data-producing schema to the same enum, or drop the enum constraint"));
      return;
    }

    Iterator<JsonNode> values = dataEnum.elements();
    while (values.hasNext()) {
      JsonNode value = values.next();
      if (!allowed.contains(value.toString())) {
        out.add(
            error(
                direction,
                JsonSchemaRuleIds.ENUM_VALUE_NOT_ALLOWED,
                path,
                validatingLabel(direction)
                    + "'s enum at "
                    + path
                    + " does not include "
                    + value
                    + ", but "
                    + dataLabel(direction)
                    + " allows it.",
                "add " + value + " to the validating schema's enum"));
      }
    }
  }

  private void checkNumericRange(
      JsonNode validating, JsonNode data, String path, Direction direction, List<Finding> out) {
    checkBound(validating, data, "minimum", true, path, direction, out);
    checkBound(validating, data, "maximum", false, path, direction, out);
  }

  private void checkBound(
      JsonNode validating,
      JsonNode data,
      String field,
      boolean isLowerBound,
      String path,
      Direction direction,
      List<Finding> out) {
    JsonNode validatingBound = validating.get(field);
    if (validatingBound == null || !validatingBound.isNumber()) {
      return;
    }
    double validatingValue = validatingBound.asDouble();
    JsonNode dataBound = data.get(field);
    double dataValue =
        dataBound != null && dataBound.isNumber()
            ? dataBound.asDouble()
            : (isLowerBound ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);

    boolean violates = isLowerBound ? dataValue < validatingValue : dataValue > validatingValue;
    if (violates) {
      out.add(
          error(
              direction,
              JsonSchemaRuleIds.NUMBER_RANGE_NARROWER,
              path,
              validatingLabel(direction)
                  + " requires '"
                  + field
                  + "' "
                  + (isLowerBound ? ">= " : "<= ")
                  + validatingValue
                  + " at "
                  + path
                  + ", but "
                  + dataLabel(direction)
                  + " allows "
                  + (dataBound != null ? dataValue : "unbounded values")
                  + ".",
              "loosen '" + field + "' on the validating schema to at least " + dataValue));
    }
  }

  private void checkStringLength(
      JsonNode validating, JsonNode data, String path, Direction direction, List<Finding> out) {
    checkLength(validating, data, "minLength", true, path, direction, out);
    checkLength(validating, data, "maxLength", false, path, direction, out);
  }

  private void checkLength(
      JsonNode validating,
      JsonNode data,
      String field,
      boolean isLowerBound,
      String path,
      Direction direction,
      List<Finding> out) {
    JsonNode validatingBound = validating.get(field);
    if (validatingBound == null || !validatingBound.isNumber()) {
      return;
    }
    long validatingValue = validatingBound.asLong();
    JsonNode dataBound = data.get(field);
    long dataValue =
        dataBound != null && dataBound.isNumber()
            ? dataBound.asLong()
            : (isLowerBound ? 0L : Long.MAX_VALUE);

    boolean violates = isLowerBound ? dataValue < validatingValue : dataValue > validatingValue;
    if (violates) {
      out.add(
          error(
              direction,
              JsonSchemaRuleIds.STRING_LENGTH_NARROWER,
              path,
              validatingLabel(direction)
                  + " requires '"
                  + field
                  + "' "
                  + (isLowerBound ? ">= " : "<= ")
                  + validatingValue
                  + " at "
                  + path
                  + ", but "
                  + dataLabel(direction)
                  + " allows "
                  + (dataBound != null ? dataValue : "unbounded length")
                  + ".",
              "loosen '" + field + "' on the validating schema"));
    }
  }

  private void recurseProperties(
      JsonNode validating, JsonNode data, String path, Direction direction, List<Finding> out) {
    JsonNode validatingProps = validating.get("properties");
    JsonNode dataProps = data.get("properties");
    if (validatingProps == null || dataProps == null) {
      return;
    }
    Iterator<String> names = validatingProps.fieldNames();
    while (names.hasNext()) {
      String name = names.next();
      if (dataProps.has(name)) {
        compare(
            validatingProps.get(name),
            dataProps.get(name),
            path + "/properties/" + name,
            direction,
            out);
      }
    }
  }

  private void recurseItems(
      JsonNode validating, JsonNode data, String path, Direction direction, List<Finding> out) {
    JsonNode validatingItems = validating.get("items");
    JsonNode dataItems = data.get("items");
    if (validatingItems == null
        || dataItems == null
        || !validatingItems.isObject()
        || !dataItems.isObject()) {
      return;
    }
    compare(validatingItems, dataItems, path + "/items", direction, out);
  }

  private Set<String> typesOf(JsonNode schema) {
    JsonNode type = schema.get("type");
    if (type == null) {
      return Set.of();
    }
    if (type.isTextual()) {
      return Set.of(type.asText());
    }
    if (type.isArray()) {
      Set<String> types = new HashSet<>();
      type.forEach(node -> types.add(node.asText()));
      return types;
    }
    return Set.of();
  }

  private Set<String> arrayOfStrings(JsonNode array) {
    if (array == null || !array.isArray()) {
      return Set.of();
    }
    Set<String> values = new HashSet<>();
    array.forEach(node -> values.add(node.asText()));
    return values;
  }

  private Set<String> fieldNames(JsonNode object) {
    if (object == null || !object.isObject()) {
      return Set.of();
    }
    Set<String> names = new HashSet<>();
    object.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private boolean isExplicitlyFalse(JsonNode node) {
    return node != null && node.isBoolean() && !node.asBoolean();
  }

  private String validatingLabel(Direction direction) {
    return direction == Direction.BACKWARD ? "The new schema" : "The old schema";
  }

  private String dataLabel(Direction direction) {
    return direction == Direction.BACKWARD
        ? "data produced under the old schema"
        : "data produced under the new schema";
  }

  private Finding error(
      Direction direction, String ruleId, String path, String message, String suggestion) {
    return new Finding(Severity.ERROR, direction, ruleId, path, message, suggestion);
  }
}

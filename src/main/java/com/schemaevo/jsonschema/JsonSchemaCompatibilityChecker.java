package com.schemaevo.jsonschema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemaevo.core.CompatibilityChecker;
import com.schemaevo.core.SchemaEvoException;
import com.schemaevo.model.CompatibilityMode;
import com.schemaevo.model.CompatibilityResult;
import com.schemaevo.model.Direction;
import com.schemaevo.model.Finding;
import com.schemaevo.model.PairCheckResult;
import com.schemaevo.model.SchemaFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks JSON Schema compatibility by determining whether data producible under one schema is
 * guaranteed to still validate under the other.
 */
public final class JsonSchemaCompatibilityChecker implements CompatibilityChecker {

  private final ObjectMapper mapper = new ObjectMapper();
  private final JsonSchemaComparator comparator = new JsonSchemaComparator();

  @Override
  public CompatibilityResult check(
      List<String> priorVersions,
      List<String> priorLabels,
      String candidate,
      String candidateLabel,
      CompatibilityMode mode) {
    if (priorVersions.size() != priorLabels.size()) {
      throw new SchemaEvoException("priorVersions and priorLabels must be the same size");
    }
    if (priorVersions.isEmpty()) {
      throw new SchemaEvoException("at least one prior schema version is required");
    }

    JsonNode candidateSchema = parse(candidate, candidateLabel);
    List<Integer> indexesToCheck =
        mode.isTransitive() ? rangeOf(priorVersions.size()) : List.of(priorVersions.size() - 1);

    List<PairCheckResult> results = new ArrayList<>();
    for (int index : indexesToCheck) {
      JsonNode priorSchema = parse(priorVersions.get(index), priorLabels.get(index));
      String priorLabel = priorLabels.get(index);

      if (mode.checksBackward()) {
        List<Finding> findings =
            comparator.compare(candidateSchema, priorSchema, Direction.BACKWARD);
        results.add(new PairCheckResult(priorLabel, candidateLabel, Direction.BACKWARD, findings));
      }
      if (mode.checksForward()) {
        List<Finding> findings =
            comparator.compare(priorSchema, candidateSchema, Direction.FORWARD);
        results.add(new PairCheckResult(priorLabel, candidateLabel, Direction.FORWARD, findings));
      }
    }

    return new CompatibilityResult(SchemaFormat.JSON_SCHEMA, mode, candidateLabel, results);
  }

  private List<Integer> rangeOf(int size) {
    List<Integer> indexes = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      indexes.add(i);
    }
    return indexes;
  }

  private JsonNode parse(String schemaJson, String label) {
    try {
      JsonNode node = mapper.readTree(schemaJson);
      if (node == null || !node.isObject()) {
        throw new SchemaEvoException(
            "Malformed JSON Schema '" + label + "': expected a JSON object");
      }
      return node;
    } catch (JsonProcessingException e) {
      throw new SchemaEvoException(
          "Malformed JSON Schema '" + label + "': " + e.getOriginalMessage(), e);
    }
  }
}

package com.schemaevo.avro;

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
import org.apache.avro.Schema;

/** Checks Avro schema compatibility using Avro's own reader/writer resolution rules. */
public final class AvroCompatibilityChecker implements CompatibilityChecker {

  private final AvroSchemaComparator comparator = new AvroSchemaComparator();

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

    Schema candidateSchema = parse(candidate, candidateLabel);
    List<Integer> indexesToCheck =
        mode.isTransitive() ? rangeOf(priorVersions.size()) : List.of(priorVersions.size() - 1);

    List<PairCheckResult> results = new ArrayList<>();
    for (int index : indexesToCheck) {
      Schema priorSchema = parse(priorVersions.get(index), priorLabels.get(index));
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

    return new CompatibilityResult(SchemaFormat.AVRO, mode, candidateLabel, results);
  }

  private List<Integer> rangeOf(int size) {
    List<Integer> indexes = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      indexes.add(i);
    }
    return indexes;
  }

  private Schema parse(String schemaJson, String label) {
    try {
      return new Schema.Parser().parse(schemaJson);
    } catch (org.apache.avro.AvroRuntimeException e) {
      throw new SchemaEvoException("Malformed Avro schema '" + label + "': " + e.getMessage(), e);
    } catch (RuntimeException e) {
      throw new SchemaEvoException(
          "Could not parse Avro schema '" + label + "': " + e.getMessage(), e);
    }
  }
}

package com.schemaevo.avro;

import com.schemaevo.model.Direction;
import com.schemaevo.model.Finding;
import com.schemaevo.model.Severity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;

/**
 * Implements Avro's reader/writer schema resolution rules and explains every mismatch in
 * reader/writer terms.
 *
 * <p>The core question this class answers is: "can a consumer using {@code reader} correctly decode
 * data that was written using {@code writer}?" Both {@link
 * com.schemaevo.model.CompatibilityMode#BACKWARD} and {@link
 * com.schemaevo.model.CompatibilityMode#FORWARD} reduce to this same question with the reader and
 * writer schemas swapped: BACKWARD asks it with reader = new schema, writer = old schema; FORWARD
 * asks it with reader = old schema, writer = new schema.
 */
public final class AvroSchemaComparator {

  /** Type promotions Avro allows when a reader's declared type differs from the writer's. */
  private static final java.util.Map<Type, Set<Type>> PROMOTIONS =
      java.util.Map.of(
          Type.INT, Set.of(Type.LONG, Type.FLOAT, Type.DOUBLE),
          Type.LONG, Set.of(Type.FLOAT, Type.DOUBLE),
          Type.FLOAT, Set.of(Type.DOUBLE),
          Type.STRING, Set.of(Type.BYTES),
          Type.BYTES, Set.of(Type.STRING));

  /**
   * Compares a reader schema against a writer schema and reports every finding, from the point of
   * view of {@code direction}.
   */
  public List<Finding> compare(Schema reader, Schema writer, Direction direction) {
    List<Finding> findings = new ArrayList<>();
    compare(reader, writer, "#", direction, findings, new HashSet<>());
    return findings;
  }

  private void compare(
      Schema reader,
      Schema writer,
      String path,
      Direction direction,
      List<Finding> out,
      Set<String> seen) {
    // Guard against infinite recursion on recursive record definitions.
    String pairKey = path + "::" + identity(reader) + "::" + identity(writer);
    if (!seen.add(pairKey)) {
      return;
    }

    if (reader.getType() == Type.UNION || writer.getType() == Type.UNION) {
      compareUnion(reader, writer, path, direction, out, seen);
      return;
    }

    if (reader.getType() == writer.getType()) {
      compareSameType(reader, writer, path, direction, out, seen);
      return;
    }

    // Different primitive types: allowed only via a one-directional promotion.
    if (isPromotable(writer.getType(), reader.getType())) {
      return;
    }

    out.add(
        error(
            direction,
            AvroRuleIds.TYPE_MISMATCH,
            path,
            readerLabel(direction)
                + " declares "
                + describe(reader)
                + " at "
                + path
                + ", but "
                + writerLabel(direction)
                + " used "
                + describe(writer)
                + " and there is no valid type promotion between them.",
            "make the types match, or use a promotable pair (e.g. int -> long -> float -> double, "
                + "string <-> bytes)"));
  }

  private boolean isPromotable(Type writerType, Type readerType) {
    return PROMOTIONS.getOrDefault(writerType, Set.of()).contains(readerType);
  }

  private void compareSameType(
      Schema reader,
      Schema writer,
      String path,
      Direction direction,
      List<Finding> out,
      Set<String> seen) {
    switch (reader.getType()) {
      case RECORD -> compareRecord(reader, writer, path, direction, out, seen);
      case ENUM -> compareEnum(reader, writer, path, direction, out);
      case ARRAY ->
          compare(
              reader.getElementType(),
              writer.getElementType(),
              path + "/items",
              direction,
              out,
              seen);
      case MAP ->
          compare(
              reader.getValueType(), writer.getValueType(), path + "/values", direction, out, seen);
      case FIXED -> compareFixed(reader, writer, path, direction, out);
      default -> {
        // NULL, BOOLEAN, INT, LONG, FLOAT, DOUBLE, STRING, BYTES with matching types: compatible.
      }
    }
  }

  private void compareRecord(
      Schema reader,
      Schema writer,
      String path,
      Direction direction,
      List<Finding> out,
      Set<String> seen) {
    if (!namesMatch(reader, writer)) {
      out.add(
          error(
              direction,
              AvroRuleIds.RECORD_NAME_MISMATCH,
              path,
              readerLabel(direction)
                  + " expects a record named '"
                  + reader.getFullName()
                  + "' at "
                  + path
                  + ", but the record in "
                  + writerLabel(direction)
                  + " is named '"
                  + writer.getFullName()
                  + "' and no alias reconciles them.",
              "add '" + writer.getFullName() + "' to the reader record's aliases"));
      return;
    }

    for (Schema.Field readerField : reader.getFields()) {
      Schema.Field writerField = findMatchingField(readerField, writer);
      if (writerField != null) {
        compare(
            readerField.schema(),
            writerField.schema(),
            path + "/fields/" + readerField.name(),
            direction,
            out,
            seen);
        continue;
      }

      if (readerField.hasDefaultValue()) {
        continue;
      }

      out.add(
          error(
              direction,
              AvroRuleIds.FIELD_MISSING_NO_DEFAULT,
              path + "/fields/" + readerField.name(),
              readerLabel(direction)
                  + " has field '"
                  + readerField.name()
                  + "' with no default, but "
                  + writerLabel(direction)
                  + " has no matching field (by name or alias), so there is no value to populate"
                  + " it with.",
              "add a default value to field '"
                  + readerField.name()
                  + "', or add a field alias if this is a rename"));
    }
  }

  /** Finds the writer field that resolves to {@code readerField}, matching by name or alias. */
  private Schema.Field findMatchingField(Schema.Field readerField, Schema writer) {
    Schema.Field byName = writer.getField(readerField.name());
    if (byName != null) {
      return byName;
    }
    for (String alias : readerField.aliases()) {
      Schema.Field byAlias = writer.getField(alias);
      if (byAlias != null) {
        return byAlias;
      }
    }
    return null;
  }

  private void compareEnum(
      Schema reader, Schema writer, String path, Direction direction, List<Finding> out) {
    if (!namesMatch(reader, writer)) {
      out.add(
          error(
              direction,
              AvroRuleIds.ENUM_NAME_MISMATCH,
              path,
              readerLabel(direction)
                  + " expects an enum named '"
                  + reader.getFullName()
                  + "' at "
                  + path
                  + ", but the enum in "
                  + writerLabel(direction)
                  + " is named '"
                  + writer.getFullName()
                  + "' and no alias reconciles them.",
              "add '" + writer.getFullName() + "' to the reader enum's aliases"));
      return;
    }

    Set<String> readerSymbols = new HashSet<>(reader.getEnumSymbols());
    boolean readerHasDefault = reader.getEnumDefault() != null;
    for (String symbol : writer.getEnumSymbols()) {
      if (readerSymbols.contains(symbol)) {
        continue;
      }
      if (readerHasDefault) {
        continue;
      }
      out.add(
          error(
              direction,
              AvroRuleIds.ENUM_SYMBOL_UNKNOWN,
              path,
              readerLabel(direction)
                  + " does not know enum symbol '"
                  + symbol
                  + "' at "
                  + path
                  + ", but "
                  + writerLabel(direction)
                  + " wrote it.",
              "add symbol '" + symbol + "' to the reader enum, or set an enum default symbol"));
    }
  }

  private void compareFixed(
      Schema reader, Schema writer, String path, Direction direction, List<Finding> out) {
    if (!namesMatch(reader, writer)) {
      out.add(
          error(
              direction,
              AvroRuleIds.FIXED_NAME_MISMATCH,
              path,
              readerLabel(direction)
                  + " expects a fixed type named '"
                  + reader.getFullName()
                  + "' at "
                  + path
                  + ", but the fixed type in "
                  + writerLabel(direction)
                  + " is named '"
                  + writer.getFullName()
                  + "' and no alias reconciles them.",
              "add '" + writer.getFullName() + "' to the reader fixed type's aliases"));
      return;
    }
    if (reader.getFixedSize() != writer.getFixedSize()) {
      out.add(
          error(
              direction,
              AvroRuleIds.FIXED_SIZE_MISMATCH,
              path,
              readerLabel(direction)
                  + " declares fixed size "
                  + reader.getFixedSize()
                  + " at "
                  + path
                  + ", but "
                  + writerLabel(direction)
                  + " used size "
                  + writer.getFixedSize()
                  + ".",
              "fixed sizes must match exactly; introduce a new type name instead of resizing"));
    }
  }

  private void compareUnion(
      Schema reader,
      Schema writer,
      String path,
      Direction direction,
      List<Finding> out,
      Set<String> seen) {
    if (reader.getType() == Type.UNION && writer.getType() == Type.UNION) {
      for (Schema writerBranch : writer.getTypes()) {
        if (!anyReaderBranchMatches(reader, writerBranch, seen)) {
          out.add(unionMismatch(direction, path, writerBranch));
        }
      }
      return;
    }

    if (writer.getType() == Type.UNION) {
      // A non-union reader must be able to read every branch the writer might produce.
      for (Schema writerBranch : writer.getTypes()) {
        List<Finding> trial = new ArrayList<>();
        compare(reader, writerBranch, path, direction, trial, new HashSet<>(seen));
        if (trial.stream().anyMatch(Finding::isBlocking)) {
          out.add(unionMismatch(direction, path, writerBranch));
        }
      }
      return;
    }

    // reader is a union, writer is a single type: at least one reader branch must accept it.
    if (!anyReaderBranchMatches(reader, writer, seen)) {
      out.add(unionMismatch(direction, path, writer));
    }
  }

  private boolean anyReaderBranchMatches(
      Schema readerUnion, Schema writerBranch, Set<String> seen) {
    for (Schema readerBranch : readerUnion.getTypes()) {
      List<Finding> trial = new ArrayList<>();
      // Direction does not affect whether a match exists, only the message, so BACKWARD is fine
      // here.
      compare(readerBranch, writerBranch, "#", Direction.BACKWARD, trial, new HashSet<>(seen));
      if (trial.stream().noneMatch(Finding::isBlocking)) {
        return true;
      }
    }
    return false;
  }

  private Finding unionMismatch(Direction direction, String path, Schema writerBranch) {
    return error(
        direction,
        AvroRuleIds.UNION_NO_MATCHING_BRANCH,
        path,
        readerLabel(direction)
            + " has no branch at "
            + path
            + " able to read a value of type "
            + describe(writerBranch)
            + ", which "
            + writerLabel(direction)
            + " can produce.",
        "add a compatible branch to the reader union, e.g. "
            + describe(writerBranch)
            + ", or narrow the writer union instead");
  }

  /**
   * Two named schemas match if their full names are equal, or the reader declares an alias for the
   * writer's name.
   */
  private boolean namesMatch(Schema reader, Schema writer) {
    if (reader.getFullName().equals(writer.getFullName())) {
      return true;
    }
    return reader.getAliases().contains(writer.getFullName());
  }

  private String describe(Schema schema) {
    return switch (schema.getType()) {
      case RECORD, ENUM, FIXED -> schema.getType().getName() + " '" + schema.getFullName() + "'";
      default -> schema.getType().getName();
    };
  }

  private String identity(Schema schema) {
    return switch (schema.getType()) {
      case RECORD, ENUM, FIXED -> schema.getFullName();
      default -> schema.toString();
    };
  }

  /** Names which schema plays the reader role for {@code direction}, for use in messages. */
  private String readerLabel(Direction direction) {
    return direction == Direction.BACKWARD
        ? "The new schema (as a reader)"
        : "The old schema (as a reader)";
  }

  /** Names which schema plays the writer role for {@code direction}, for use in messages. */
  private String writerLabel(Direction direction) {
    return direction == Direction.BACKWARD
        ? "the old schema (as the writer)"
        : "the new schema (as the writer)";
  }

  private Finding error(
      Direction direction, String ruleId, String path, String message, String suggestion) {
    return new Finding(Severity.ERROR, direction, ruleId, path, message, suggestion);
  }
}

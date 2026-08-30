package com.schemaevo.avro;

import static org.assertj.core.api.Assertions.assertThat;

import com.schemaevo.model.Direction;
import com.schemaevo.model.Finding;
import java.util.List;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

class AvroSchemaComparatorTest {

  private final AvroSchemaComparator comparator = new AvroSchemaComparator();

  private static Schema schema(String json) {
    return new Schema.Parser().parse(json);
  }

  private List<Finding> errors(List<Finding> findings) {
    return findings.stream().filter(Finding::isBlocking).toList();
  }

  // ---- field addition / removal --------------------------------------------------------------

  @Test
  void fieldAddedWithDefault_isBackwardCompatible() {
    Schema oldSchema =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"string\"}]}");
    Schema newSchema =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"string\"},"
                + "{\"name\":\"b\",\"type\":\"string\",\"default\":\"x\"}]}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void fieldAddedWithoutDefault_breaksBackwardCompatibility() {
    Schema oldSchema =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"string\"}]}");
    Schema newSchema =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"string\"},"
                + "{\"name\":\"b\",\"type\":\"string\"}]}");

    List<Finding> findings = comparator.compare(newSchema, oldSchema, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.record.field-missing-no-default");
    assertThat(errors(findings).get(0).message()).contains("b").contains("no default");
    assertThat(errors(findings).get(0).suggestion()).contains("default");
  }

  @Test
  void fieldRemovedWithDefault_isForwardCompatible() {
    // FORWARD: reader = old schema (still has the field, with a default), writer = new schema
    // (field removed).
    Schema oldSchema =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"string\"},"
                + "{\"name\":\"b\",\"type\":\"string\",\"default\":\"x\"}]}");
    Schema newSchema =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"string\"}]}");

    List<Finding> findings = comparator.compare(oldSchema, newSchema, Direction.FORWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void fieldRemovedWithoutDefault_breaksForwardCompatibility() {
    Schema oldSchema =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"string\"},"
                + "{\"name\":\"b\",\"type\":\"string\"}]}");
    Schema newSchema =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"string\"}]}");

    List<Finding> findings = comparator.compare(oldSchema, newSchema, Direction.FORWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.record.field-missing-no-default");
  }

  @Test
  void fieldAddedWithDefault_alsoForwardCompatible() {
    Schema oldSchema =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"string\"}]}");
    Schema newSchema =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"string\"},"
                + "{\"name\":\"b\",\"type\":\"string\",\"default\":\"x\"}]}");

    // FORWARD: reader = old (no field b), writer = new (has field b) -> old reader ignores it.
    List<Finding> findings = comparator.compare(oldSchema, newSchema, Direction.FORWARD);
    assertThat(errors(findings)).isEmpty();
  }

  // ---- type promotion --------------------------------------------------------------------------

  @Test
  void intToLongPromotion_isCompatible() {
    Schema writer = primitiveField("int");
    Schema reader = primitiveField("long");
    assertThat(errors(comparator.compare(reader, writer, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void intToFloatPromotion_isCompatible() {
    Schema writer = primitiveField("int");
    Schema reader = primitiveField("float");
    assertThat(errors(comparator.compare(reader, writer, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void longToDoublePromotion_isCompatible() {
    Schema writer = primitiveField("long");
    Schema reader = primitiveField("double");
    assertThat(errors(comparator.compare(reader, writer, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void floatToDoublePromotion_isCompatible() {
    Schema writer = primitiveField("float");
    Schema reader = primitiveField("double");
    assertThat(errors(comparator.compare(reader, writer, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void stringToBytesPromotion_isCompatible() {
    Schema writer = primitiveField("string");
    Schema reader = primitiveField("bytes");
    assertThat(errors(comparator.compare(reader, writer, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void bytesToStringPromotion_isCompatible() {
    Schema writer = primitiveField("bytes");
    Schema reader = primitiveField("string");
    assertThat(errors(comparator.compare(reader, writer, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void reversePromotion_longToInt_isNotAllowed() {
    // Promotion only works one direction: a reader declaring "int" cannot read writer "long".
    Schema writer = primitiveField("long");
    Schema reader = primitiveField("int");
    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.type.mismatch");
  }

  @Test
  void unrelatedTypeChange_isIncompatible() {
    Schema writer = primitiveField("string");
    Schema reader = primitiveField("int");
    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.type.mismatch");
  }

  @Test
  void identicalPrimitiveType_isCompatible() {
    Schema writer = primitiveField("string");
    Schema reader = primitiveField("string");
    assertThat(errors(comparator.compare(reader, writer, Direction.BACKWARD))).isEmpty();
  }

  private Schema primitiveField(String type) {
    return schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"a\",\"type\":\""
                + type
                + "\"}]}")
        .getField("a")
        .schema();
  }

  // ---- enums -------------------------------------------------------------------------------

  @Test
  void enumSymbolAddition_breaksForwardCompatibility() {
    // FORWARD: reader = old enum (fewer symbols), writer = new enum (added a symbol), no default.
    Schema oldEnum = schema("{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"A\",\"B\"]}");
    Schema newEnum = schema("{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"A\",\"B\",\"C\"]}");

    List<Finding> findings = comparator.compare(oldEnum, newEnum, Direction.FORWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.enum.symbol-unknown");
  }

  @Test
  void enumSymbolAdditionWithDefault_isForwardCompatible() {
    Schema oldEnum =
        schema("{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"A\",\"B\"],\"default\":\"A\"}");
    Schema newEnum = schema("{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"A\",\"B\",\"C\"]}");

    List<Finding> findings = comparator.compare(oldEnum, newEnum, Direction.FORWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void enumSymbolAddition_isAlwaysBackwardCompatible() {
    // BACKWARD: reader = new enum (superset), writer = old enum (subset) -> always fine.
    Schema oldEnum = schema("{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"A\",\"B\"]}");
    Schema newEnum = schema("{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"A\",\"B\",\"C\"]}");

    List<Finding> findings = comparator.compare(newEnum, oldEnum, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void enumNameMismatch_withoutAlias_isIncompatible() {
    Schema reader = schema("{\"type\":\"enum\",\"name\":\"E1\",\"symbols\":[\"A\"]}");
    Schema writer = schema("{\"type\":\"enum\",\"name\":\"E2\",\"symbols\":[\"A\"]}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.enum.name-mismatch");
  }

  @Test
  void enumNameMismatch_withAlias_isCompatible() {
    Schema reader =
        schema("{\"type\":\"enum\",\"name\":\"E2\",\"aliases\":[\"E1\"],\"symbols\":[\"A\"]}");
    Schema writer = schema("{\"type\":\"enum\",\"name\":\"E1\",\"symbols\":[\"A\"]}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  // ---- records: rename, aliases, namespaces -------------------------------------------------

  @Test
  void recordRenameWithoutAlias_isIncompatible() {
    Schema reader = schema("{\"type\":\"record\",\"name\":\"NewName\",\"fields\":[]}");
    Schema writer = schema("{\"type\":\"record\",\"name\":\"OldName\",\"fields\":[]}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.record.name-mismatch");
  }

  @Test
  void recordRenameWithAlias_isCompatible() {
    Schema reader =
        schema(
            "{\"type\":\"record\",\"name\":\"NewName\",\"aliases\":[\"OldName\"],\"fields\":[]}");
    Schema writer = schema("{\"type\":\"record\",\"name\":\"OldName\",\"fields\":[]}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void namespaceChangeWithoutAlias_isIncompatible() {
    Schema reader =
        schema("{\"type\":\"record\",\"name\":\"R\",\"namespace\":\"new.ns\",\"fields\":[]}");
    Schema writer =
        schema("{\"type\":\"record\",\"name\":\"R\",\"namespace\":\"old.ns\",\"fields\":[]}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.record.name-mismatch");
  }

  @Test
  void namespaceChangeWithAlias_isCompatible() {
    Schema reader =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"namespace\":\"new.ns\","
                + "\"aliases\":[\"old.ns.R\"],\"fields\":[]}");
    Schema writer =
        schema("{\"type\":\"record\",\"name\":\"R\",\"namespace\":\"old.ns\",\"fields\":[]}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void fieldRenameWithAlias_rescuesOtherwiseBreakingChange() {
    Schema reader =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"newName\",\"type\":\"string\",\"aliases\":[\"oldName\"]}]}");
    Schema writer =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"oldName\",\"type\":\"string\"}]}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void fieldRenameWithoutAlias_breaksCompatibility() {
    Schema reader =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"newName\",\"type\":\"string\"}]}");
    Schema writer =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"oldName\",\"type\":\"string\"}]}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.record.field-missing-no-default");
  }

  @Test
  void nestedRecordFieldTypeMismatch_isReportedWithPath() {
    Schema reader =
        schema(
            "{\"type\":\"record\",\"name\":\"Outer\",\"fields\":[{\"name\":\"inner\",\"type\":"
                + "{\"type\":\"record\",\"name\":\"Inner\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}}]}");
    Schema writer =
        schema(
            "{\"type\":\"record\",\"name\":\"Outer\",\"fields\":[{\"name\":\"inner\",\"type\":"
                + "{\"type\":\"record\",\"name\":\"Inner\",\"fields\":[{\"name\":\"x\",\"type\":\"string\"}]}}]}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).path()).isEqualTo("#/fields/inner/fields/x");
  }

  // ---- unions ------------------------------------------------------------------------------

  @Test
  void unionWidening_readerHasExtraBranch_isBackwardCompatible() {
    Schema writer = primitiveField("string");
    Schema reader =
        schema(
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                    + "{\"name\":\"a\",\"type\":[\"string\",\"int\"]}]}")
            .getField("a")
            .schema();

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void unionNarrowing_readerMissingBranch_isIncompatible() {
    Schema writer =
        schema(
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                    + "{\"name\":\"a\",\"type\":[\"string\",\"int\"]}]}")
            .getField("a")
            .schema();
    Schema reader = primitiveField("string");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.union.no-matching-branch");
  }

  @Test
  void unionToUnion_allBranchesResolvable_isCompatible() {
    Schema writer =
        schema(
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                    + "{\"name\":\"a\",\"type\":[\"null\",\"int\"]}]}")
            .getField("a")
            .schema();
    Schema reader =
        schema(
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                    + "{\"name\":\"a\",\"type\":[\"null\",\"long\"]}]}")
            .getField("a")
            .schema();

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  @Test
  void unionToUnion_missingBranch_isIncompatible() {
    Schema writer =
        schema(
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                    + "{\"name\":\"a\",\"type\":[\"null\",\"string\"]}]}")
            .getField("a")
            .schema();
    Schema reader =
        schema(
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                    + "{\"name\":\"a\",\"type\":[\"null\",\"int\"]}]}")
            .getField("a")
            .schema();

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
  }

  @Test
  void nonUnionReader_canResolveSingleValuedWriterUnion() {
    Schema writer =
        schema(
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                    + "{\"name\":\"a\",\"type\":[\"int\",\"long\"]}]}")
            .getField("a")
            .schema();
    Schema reader = primitiveField("long");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).isEmpty();
  }

  // ---- arrays / maps -------------------------------------------------------------------------

  @Test
  void arrayItemTypePromotion_isCompatible() {
    Schema writer = schema("{\"type\":\"array\",\"items\":\"int\"}");
    Schema reader = schema("{\"type\":\"array\",\"items\":\"long\"}");

    assertThat(errors(comparator.compare(reader, writer, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void arrayItemTypeMismatch_isIncompatible() {
    Schema writer = schema("{\"type\":\"array\",\"items\":\"string\"}");
    Schema reader = schema("{\"type\":\"array\",\"items\":\"int\"}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).path()).isEqualTo("#/items");
  }

  @Test
  void mapValueTypeMismatch_isIncompatible() {
    Schema writer = schema("{\"type\":\"map\",\"values\":\"string\"}");
    Schema reader = schema("{\"type\":\"map\",\"values\":\"int\"}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).path()).isEqualTo("#/values");
  }

  @Test
  void mapValueTypePromotion_isCompatible() {
    Schema writer = schema("{\"type\":\"map\",\"values\":\"float\"}");
    Schema reader = schema("{\"type\":\"map\",\"values\":\"double\"}");

    assertThat(errors(comparator.compare(reader, writer, Direction.BACKWARD))).isEmpty();
  }

  // ---- fixed ----------------------------------------------------------------------------------

  @Test
  void fixedMatchingSizeAndName_isCompatible() {
    Schema schemaA = schema("{\"type\":\"fixed\",\"name\":\"F\",\"size\":16}");
    Schema schemaB = schema("{\"type\":\"fixed\",\"name\":\"F\",\"size\":16}");

    assertThat(errors(comparator.compare(schemaA, schemaB, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void fixedSizeMismatch_isIncompatible() {
    Schema reader = schema("{\"type\":\"fixed\",\"name\":\"F\",\"size\":16}");
    Schema writer = schema("{\"type\":\"fixed\",\"name\":\"F\",\"size\":8}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.fixed.size-mismatch");
  }

  @Test
  void fixedNameMismatch_withoutAlias_isIncompatible() {
    Schema reader = schema("{\"type\":\"fixed\",\"name\":\"F1\",\"size\":8}");
    Schema writer = schema("{\"type\":\"fixed\",\"name\":\"F2\",\"size\":8}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(1);
    assertThat(errors(findings).get(0).ruleId()).isEqualTo("avro.fixed.name-mismatch");
  }

  @Test
  void fixedNameMismatch_withAlias_isCompatible() {
    Schema reader = schema("{\"type\":\"fixed\",\"name\":\"F2\",\"aliases\":[\"F1\"],\"size\":8}");
    Schema writer = schema("{\"type\":\"fixed\",\"name\":\"F1\",\"size\":8}");

    assertThat(errors(comparator.compare(reader, writer, Direction.BACKWARD))).isEmpty();
  }

  // ---- recursion safety -----------------------------------------------------------------------

  @Test
  void selfReferencingRecord_doesNotInfiniteLoop() {
    String json =
        "{\"type\":\"record\",\"name\":\"Node\",\"fields\":["
            + "{\"name\":\"value\",\"type\":\"int\"},"
            + "{\"name\":\"next\",\"type\":[\"null\",\"Node\"],\"default\":null}]}";
    Schema reader = schema(json);
    Schema writer = schema(json);

    assertThat(errors(comparator.compare(reader, writer, Direction.BACKWARD))).isEmpty();
  }

  @Test
  void multipleFindings_areAllReported() {
    Schema reader =
        schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
                + "{\"name\":\"a\",\"type\":\"string\"},"
                + "{\"name\":\"b\",\"type\":\"string\"},"
                + "{\"name\":\"c\",\"type\":\"string\"}]}");
    Schema writer = schema("{\"type\":\"record\",\"name\":\"R\",\"fields\":[]}");

    List<Finding> findings = comparator.compare(reader, writer, Direction.BACKWARD);
    assertThat(errors(findings)).hasSize(3);
  }
}

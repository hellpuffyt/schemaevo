package com.schemaevo.avro;

/** Stable identifiers for every Avro compatibility rule schemaevo enforces. */
final class AvroRuleIds {

  static final String TYPE_MISMATCH = "avro.type.mismatch";
  static final String PROMOTION_INVALID = "avro.type.promotion-invalid";
  static final String RECORD_NAME_MISMATCH = "avro.record.name-mismatch";
  static final String FIELD_MISSING_NO_DEFAULT = "avro.record.field-missing-no-default";
  static final String ENUM_NAME_MISMATCH = "avro.enum.name-mismatch";
  static final String ENUM_SYMBOL_UNKNOWN = "avro.enum.symbol-unknown";
  static final String UNION_NO_MATCHING_BRANCH = "avro.union.no-matching-branch";
  static final String FIXED_NAME_MISMATCH = "avro.fixed.name-mismatch";
  static final String FIXED_SIZE_MISMATCH = "avro.fixed.size-mismatch";

  private AvroRuleIds() {}
}

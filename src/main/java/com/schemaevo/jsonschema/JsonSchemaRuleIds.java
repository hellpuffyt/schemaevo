package com.schemaevo.jsonschema;

/** Stable identifiers for every JSON Schema compatibility rule schemaevo enforces. */
final class JsonSchemaRuleIds {

  static final String TYPE_NOT_ACCEPTED = "jsonschema.type.not-accepted";
  static final String REQUIRED_NOT_GUARANTEED = "jsonschema.required.not-guaranteed";
  static final String ADDITIONAL_PROPERTIES_CLOSED = "jsonschema.additional-properties.closed";
  static final String ENUM_VALUE_NOT_ALLOWED = "jsonschema.enum.value-not-allowed";
  static final String ENUM_UNCONSTRAINED = "jsonschema.enum.unconstrained-source";
  static final String NUMBER_RANGE_NARROWER = "jsonschema.number.range-narrower";
  static final String STRING_LENGTH_NARROWER = "jsonschema.string.length-narrower";

  private JsonSchemaRuleIds() {}
}

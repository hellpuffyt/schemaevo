# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] - 2026-08-30

### Added

- Avro compatibility checker implementing reader/writer resolution rules: field
  add/remove with and without defaults, type promotion, enum symbol rules
  (including enum defaults), union widening/narrowing, record/field/enum/fixed
  aliases, and namespace changes.
- JSON Schema compatibility checker covering `required`, `type`, `enum`,
  `additionalProperties`, numeric range, and string length constraints, with
  recursive checks through `properties` and `items`.
- `BACKWARD`, `FORWARD`, `FULL` compatibility modes plus `*_TRANSITIVE`
  variants that check a candidate schema against every prior version in a
  history, not just the immediate predecessor.
- Command-line interface (`schemaevo check`) with human-readable and JSON
  report output, and process exit codes suitable for CI gating.
- `--history` mode for checking a directory of numbered schema versions.
- Example Avro and JSON Schema version histories under `examples/`, both a
  compatible chain and one containing a genuine breaking change.

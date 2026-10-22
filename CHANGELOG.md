# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed

- `schemaevo check ...` as documented now works: `check` is a real
  subcommand. Previously `check` was read as a schema file name, so every
  positional example in the README failed with exit code 2.
- Avro: a record that refers to itself through an array or map no longer
  crashes with `StackOverflowError`.
- Avro: a namespace-only change is no longer reported as a break; named types
  are matched by unqualified name, as the Avro specification and Avro's own
  `SchemaCompatibility` do.
- JSON Schema: adding a `type` to a previously untyped property is now
  reported as a backward break, and `integer` data is accepted by a `number`
  schema.
- Passing schema files together with `--history` is now a usage error (exit
  2) instead of silently ignoring the files.
- An unexpected internal failure exits with 2, never 1 ("incompatible").

### Security

- Upgraded Jackson to 2.22.4 (managed via the Jackson BOM so Avro cannot pull
  an older `jackson-core`), Avro to 1.12.2 and `commons-lang3` to 3.21.0, which
  clears every OSV advisory against the runtime dependency set.

### Added

- Differential tests that check every Avro verdict against Apache Avro's own
  `SchemaCompatibility` and `SchemaValidatorBuilder`.
- `SECURITY.md`, `.editorconfig`, `docs/PRODUCTION_READINESS.md`.

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

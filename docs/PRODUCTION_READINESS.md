# Production readiness — schemaevo

Audit of the `audit/production-readiness` branch. Evidence only; the
classification at the end is a proposal for the maintainer to confirm.

- Tested commit: `75458d28f7682cd21aa77d15f3abbcb6a2bd6776` (this file was
  added in the next commit and changes no code)
- Date: 2026-10-22
- Host: macOS 27 (Apple Silicon), Temurin-compatible OpenJDK 21, Maven 3.10.

## Scope

A command-line tool (single shaded JAR) that checks whether a candidate Avro
or JSON Schema is compatible with one or more prior versions under
`BACKWARD`, `FORWARD`, `FULL` or their `*_TRANSITIVE` variants, explains each
break in reader/writer terms with a suggested fix, prints a human or JSON
report, and exits `0` compatible / `1` incompatible / `2` error.

Architecture: `model` (value types) → `avro` / `jsonschema` (comparators that
walk both schemas recursively, plus checkers that map modes to reader/writer
pairs) → `report` (renderers) → `cli` (picocli: `schemaevo` with a `check`
subcommand). Avro schemas are parsed with `org.apache.avro`, JSON Schema with
Jackson. No network access, no state.

## Platforms and prerequisites

- JDK 21+ and Maven 3.9+. Verified locally on macOS arm64 with JDK 21.
- CI (`.github/workflows/ci.yml`): JDK 21 and 25 on ubuntu-latest and
  windows-latest, a lint job (Spotless, SpotBugs) and a smoke job against the
  bundled examples. JDK 25 and Windows were **not** run locally.

## Clean-clone verification

`git clone -b audit/production-readiness <working clone> <fresh scratch dir>`,
then the README followed literally:

| Command | Exit | Result |
|---|---|---|
| `mvn -B package` | 0 | `target/schemaevo.jar` built |
| `java -jar target/schemaevo.jar --help` | 0 | usage printed |
| `java -jar target/schemaevo.jar check --format avro --mode BACKWARD examples/avro/orders-breaking/v1.avsc …/v2.avsc …/v3.avsc` | 1 | `avro.record.field-missing-no-default at #/fields/priority`, as in the README |
| `java -jar target/schemaevo.jar check --format avro --mode BACKWARD_TRANSITIVE --history examples/avro/orders-compatible` | 0 | compatible |
| `java -jar target/schemaevo.jar check --format avro --mode BACKWARD …/v2.avsc …/v3.avsc` | 1 | same report as the README "Examples" section |
| `mvn -B verify` | 0 | 292 tests: 292 passed, 0 failed, 0 errors, 0 skipped; Spotless clean; SpotBugs `BugInstance size is 0` |

## Commands executed (working clone)

| Command | Exit | Outcome |
|---|---|---|
| `mvn -B verify` (baseline, default branch `6b2613d`) | 0 | 105 passed, 0 failed, 0 skipped; Spotless + SpotBugs clean |
| `java -jar target/schemaevo.jar check --format avro …` (README positional form, baseline) | 2 | `error: could not read schema file: check` — see bug 1 |
| recursive Avro record via array, baseline | 1 | `java.lang.StackOverflowError` stack trace — see bug 2 |
| `mvn -B test -Dtest=AvroReferenceAgreementTest` (new test, against baseline code) | 1 | 177 run, 4 failures (namespace-only changes), 6 errors (StackOverflowError) |
| `mvn -B verify` (after) | 0 | 292 passed, 0 failed, 0 errors, 0 skipped; Spotless + SpotBugs clean |
| examples: `--history` on all four example dirs, BACKWARD_TRANSITIVE and FULL_TRANSITIVE | 0/1 as expected | compatible dirs exit 0, breaking dirs exit 1 |
| `mvn dependency:list -DincludeScope=runtime` + OSV batch query (api.osv.dev) | 0 | before: 17 advisories (jackson-core 4, jackson-databind 12, commons-lang3 1); after: 0 across 10 packages |
| history secret scan (`git grep` over every commit for token/key/path patterns) | — | no hits |

## Verdicts checked against Avro's reference implementation

`AvroReferenceAgreementTest` compares this project's compatible/incompatible
verdict with Apache Avro's own code:

- 169 reader/writer pairs against `SchemaCompatibility.checkReaderWriterCompatibility`:
  all 64 primitive-type combinations (bare and as a record field), field
  add/remove with and without defaults, field and record aliases, record /
  enum / fixed renames and namespace-only changes, enum symbol add/remove and
  enum defaults, fixed size changes, array/map promotion and demotion, union
  widen/narrow (including via promotion) in all three union shapes, and
  recursive records through unions, arrays and maps.
- Every mode (`BACKWARD`…`FULL_TRANSITIVE`) against `SchemaValidatorBuilder`
  (`canReadStrategy` / `canBeReadStrategy` / `mutualReadStrategy`,
  `validateLatest` / `validateAll`) on both bundled Avro example histories and
  a hand-made history with six candidates (field without default, removed
  field, promoted type, new required field, unchanged, reverted).

JSON Schema has no reference implementation to compare against; it is covered
by hand-made fixtures in both directions (`JsonSchemaComparatorTest`,
`JsonSchemaCompatibilityCheckerTest`) and the bundled examples.

## Bugs found and fixed

| # | Bug | Fix | Regression test |
|---|---|---|---|
| 1 | The README documents `schemaevo check …`, but `check` was not a subcommand; it was read as a schema file, so every positional example failed with exit 2 (CI only exercised `--history`, where positional args were ignored). | `check` is a real picocli subcommand of `schemaevo`. | `CheckCommandTest.documentedCheckSubcommand_works`, `missingSubcommand_exitsTwo`; CI smoke step for the positional form |
| 2 | A record that refers to itself through an array or map recursed forever (`StackOverflowError`, exit 1 = "incompatible"). The guard was keyed on the ever-growing path and called `Schema.toString()` on recursive schemas. | Guard on the in-progress (reader, writer) record-name pair, like Avro's own check. | `AvroSchemaComparatorTest.recursiveRecordThrough{Array,Map}…`, `CheckCommandTest.recursiveAvroSchema_doesNotCrash`, reference-agreement cases |
| 3 | A namespace-only change was reported as a break. The Avro specification matches named types by unqualified name, and `SchemaCompatibility` agrees. | Match by unqualified name (aliases still compared by full name). The existing test `namespaceChangeWithoutAlias_isIncompatible` asserted the old, wrong verdict; it was changed to `namespaceOnlyChangeWithoutAlias_isCompatible`, with this justification in a comment, and a rename across namespaces is still asserted as a break. | `AvroSchemaComparatorTest`, reference-agreement cases |
| 4 | JSON Schema: adding a `type` to a property that had none was reported as compatible (missed break). | Report `jsonschema.type.unconstrained-source`; an untyped `enum` is judged by the types of its values. | `JsonSchemaComparatorTest.typeAddedOverUnconstrainedProperty_isBreaking`, `untypedEnumSourceIsJudgedByItsValues` |
| 5 | JSON Schema: `integer` data against a `number` schema was reported as a break (false positive). | `number` accepts `integer`. | `JsonSchemaComparatorTest.integerDataIsAcceptedByNumberSchema` |
| 6 | `--history` together with positional files silently ignored the files. | Usage error, exit 2. | `CheckCommandTest.historyAndPositionalFilesTogether_exitTwo` |
| 7 | Runtime dependencies had 17 known advisories (Jackson 2.17.2, commons-lang3 3.14.0). | Jackson 2.22.4 through the Jackson BOM (so Avro cannot pull in an older `jackson-core`), Avro 1.12.2, commons-lang3 3.21.0. | full suite + OSV re-query (0 advisories) |

An unexpected internal exception now exits with 2, never 1, so a crash
cannot be mistaken for "incompatible".

## Security review

- Input is local files named on the command line; there is no network, no
  deserialization into arbitrary types (Jackson `readTree` only), and no
  writes. Jackson's default nesting/length limits apply to both parsers.
- CI now declares `permissions: contents: read`. Action references are tag
  pins (`@v4`), left as they were; pinning them to SHAs is a maintainer choice.
- `SECURITY.md` added (private advisory link).

## Limitations and unresolved risks

- JSON Schema support is partial by design (README "Limitations"): `$ref`,
  combinators, `const`, `pattern`, `format`, exclusive bounds, `multipleOf`,
  tuple `items`, array size and `patternProperties` are ignored, so changes
  limited to them are reported as compatible.
- Adding an optional property to an open JSON Schema object is reported as
  compatible.
- Avro logical types are ignored (as in Avro's own check).
- JDK 25 and Windows are only exercised by CI, not locally.
- Avro prints a harmless SLF4J "no providers" notice on stderr.

## CI status

- Default branch (`6b2613d`): success.
- Audit branch: pending until pushed.

## Acceptance checklist

| Item | Expected behavior | Command / test | Result | Actual | Reference |
|---|---|---|---|---|---|
| README build | `mvn -B package` builds `target/schemaevo.jar` | clean clone | pass | exit 0 | pom.xml |
| README positional usage | `check --format avro … v2 v3` reports the `priority` break, exit 1 | clean clone | pass | exit 1, report matches README | bug 1 |
| History mode | compatible history exits 0, breaking exits 1 | CI smoke commands run locally | pass | 0 / 1 | CheckCommand |
| Avro verdicts match Avro | every reader/writer pair agrees with `SchemaCompatibility` | `AvroReferenceAgreementTest` | pass | 169/169 | bugs 2, 3 |
| Modes match Avro | BACKWARD/FORWARD/FULL (+transitive) agree with `SchemaValidatorBuilder` | `AvroReferenceAgreementTest` | pass | all modes | AvroCompatibilityChecker |
| JSON Schema rules | each README rule breaks/doesn't in the documented direction | `JsonSchemaComparatorTest` | pass | 29/29 | bugs 4, 5 |
| Malformed input | malformed schema / missing file → clear message, exit 2 | `CheckCommandTest.malformedSchema_exitsTwo`, manual run | pass | exit 2, one-line message | CheckCommand |
| Recursive schemas | no crash | `recursiveAvroSchema_doesNotCrash` | pass | exit 0 | bug 2 |
| Static analysis | Spotless + SpotBugs clean on JDK 21 | `mvn -B verify` | pass | 0 bugs | pom.xml |
| Dependencies | no known advisories in runtime deps | OSV batch query | pass | 0 / 10 packages | pom.xml |

## Classification proposal

**Partially Verified** — everything in the documented scope passes locally on
JDK 21/macOS and Avro verdicts agree with Avro's reference implementation, but
the JDK 25 and Windows CI legs have not run on this branch, and JSON Schema
support is intentionally partial (documented). Pending a green CI run on the
pushed branch, it would be a candidate for "Production-Ready for Its
Documented Scope".

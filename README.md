# schemaevo

A schema evolution compatibility checker for Avro and JSON Schema. It answers
one question in detail: **if I ship this schema change, which consumers break,
and why?**

## What

`schemaevo` compares a candidate schema against one or more prior versions and
reports, for each pair, whether the change is compatible under a chosen
guarantee (`BACKWARD`, `FORWARD`, `FULL`, or a `*_TRANSITIVE` variant checked
against the whole history). Every failure is explained in terms of a reader
and a writer — the schema resolving the data, and the schema that produced
it — plus a concrete suggestion for how to fix it. It ships as a single JAR
with a JSON output mode and a non-zero exit code, so it drops into CI as a
compatibility gate.

## Why

A schema registry will tell you a change is "incompatible." It usually will
not tell you which of your fifteen consumer teams that affects, or why the
specific field you touched is the problem, or what the minimal fix is. In an
event-driven system, an incompatible schema change is how you take production
down at 3am — a consumer silently fails to decode a valid message, or worse,
decodes it into different data than the producer intended. This tool exists to
turn "incompatible" into "consumer `orders-service` cannot read this because
field `priority` was added without a default — add one, or make the field
optional."

## Compatibility modes explained

Every mode reduces to one question, asked with different schemas playing the
roles of **reader** (the schema resolving/decoding a value) and **writer**
(the schema that produced it):

| Mode | Question | Typical use |
|---|---|---|
| `BACKWARD` | Can a consumer on the **new** schema (reader) read data written under the **old** schema (writer)? | Deploy new consumers before producers upgrade. |
| `FORWARD` | Can a consumer still on the **old** schema (reader) read data written under the **new** schema (writer)? | Deploy new producers before every consumer upgrades. |
| `FULL` | Both of the above. | Deploy producers and consumers in any order. |
| `BACKWARD_TRANSITIVE` | Is the candidate `BACKWARD` compatible with **every** prior version, not just the last one? | You cannot guarantee every consumer is on the latest schema. |
| `FORWARD_TRANSITIVE` | Is the candidate `FORWARD` compatible with **every** prior version? | Same, for producers. |
| `FULL_TRANSITIVE` | Both, against every prior version. | The strictest, safest default for a shared topic. |

Non-transitive modes only compare the candidate against its immediate
predecessor. Transitive modes matter because a schema can be compatible with
`v3` while still breaking a consumer stuck on `v1` — a real and common
situation when rollout is staggered.

For JSON Schema, the same reduction applies with "reader" replaced by
"validating schema" and "writer" by "the schema that produced the data":
`BACKWARD` asks whether every document the old schema could produce still
validates against the new schema; `FORWARD` asks the same with old and new
swapped.

## Rules reference

### Avro

| Rule | Backward-breaking? | Forward-breaking? | Notes |
|---|---|---|---|
| Add a field **with** a default | No | No | The default fills the gap for readers/writers missing it. |
| Add a field **without** a default | Yes | No | A reader on the new schema has nothing to put in that field for old data. |
| Remove a field **with** a default | No | No | Symmetric to adding one. |
| Remove a field **without** a default | No | Yes | An old reader (which still declares the field) has nothing to fill it with once the writer drops it. |
| Type promotion (`int`→`long`→`float`→`double`, `string`↔`bytes`) | No | No | Promotion is one-directional per pair; the reverse direction is a break. |
| Any other type change | Yes | Yes | No safe resolution exists. |
| Enum: add a symbol | No | Yes, unless the reader enum declares an **enum default** | A reader without the new symbol (and no default) cannot resolve it. |
| Enum: rename without an alias | Yes | Yes | Add the old name to `aliases` to rescue it. |
| Union: narrow (remove a branch a writer can still produce) | Yes | Yes | Each writer-producible type needs a resolvable reader branch. |
| Union: widen (add a branch) | No (reader side) | N/A | An unused extra reader branch is harmless. |
| Record/field/enum/fixed rename without alias | Yes | Yes | Add the old fully-qualified name (or field name) to `aliases`. |
| Record/field/enum/fixed rename **with** alias | No | No | The alias reconciles the old and new names during resolution. |
| Namespace change without alias | Yes | Yes | Namespace is part of the full name; same alias rule applies. |
| `fixed` size change | Yes | Yes | Sizes must match exactly; there is no promotion for `fixed`. |

### JSON Schema

| Rule | Backward-breaking? | Forward-breaking? | Notes |
|---|---|---|---|
| Add a `required` property | Yes | No | Old data may not have it; new data always will. |
| Remove a `required` property | No | No | Strictly loosens the schema. |
| Narrow `type` (e.g. drop an allowed type) | Yes | No | Old data of the dropped type would now fail validation. |
| Loosen `type` (e.g. add an allowed type) | No | Yes | New data of the added type would fail the old, narrower schema. |
| Narrow `enum` (remove a value) | Yes | No | Old data may have used the removed value. |
| Widen `enum` (add a value) | No | Yes | New data may use the new value, which the old schema rejects. |
| Add an `enum` over a previously unconstrained field | Yes | Yes | Neither side can assume the other's values fit the new set. |
| `additionalProperties: true/absent` → `false` | Yes, if the other side can produce extra properties | — | A closed schema now rejects fields the open schema allowed. |
| Tighten numeric bounds (`minimum` up, `maximum` down) | Yes | No | Values the looser schema allowed now fail. |
| Loosen numeric bounds | No | Yes | Symmetric. |
| Tighten string length (`minLength` up, `maxLength` down) | Yes | No | Same reasoning as numeric bounds. |

## Architecture

```
com.schemaevo.model        Value types: CompatibilityMode, Direction, Finding,
                            PairCheckResult, CompatibilityResult, Severity.
com.schemaevo.core          CompatibilityChecker interface, SchemaEvoException.
com.schemaevo.avro           AvroSchemaComparator: the actual reader/writer
                            resolution algorithm, walking records, enums,
                            unions, arrays, maps and fixed types recursively.
                            AvroCompatibilityChecker: wires the comparator to
                            CompatibilityMode semantics (which pairs to check,
                            in which direction).
com.schemaevo.jsonschema     JsonSchemaComparator: the validating/data-schema
                            comparison over type, required, enum,
                            additionalProperties, and numeric/length bounds.
                            JsonSchemaCompatibilityChecker: same wiring as
                            above for JSON Schema.
com.schemaevo.report         Human-readable and JSON report renderers.
com.schemaevo.cli             Picocli-based command line entry point.
```

`org.apache.avro:avro` is used to *parse* `.avsc` files into a `Schema` object
model; every compatibility rule and every explanation is implemented in this
project rather than reused from Avro's own (much terser) `SchemaCompatibility`
class. JSON Schema is parsed with Jackson into a plain `JsonNode` tree and
compared with a purpose-built, non-spec-complete comparator covering the
constraints listed above.

## Installation

Build the shaded JAR yourself (requires JDK 21):

```
mvn -B package
java -jar target/schemaevo.jar --help
```

## Usage

```
schemaevo check --format <avro|json-schema> --mode <MODE> [OPTIONS] FILE... CANDIDATE

Options:
  -f, --format <format>   avro or json-schema (required)
  -m, --mode <mode>       BACKWARD (default), FORWARD, FULL,
                          BACKWARD_TRANSITIVE, FORWARD_TRANSITIVE, FULL_TRANSITIVE
      --history <dir>     Check every file in a directory, ordered by the
                          trailing number in its filename, oldest to newest.
                          The last file is the candidate.
      --json              Emit a JSON report instead of the human report.
  -h, --help               Show help.
```

Positional usage takes one or more prior schema files followed by the
candidate schema (the last argument):

```
java -jar target/schemaevo.jar check \
  --format avro --mode BACKWARD \
  examples/avro/orders-breaking/v1.avsc examples/avro/orders-breaking/v2.avsc examples/avro/orders-breaking/v3.avsc
```

History mode checks an entire directory transitively:

```
java -jar target/schemaevo.jar check \
  --format avro --mode BACKWARD_TRANSITIVE \
  --history examples/avro/orders-compatible
```

Exit codes: `0` compatible, `1` incompatible, `2` usage or schema error (e.g.
malformed JSON/Avro, missing file).

## Examples

`examples/avro/orders-compatible/` is a three-version Avro history where every
change is safe: a field added with a default, an enum symbol added alongside
an enum default, and a nullable field added with a `null` default.

`examples/avro/orders-breaking/` adds a required `priority` field with no
default in `v3` — a canonical `BACKWARD`-breaking change. Running:

```
java -jar target/schemaevo.jar check --format avro --mode BACKWARD \
  examples/avro/orders-breaking/v2.avsc examples/avro/orders-breaking/v3.avsc
```

reports:

```
[BACKWARD] v3.avsc vs v2.avsc: INCOMPATIBLE
  - [ERROR] avro.record.field-missing-no-default at #/fields/priority
      The new schema (as a reader) has field 'priority' with no default, but the old schema (as the writer) has no matching field (by name or alias), so there is no value to populate it with.
      fix: add a default value to field 'priority', or add a field alias if this is a rename
```

`examples/jsonschema/user-compatible/` and `examples/jsonschema/user-breaking/`
mirror the same idea for JSON Schema: the breaking example promotes an
optional `role` property to `required` in `v3`.

## Testing

```
mvn -B verify
```

runs the full JUnit 5 suite (100+ tests covering every rule above in both the
breaking and non-breaking direction, transitive checks across three versions,
and malformed-schema handling), Spotless formatting checks, and SpotBugs
static analysis.

## Security

`schemaevo` only reads local files you point it at and never makes network
calls. Schema parsing errors are caught and reported as a clear message
(exit code `2`), not a raw stack trace. If you find a security issue, please
open a private report rather than a public issue.

## License

MIT. See [LICENSE](LICENSE).

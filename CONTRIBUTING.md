# Contributing

Thanks for considering a contribution to schemaevo.

## Development setup

You need JDK 21 and Maven 3.9+. From the repository root:

```
mvn -B verify
```

This compiles the code, runs the test suite, checks formatting with Spotless,
and runs static analysis with SpotBugs. All four must pass before a change is
merged.

## Formatting

Code is formatted with [google-java-format](https://github.com/google/google-java-format)
via the Spotless Maven plugin. Run:

```
mvn spotless:apply
```

before committing to auto-format your changes. `mvn verify` fails the build if
formatting is off.

## Adding a compatibility rule

Each rule lives in either `com.schemaevo.avro.AvroSchemaComparator` or
`com.schemaevo.jsonschema.JsonSchemaComparator`, with a stable rule ID declared
in the matching `*RuleIds` class. When you add a rule:

1. Add the rule ID constant.
2. Implement the check, emitting a `Finding` that names the concrete
   reader/writer (or validating/data) roles in its message and offers a
   `suggestion` when a fix exists.
3. Add at least one test for the breaking case and one for the adjacent
   non-breaking case (e.g. "field added without a default" next to "field
   added with a default").
4. If the rule matters for schema history, add or update an example under
   `examples/`.

## Reporting bugs

Please include the two schema versions involved, the compatibility mode you
ran, and the actual vs. expected output.

## License

By contributing, you agree your contributions are licensed under the MIT
License that covers this project.

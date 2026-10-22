package com.schemaevo.avro;

import static org.assertj.core.api.Assertions.assertThat;

import com.schemaevo.model.CompatibilityMode;
import com.schemaevo.model.CompatibilityResult;
import com.schemaevo.model.Direction;
import com.schemaevo.model.Finding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.apache.avro.SchemaValidationException;
import org.apache.avro.SchemaValidator;
import org.apache.avro.SchemaValidatorBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Differential tests: every verdict this project produces must agree with Apache Avro's own
 * reference implementation ({@link SchemaCompatibility} for a single reader/writer pair, and {@link
 * SchemaValidatorBuilder} for BACKWARD / FORWARD / FULL and their transitive variants).
 *
 * <p>The explanations are this project's own; only the compatible/incompatible verdict is compared.
 */
class AvroReferenceAgreementTest {

  private final AvroSchemaComparator comparator = new AvroSchemaComparator();

  private static String rec(String name, String fields) {
    return "{\"type\":\"record\",\"name\":\"" + name + "\",\"fields\":[" + fields + "]}";
  }

  private static String field(String name, String type) {
    return "{\"name\":\"" + name + "\",\"type\":" + type + "}";
  }

  private static String fieldWithDefault(String name, String type, String dflt) {
    return "{\"name\":\"" + name + "\",\"type\":" + type + ",\"default\":" + dflt + "}";
  }

  /** (case name, reader schema, writer schema). */
  static Stream<Arguments> pairs() {
    List<Arguments> cases = new ArrayList<>();
    String[] primitives = {
      "\"null\"",
      "\"boolean\"",
      "\"int\"",
      "\"long\"",
      "\"float\"",
      "\"double\"",
      "\"bytes\"",
      "\"string\""
    };
    // Every primitive reader/writer combination: promotions and plain mismatches.
    for (String r : primitives) {
      for (String w : primitives) {
        cases.add(Arguments.of("primitive " + w + " -> " + r, r, w));
        cases.add(
            Arguments.of(
                "field " + w + " -> " + r, rec("R", field("f", r)), rec("R", field("f", w))));
      }
    }
    String a = field("a", "\"string\"");
    cases.add(Arguments.of("identical record", rec("R", a), rec("R", a)));
    cases.add(
        Arguments.of(
            "reader adds field without default",
            rec("R", a + "," + field("b", "\"int\"")),
            rec("R", a)));
    cases.add(
        Arguments.of(
            "reader adds field with default",
            rec("R", a + "," + fieldWithDefault("b", "\"int\"", "0")),
            rec("R", a)));
    cases.add(
        Arguments.of(
            "writer has extra field", rec("R", a), rec("R", a + "," + field("b", "\"int\""))));
    cases.add(
        Arguments.of(
            "field rename with alias",
            rec("R", "{\"name\":\"b\",\"type\":\"string\",\"aliases\":[\"a\"]}"),
            rec("R", a)));
    cases.add(
        Arguments.of(
            "field rename without alias", rec("R", field("b", "\"string\"")), rec("R", a)));
    cases.add(Arguments.of("record rename without alias", rec("New", a), rec("Old", a)));
    cases.add(
        Arguments.of(
            "record rename with alias",
            "{\"type\":\"record\",\"name\":\"New\",\"aliases\":[\"Old\"],\"fields\":[" + a + "]}",
            rec("Old", a)));
    cases.add(
        Arguments.of(
            "namespace-only change",
            "{\"type\":\"record\",\"name\":\"R\",\"namespace\":\"n.new\",\"fields\":[" + a + "]}",
            "{\"type\":\"record\",\"name\":\"R\",\"namespace\":\"n.old\",\"fields\":[" + a + "]}"));
    cases.add(
        Arguments.of(
            "nested record namespace-only change",
            rec(
                "Outer",
                field(
                    "in",
                    "{\"type\":\"record\",\"name\":\"In\",\"namespace\":\"x\",\"fields\":["
                        + a
                        + "]}")),
            rec(
                "Outer",
                field(
                    "in",
                    "{\"type\":\"record\",\"name\":\"In\",\"namespace\":\"y\",\"fields\":["
                        + a
                        + "]}"))));
    String e1 = "{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"A\",\"B\"]}";
    String e2 = "{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"A\",\"B\",\"C\"]}";
    String e2d = "{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"A\",\"B\"],\"default\":\"A\"}";
    String eRenamed = "{\"type\":\"enum\",\"name\":\"F\",\"symbols\":[\"A\",\"B\"]}";
    String eNs = "{\"type\":\"enum\",\"name\":\"E\",\"namespace\":\"z\",\"symbols\":[\"A\",\"B\"]}";
    cases.add(Arguments.of("enum reader lacks writer symbol", e1, e2));
    cases.add(Arguments.of("enum reader has extra symbol", e2, e1));
    cases.add(Arguments.of("enum reader default rescues unknown symbol", e2d, e2));
    cases.add(Arguments.of("enum rename", eRenamed, e1));
    cases.add(Arguments.of("enum namespace-only change", eNs, e1));
    String f4 = "{\"type\":\"fixed\",\"name\":\"F\",\"size\":4}";
    String f8 = "{\"type\":\"fixed\",\"name\":\"F\",\"size\":8}";
    String g4 = "{\"type\":\"fixed\",\"name\":\"G\",\"size\":4}";
    String f4ns = "{\"type\":\"fixed\",\"name\":\"F\",\"namespace\":\"q\",\"size\":4}";
    cases.add(Arguments.of("fixed same", f4, f4));
    cases.add(Arguments.of("fixed resize", f8, f4));
    cases.add(Arguments.of("fixed rename", g4, f4));
    cases.add(Arguments.of("fixed namespace-only change", f4ns, f4));
    cases.add(
        Arguments.of(
            "array promotion",
            "{\"type\":\"array\",\"items\":\"long\"}",
            "{\"type\":\"array\",\"items\":\"int\"}"));
    cases.add(
        Arguments.of(
            "array demotion",
            "{\"type\":\"array\",\"items\":\"int\"}",
            "{\"type\":\"array\",\"items\":\"long\"}"));
    cases.add(
        Arguments.of(
            "map promotion",
            "{\"type\":\"map\",\"values\":\"double\"}",
            "{\"type\":\"map\",\"values\":\"float\"}"));
    cases.add(
        Arguments.of(
            "map vs array",
            "{\"type\":\"map\",\"values\":\"int\"}",
            "{\"type\":\"array\",\"items\":\"int\"}"));
    cases.add(
        Arguments.of("union widened", "[\"null\",\"string\",\"int\"]", "[\"null\",\"string\"]"));
    cases.add(
        Arguments.of("union narrowed", "[\"null\",\"string\"]", "[\"null\",\"string\",\"int\"]"));
    cases.add(
        Arguments.of("union narrowed via promotion", "[\"null\",\"long\"]", "[\"null\",\"int\"]"));
    cases.add(Arguments.of("reader union, writer single", "[\"null\",\"string\"]", "\"string\""));
    cases.add(Arguments.of("reader union, writer single miss", "[\"null\",\"string\"]", "\"int\""));
    cases.add(Arguments.of("reader single, writer union", "\"string\"", "[\"null\",\"string\"]"));
    cases.add(
        Arguments.of(
            "reader single, writer union all promotable",
            "\"double\"",
            "[\"int\",\"long\",\"float\"]"));
    cases.add(
        Arguments.of("reader union of records", "[\"null\"," + rec("R", a) + "]", rec("R", a)));
    cases.add(
        Arguments.of(
            "reader union of renamed record", "[\"null\"," + rec("S", a) + "]", rec("R", a)));
    String list = rec("L", field("v", "\"int\"") + "," + field("next", "[\"null\",\"L\"]"));
    String listLong = rec("L", field("v", "\"long\"") + "," + field("next", "[\"null\",\"L\"]"));
    String tree =
        rec(
            "T",
            field("v", "\"int\"") + "," + field("kids", "{\"type\":\"array\",\"items\":\"T\"}"));
    String treeLong =
        rec(
            "T",
            field("v", "\"long\"") + "," + field("kids", "{\"type\":\"array\",\"items\":\"T\"}"));
    String treeMap =
        rec(
            "T",
            field("v", "\"int\"") + "," + field("kids", "{\"type\":\"map\",\"values\":\"T\"}"));
    String treeExtra =
        rec(
            "T",
            field("v", "\"int\"")
                + ","
                + field("kids", "{\"type\":\"array\",\"items\":\"T\"}")
                + ","
                + field("w", "\"int\""));
    cases.add(Arguments.of("recursive list identical", list, list));
    cases.add(Arguments.of("recursive list promotion", listLong, list));
    cases.add(Arguments.of("recursive list demotion", list, listLong));
    cases.add(Arguments.of("recursive tree via array identical", tree, tree));
    cases.add(Arguments.of("recursive tree via array promotion", treeLong, tree));
    cases.add(Arguments.of("recursive tree via array demotion", tree, treeLong));
    cases.add(Arguments.of("recursive tree via map identical", treeMap, treeMap));
    cases.add(Arguments.of("recursive tree reader adds field", treeExtra, tree));
    cases.add(Arguments.of("recursive tree writer adds field", tree, treeExtra));
    return cases.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("pairs")
  void readerWriterVerdictMatchesAvro(String name, String readerJson, String writerJson) {
    Schema reader = new Schema.Parser().parse(readerJson);
    Schema writer = new Schema.Parser().parse(writerJson);

    boolean avroCompatible =
        SchemaCompatibility.checkReaderWriterCompatibility(reader, writer).getType()
            == SchemaCompatibilityType.COMPATIBLE;
    boolean ours =
        comparator.compare(reader, writer, Direction.BACKWARD).stream()
            .noneMatch(Finding::isBlocking);

    assertThat(ours)
        .as("verdict for '%s' (Avro says %s)", name, avroCompatible)
        .isEqualTo(avroCompatible);
  }

  /** Every bundled example history, in every mode, against Avro's own SchemaValidator. */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"orders-compatible", "orders-breaking"})
  void bundledExamplesMatchAvroValidatorInEveryMode(String example) throws IOException {
    List<String> versions = new ArrayList<>();
    for (int i = 1; i <= 3; i++) {
      versions.add(
          Files.readString(
              Path.of("examples", "avro", example, "v" + i + ".avsc"), StandardCharsets.UTF_8));
    }
    for (CompatibilityMode mode : CompatibilityMode.values()) {
      for (int candidateIndex = 1; candidateIndex < versions.size(); candidateIndex++) {
        assertModeAgrees(mode, versions.subList(0, candidateIndex), versions.get(candidateIndex));
      }
    }
  }

  @ParameterizedTest
  @EnumSource(CompatibilityMode.class)
  void handMadeHistoriesMatchAvroValidator(CompatibilityMode mode) {
    String a = field("a", "\"string\"");
    String v1 = rec("R", a);
    String v2 = rec("R", a + "," + fieldWithDefault("b", "\"int\"", "0"));
    String v3NoDefault = rec("R", a + "," + field("b", "\"int\""));
    String v3Removed = rec("R", fieldWithDefault("b", "\"int\"", "0"));
    String v3Promoted = rec("R", a + "," + fieldWithDefault("b", "\"long\"", "0"));
    String v3Required = rec("R", a + "," + field("c", "\"int\""));
    for (String candidate : List.of(v3NoDefault, v3Removed, v3Promoted, v3Required, v2, v1)) {
      assertModeAgrees(mode, List.of(v1, v2), candidate);
      assertModeAgrees(mode, List.of(v1), candidate);
    }
  }

  private void assertModeAgrees(CompatibilityMode mode, List<String> priors, String candidate) {
    List<String> labels = new ArrayList<>();
    for (int i = 0; i < priors.size(); i++) {
      labels.add("v" + (i + 1));
    }
    CompatibilityResult ours =
        new AvroCompatibilityChecker().check(priors, labels, candidate, "candidate", mode);

    List<Schema> history = new ArrayList<>();
    // SchemaValidator expects the most recent schema first.
    for (int i = priors.size() - 1; i >= 0; i--) {
      history.add(new Schema.Parser().parse(priors.get(i)));
    }
    boolean avroCompatible = true;
    try {
      avroValidator(mode).validate(new Schema.Parser().parse(candidate), history);
    } catch (SchemaValidationException e) {
      avroCompatible = false;
    }
    assertThat(ours.isCompatible())
        .as("mode %s, candidate %s, history %s", mode, candidate, priors)
        .isEqualTo(avroCompatible);
  }

  private static SchemaValidator avroValidator(CompatibilityMode mode) {
    SchemaValidatorBuilder builder = new SchemaValidatorBuilder();
    builder =
        switch (mode) {
          case BACKWARD, BACKWARD_TRANSITIVE -> builder.canReadStrategy();
          case FORWARD, FORWARD_TRANSITIVE -> builder.canBeReadStrategy();
          case FULL, FULL_TRANSITIVE -> builder.mutualReadStrategy();
        };
    return mode.isTransitive() ? builder.validateAll() : builder.validateLatest();
  }
}

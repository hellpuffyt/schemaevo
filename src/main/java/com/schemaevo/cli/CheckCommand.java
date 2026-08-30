package com.schemaevo.cli;

import com.schemaevo.avro.AvroCompatibilityChecker;
import com.schemaevo.core.CompatibilityChecker;
import com.schemaevo.core.SchemaEvoException;
import com.schemaevo.jsonschema.JsonSchemaCompatibilityChecker;
import com.schemaevo.model.CompatibilityMode;
import com.schemaevo.model.CompatibilityResult;
import com.schemaevo.model.SchemaFormat;
import com.schemaevo.report.HumanReportRenderer;
import com.schemaevo.report.JsonReportRenderer;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Checks whether a candidate schema is compatible with one or more prior versions, and reports
 * every break in reader/writer terms.
 */
@Command(
    name = "schemaevo",
    mixinStandardHelpOptions = true,
    version = "schemaevo 1.0.0",
    description = "Checks Avro and JSON Schema evolution compatibility.")
public final class CheckCommand implements Callable<Integer> {

  private static final Pattern TRAILING_NUMBER = Pattern.compile("(\\d+)(?!.*\\d)");

  @Option(
      names = {"-f", "--format"},
      required = true,
      description = "Schema format: avro or json-schema")
  private String formatArg;

  @Option(
      names = {"-m", "--mode"},
      defaultValue = "BACKWARD",
      description =
          "Compatibility mode: BACKWARD, FORWARD, FULL, BACKWARD_TRANSITIVE, FORWARD_TRANSITIVE,"
              + " FULL_TRANSITIVE")
  private CompatibilityMode mode;

  @Option(
      names = {"--history"},
      description = "Directory of numbered schema version files, checked oldest to newest")
  private Path historyDir;

  @Option(
      names = {"--json"},
      description = "Emit a machine-readable JSON report instead of the human report")
  private boolean jsonOutput;

  @Parameters(
      arity = "0..*",
      description = "Schema files: one or more prior versions followed by the candidate schema")
  private List<Path> files = new ArrayList<>();

  @Override
  public Integer call() {
    PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
    PrintWriter err = new PrintWriter(System.err, true, StandardCharsets.UTF_8);
    try {
      SchemaFormat format = parseFormat(formatArg);
      List<Path> orderedFiles = resolveFiles();
      if (orderedFiles.size() < 2) {
        err.println(
            "error: at least two schema versions are required (prior version(s) and a candidate)");
        return 2;
      }

      List<String> priorLabels = new ArrayList<>();
      List<String> priorContents = new ArrayList<>();
      for (int i = 0; i < orderedFiles.size() - 1; i++) {
        priorLabels.add(labelOf(orderedFiles.get(i)));
        priorContents.add(readFile(orderedFiles.get(i)));
      }
      Path candidatePath = orderedFiles.get(orderedFiles.size() - 1);
      String candidateLabel = labelOf(candidatePath);
      String candidateContent = readFile(candidatePath);

      CompatibilityChecker checker = checkerFor(format);
      CompatibilityResult result =
          checker.check(priorContents, priorLabels, candidateContent, candidateLabel, mode);

      out.print(
          jsonOutput ? JsonReportRenderer.render(result) : HumanReportRenderer.render(result));
      out.flush();
      return result.isCompatible() ? 0 : 1;
    } catch (SchemaEvoException e) {
      err.println("error: " + e.getMessage());
      return 2;
    } catch (IOException e) {
      err.println("error: could not read schema file: " + e.getMessage());
      return 2;
    }
  }

  private List<Path> resolveFiles() throws IOException {
    if (historyDir != null) {
      if (!Files.isDirectory(historyDir)) {
        throw new SchemaEvoException("--history path is not a directory: " + historyDir);
      }
      try (Stream<Path> stream = Files.list(historyDir)) {
        List<Path> found =
            stream.filter(Files::isRegularFile).sorted(this::compareByTrailingNumber).toList();
        if (found.isEmpty()) {
          throw new SchemaEvoException("--history directory contains no files: " + historyDir);
        }
        return found;
      }
    }
    return files;
  }

  private int compareByTrailingNumber(Path a, Path b) {
    int numA = trailingNumberOf(a.getFileName().toString());
    int numB = trailingNumberOf(b.getFileName().toString());
    if (numA != numB) {
      return Integer.compare(numA, numB);
    }
    return a.getFileName().toString().compareTo(b.getFileName().toString());
  }

  private int trailingNumberOf(String name) {
    Matcher matcher = TRAILING_NUMBER.matcher(name);
    if (matcher.find()) {
      try {
        return Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
  }

  private String readFile(Path path) throws IOException {
    return Files.readString(path);
  }

  private String labelOf(Path path) {
    Path fileName = path.getFileName();
    return fileName != null ? fileName.toString() : path.toString();
  }

  private SchemaFormat parseFormat(String raw) {
    String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "avro" -> SchemaFormat.AVRO;
      case "json_schema", "jsonschema", "json" -> SchemaFormat.JSON_SCHEMA;
      default ->
          throw new SchemaEvoException(
              "Unknown format '" + raw + "', expected 'avro' or 'json-schema'");
    };
  }

  private CompatibilityChecker checkerFor(SchemaFormat format) {
    return switch (format) {
      case AVRO -> new AvroCompatibilityChecker();
      case JSON_SCHEMA -> new JsonSchemaCompatibilityChecker();
    };
  }
}

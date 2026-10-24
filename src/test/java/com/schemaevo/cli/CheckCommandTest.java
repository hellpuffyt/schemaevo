package com.schemaevo.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class CheckCommandTest {

  @TempDir Path tempDir;

  private static final String V1 =
      "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"a\",\"type\":\"string\"}]}";
  private static final String V2_COMPATIBLE =
      "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
          + "{\"name\":\"a\",\"type\":\"string\"},"
          + "{\"name\":\"b\",\"type\":\"string\",\"default\":\"x\"}]}";
  private static final String V2_BREAKING =
      "{\"type\":\"record\",\"name\":\"R\",\"fields\":["
          + "{\"name\":\"a\",\"type\":\"string\"},"
          + "{\"name\":\"b\",\"type\":\"string\"}]}";

  private Path write(String name, String content) throws IOException {
    Path path = tempDir.resolve(name);
    Files.writeString(path, content, StandardCharsets.UTF_8);
    return path;
  }

  private int run(String... args) {
    return new CommandLine(new CheckCommand()).execute(args);
  }

  @Test
  void compatibleChange_exitsZero() throws IOException {
    Path v1 = write("v1.avsc", V1);
    Path v2 = write("v2.avsc", V2_COMPATIBLE);

    int exitCode = run("--format", "avro", "--mode", "BACKWARD", v1.toString(), v2.toString());
    assertThat(exitCode).isEqualTo(0);
  }

  @Test
  void breakingChange_exitsOne() throws IOException {
    Path v1 = write("v1.avsc", V1);
    Path v2 = write("v2.avsc", V2_BREAKING);

    int exitCode = run("--format", "avro", "--mode", "BACKWARD", v1.toString(), v2.toString());
    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void malformedSchema_exitsTwo() throws IOException {
    Path v1 = write("v1.avsc", V1);
    Path bad = write("bad.avsc", "{ not json");

    int exitCode = run("--format", "avro", "--mode", "BACKWARD", v1.toString(), bad.toString());
    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void unknownFormat_exitsTwo() throws IOException {
    Path v1 = write("v1.avsc", V1);
    Path v2 = write("v2.avsc", V2_COMPATIBLE);

    int exitCode = run("--format", "yaml-nonsense", v1.toString(), v2.toString());
    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void tooFewFiles_exitsTwo() throws IOException {
    Path v1 = write("v1.avsc", V1);

    int exitCode = run("--format", "avro", v1.toString());
    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void historyDirectory_ordersFilesNumerically() throws IOException {
    Path dir = tempDir.resolve("history");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("schema-v2.avsc"), V2_COMPATIBLE, StandardCharsets.UTF_8);
    Files.writeString(dir.resolve("schema-v1.avsc"), V1, StandardCharsets.UTF_8);
    Files.writeString(dir.resolve("schema-v10.avsc"), V2_COMPATIBLE, StandardCharsets.UTF_8);

    int exitCode =
        run("--format", "avro", "--mode", "BACKWARD_TRANSITIVE", "--history", dir.toString());
    assertThat(exitCode).isEqualTo(0);
  }

  @Test
  void jsonOutput_isValidAndReflectsResult() throws IOException {
    Path v1 = write("v1.avsc", V1);
    Path v2 = write("v2.avsc", V2_BREAKING);

    PrintStream originalOut = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
      int exitCode =
          run("--format", "avro", "--mode", "BACKWARD", "--json", v1.toString(), v2.toString());
      assertThat(exitCode).isEqualTo(1);
    } finally {
      System.setOut(originalOut);
    }
    String output = captured.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("\"compatible\" : false");
  }

  private int runMain(String... args) {
    return Main.commandLine().execute(args);
  }

  // Regression: the README documents 'schemaevo check ...', but 'check' used to be parsed as a
  // schema file name, so every documented positional example failed with exit code 2.
  @Test
  void documentedCheckSubcommand_works() throws IOException {
    Path v1 = write("v1.avsc", V1);
    Path ok = write("v2.avsc", V2_COMPATIBLE);
    Path bad = write("v3.avsc", V2_BREAKING);

    assertThat(
            runMain(
                "check", "--format", "avro", "--mode", "BACKWARD", v1.toString(), ok.toString()))
        .isEqualTo(0);
    assertThat(runMain("check", "--format", "avro", v1.toString(), bad.toString())).isEqualTo(1);
    assertThat(runMain("--help")).isEqualTo(0);
    assertThat(runMain("check", "--help")).isEqualTo(0);
  }

  @Test
  void missingSubcommand_exitsTwo() throws IOException {
    Path v1 = write("v1.avsc", V1);
    Path v2 = write("v2.avsc", V2_COMPATIBLE);
    assertThat(runMain("--format", "avro", v1.toString(), v2.toString())).isEqualTo(2);
    assertThat(runMain()).isEqualTo(2);
  }

  // Regression: positional files passed together with --history used to be silently ignored.
  @Test
  void historyAndPositionalFilesTogether_exitTwo() throws IOException {
    Path dir = Files.createDirectory(tempDir.resolve("hist"));
    Files.writeString(dir.resolve("v1.avsc"), V1, StandardCharsets.UTF_8);
    Files.writeString(dir.resolve("v2.avsc"), V2_COMPATIBLE, StandardCharsets.UTF_8);
    Path extra = write("v3.avsc", V2_BREAKING);

    assertThat(runMain("check", "--format", "avro", "--history", dir.toString(), extra.toString()))
        .isEqualTo(2);
  }

  @Test
  void recursiveAvroSchema_doesNotCrash() throws IOException {
    String tree =
        "{\"type\":\"record\",\"name\":\"T\",\"fields\":[{\"name\":\"v\",\"type\":\"int\"},"
            + "{\"name\":\"kids\",\"type\":{\"type\":\"array\",\"items\":\"T\"}}]}";
    Path v1 = write("t1.avsc", tree);
    Path v2 = write("t2.avsc", tree);
    assertThat(runMain("check", "--format", "avro", "--mode", "FULL", v1.toString(), v2.toString()))
        .isEqualTo(0);
  }
}

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
}

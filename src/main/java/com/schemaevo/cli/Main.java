package com.schemaevo.cli;

import picocli.CommandLine;

/** Entry point for the schemaevo command line tool. */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    System.exit(commandLine().execute(args));
  }

  /**
   * Builds the command line. An unexpected internal failure exits with {@code 2} ("error"), never
   * with {@code 1}, which is reserved for "the schemas are incompatible".
   */
  static CommandLine commandLine() {
    CommandLine cli = new CommandLine(new SchemaevoCommand());
    cli.setExecutionExceptionHandler(
        (exception, commandLine, parseResult) -> {
          commandLine.getErr().println("error: internal failure: " + exception);
          return 2;
        });
    return cli;
  }
}

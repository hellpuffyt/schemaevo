package com.schemaevo.cli;

import picocli.CommandLine;

/** Entry point for the schemaevo command line tool. */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    int exitCode = new CommandLine(new CheckCommand()).execute(args);
    System.exit(exitCode);
  }
}

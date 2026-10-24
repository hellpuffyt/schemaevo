package com.schemaevo.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Top-level {@code schemaevo} command; the work is done by the {@code check} subcommand. */
@Command(
    name = "schemaevo",
    mixinStandardHelpOptions = true,
    version = "schemaevo 1.0.0",
    description = "Checks Avro and JSON Schema evolution compatibility.",
    subcommands = {CheckCommand.class})
public final class SchemaevoCommand implements Runnable {

  @CommandLine.Spec CommandLine.Model.CommandSpec spec;

  @Override
  public void run() {
    throw new CommandLine.ParameterException(
        spec.commandLine(), "missing subcommand: use 'schemaevo check ...' (see --help)");
  }
}

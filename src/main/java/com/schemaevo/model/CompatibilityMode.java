package com.schemaevo.model;

/**
 * The compatibility guarantee to check for a schema change.
 *
 * <p>The direction names are defined from the point of view of a <em>reader</em> and a
 * <em>writer</em>:
 *
 * <ul>
 *   <li>{@code BACKWARD} — a consumer using the <b>new</b> schema (reader) can read data produced
 *       by the <b>old</b> schema (writer). This is what lets you deploy new consumers before
 *       producers have migrated.
 *   <li>{@code FORWARD} — a consumer still on the <b>old</b> schema (reader) can read data produced
 *       by the <b>new</b> schema (writer). This is what lets you deploy new producers before every
 *       consumer has migrated.
 *   <li>{@code FULL} — both directions hold.
 * </ul>
 *
 * <p>The {@code *_TRANSITIVE} variants check the candidate schema against <em>every</em> prior
 * version in the history, not just the immediately preceding one. This matters because a schema can
 * be pairwise-compatible with its immediate predecessor while still breaking a consumer that is two
 * versions behind.
 */
public enum CompatibilityMode {
  BACKWARD(false, true, false),
  BACKWARD_TRANSITIVE(true, true, false),
  FORWARD(false, false, true),
  FORWARD_TRANSITIVE(true, false, true),
  FULL(false, true, true),
  FULL_TRANSITIVE(true, true, true);

  private final boolean transitive;
  private final boolean checksBackward;
  private final boolean checksForward;

  CompatibilityMode(boolean transitive, boolean checksBackward, boolean checksForward) {
    this.transitive = transitive;
    this.checksBackward = checksBackward;
    this.checksForward = checksForward;
  }

  public boolean isTransitive() {
    return transitive;
  }

  public boolean checksBackward() {
    return checksBackward;
  }

  public boolean checksForward() {
    return checksForward;
  }
}

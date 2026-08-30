package com.schemaevo.model;

/**
 * Which reader/writer pairing a {@link Finding} was produced under.
 *
 * <p>{@code BACKWARD} means "the new schema read as a reader, the old schema as a writer". {@code
 * FORWARD} means "the old schema read as a reader, the new schema as a writer". A finding always
 * carries the direction it was discovered under so the report can explain it in the correct
 * reader/writer terms.
 */
public enum Direction {
  BACKWARD,
  FORWARD
}

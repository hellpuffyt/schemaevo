package com.schemaevo.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.schemaevo.core.SchemaEvoException;
import com.schemaevo.model.CompatibilityResult;
import java.util.LinkedHashMap;
import java.util.Map;

/** Renders a {@link CompatibilityResult} as machine-readable JSON. */
public final class JsonReportRenderer {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private JsonReportRenderer() {}

  public static String render(CompatibilityResult result) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("format", result.format());
    root.put("mode", result.mode());
    root.put("candidate", result.candidateLabel());
    root.put("compatible", result.isCompatible());
    root.put("pairResults", result.pairResults());
    try {
      return MAPPER.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new SchemaEvoException("Failed to render JSON report: " + e.getMessage(), e);
    }
  }
}

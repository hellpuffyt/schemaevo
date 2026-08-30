package com.schemaevo.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.schemaevo.model.CompatibilityMode;
import org.junit.jupiter.api.Test;

class CompatibilityModeTest {

  @Test
  void backward_isNotTransitive_andChecksOnlyBackward() {
    assertThat(CompatibilityMode.BACKWARD.isTransitive()).isFalse();
    assertThat(CompatibilityMode.BACKWARD.checksBackward()).isTrue();
    assertThat(CompatibilityMode.BACKWARD.checksForward()).isFalse();
  }

  @Test
  void forward_isNotTransitive_andChecksOnlyForward() {
    assertThat(CompatibilityMode.FORWARD.isTransitive()).isFalse();
    assertThat(CompatibilityMode.FORWARD.checksBackward()).isFalse();
    assertThat(CompatibilityMode.FORWARD.checksForward()).isTrue();
  }

  @Test
  void full_checksBothDirections_notTransitive() {
    assertThat(CompatibilityMode.FULL.isTransitive()).isFalse();
    assertThat(CompatibilityMode.FULL.checksBackward()).isTrue();
    assertThat(CompatibilityMode.FULL.checksForward()).isTrue();
  }

  @Test
  void transitiveVariants_setTransitiveFlag() {
    assertThat(CompatibilityMode.BACKWARD_TRANSITIVE.isTransitive()).isTrue();
    assertThat(CompatibilityMode.FORWARD_TRANSITIVE.isTransitive()).isTrue();
    assertThat(CompatibilityMode.FULL_TRANSITIVE.isTransitive()).isTrue();
  }
}

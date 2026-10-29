package com.chanakanlabs.theparlor.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class UserMaintenanceOptionsTests {
  @Test
  void requiresConfirmationAndAnUnambiguousSelector() {
    assertThatThrownBy(
            () -> UserMaintenance.Options.from(Map.of("THEPARLOR_DELETE_MODE", "wipe-users")))
        .hasMessageContaining("CONFIRM=1");
    assertThatThrownBy(
            () ->
                UserMaintenance.Options.from(
                    Map.of(
                        "THEPARLOR_DELETE_MODE", "delete-user", "THEPARLOR_DELETE_CONFIRM", "1")))
        .hasMessageContaining("exactly one");
    assertThatThrownBy(
            () ->
                UserMaintenance.Options.from(
                    Map.of(
                        "THEPARLOR_DELETE_MODE",
                        "delete-user",
                        "THEPARLOR_DELETE_USERNAME",
                        "alice",
                        "THEPARLOR_DELETE_USER_ID",
                        "id",
                        "THEPARLOR_DELETE_CONFIRM",
                        "1")))
        .hasMessageContaining("exactly one");
    assertThatThrownBy(
            () ->
                UserMaintenance.Options.from(
                    Map.of(
                        "THEPARLOR_DELETE_MODE",
                        "wipe-users",
                        "THEPARLOR_DELETE_USERNAME",
                        "alice",
                        "THEPARLOR_DELETE_CONFIRM",
                        "1")))
        .hasMessageContaining("neither");
  }

  @Test
  void previewTakesPrecedenceOverConfirmation() {
    var options =
        UserMaintenance.Options.from(
            Map.of(
                "THEPARLOR_DELETE_MODE",
                "delete-user",
                "THEPARLOR_DELETE_USERNAME",
                "a user's name",
                "THEPARLOR_DELETE_CONFIRM",
                "1",
                "THEPARLOR_DELETE_DRY_RUN",
                "1"));
    assertThat(options.dryRun()).isTrue();
    assertThat(options.username()).isEqualTo("a user's name");
  }
}

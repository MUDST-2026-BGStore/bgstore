package com.chanakanlabs.theparlor.account.internal.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class AccountEncapsulationTests {
  @Test
  void preferencesCannotMutateManagedJsonIncludingNestedObjectsOrNulls() {
    var nested = new HashMap<String, Object>();
    nested.put("city", "Bangkok");
    nested.put("optional", null);
    var address = new HashMap<String, Object>();
    address.put("details", nested);
    address.put("lines", new ArrayList<>(List.of("First")));
    address.put("preferredLocale", "en-US");
    var account = Account.create("First", "Last", "123", address);
    nested.put("city", "Changed after creation");
    assertThat(((Map<?, ?>) account.getPreferences().get("details")).get("city"))
        .isEqualTo("Bangkok");
    var view = account.getPreferences();
    assertThatThrownBy(view::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> ((Map<?, ?>) view.get("details")).clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> ((List<?>) view.get("lines")).clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(((Map<?, ?>) view.get("details")).containsKey("optional")).isTrue();
    var patch = new HashMap<String, Object>();
    var choices = new ArrayList<>(List.of("First"));
    patch.put("lines", choices);
    account.updatePreferences(patch);
    choices.add("Changed after update");
    patch.put("preferredLocale", "th-TH");
    assertThat(account.getPreferences()).containsEntry("preferredLocale", "en-US");
    assertThat((List<?>) account.getPreferences().get("lines")).hasSize(1);
    assertThat(((Map<?, ?>) view.get("details")).get("city")).isEqualTo("Bangkok");
  }
}

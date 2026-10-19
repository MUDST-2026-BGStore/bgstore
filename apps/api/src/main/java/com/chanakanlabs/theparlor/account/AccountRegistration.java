package com.chanakanlabs.theparlor.account;

import com.chanakanlabs.theparlor.shared.JsonValues;
import java.util.Map;

public record AccountRegistration(
    String firstName, String lastName, String phoneNumber, Map<String, Object> preferences) {
  public AccountRegistration {
    preferences = JsonValues.immutableCopy(preferences);
  }
}

package com.chanakanlabs.theparlor.account.internal.domain;

import java.util.Set;

/** Account preference keys and values; independent of the catalog's required translation locale. */
public final class AccountPreferencePolicy {
  private AccountPreferencePolicy() {}

  /** Persisted JSON key; changing it requires migrating stored preferences and updating the API. */
  public static final String PREFERRED_LOCALE_KEY = "preferredLocale";

  /** Supported account locales; contract consistency is checked against PreferredLocale.yaml. */
  public static final Set<String> SUPPORTED_LOCALES = Set.of("th-TH", "en-US");
}

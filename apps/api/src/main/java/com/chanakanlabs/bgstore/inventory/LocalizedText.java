package com.chanakanlabs.bgstore.inventory;

import org.springframework.lang.Nullable;

/**
 * Catalogue text in the languages the store publishes.
 *
 * <p>Both members arrive normalised from {@link GameValidator}: trimmed, with a blank collapsed to
 * null. A title always carries English, which the contract requires; a description may carry
 * neither language.
 *
 * <p>The contract's {@code LocalizedTitle} states the fallback rule readers apply — the asked-for
 * language when it holds text, English otherwise. Nothing on this side resolves it: responses carry
 * both languages so the browser can render one and the edit form can fill in both. The one place
 * the server applies the rule is the list ordering, which has to happen in SQL to be paged.
 */
record LocalizedText(@Nullable String english, @Nullable String thai) {

  /** Neither language carries text, which only a description is allowed to be. */
  static final LocalizedText NONE = new LocalizedText(null, null);

  boolean isEmpty() {
    return english == null && thai == null;
  }
}

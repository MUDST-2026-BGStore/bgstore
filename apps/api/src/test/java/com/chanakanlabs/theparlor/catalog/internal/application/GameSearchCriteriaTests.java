package com.chanakanlabs.theparlor.catalog.internal.application;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.*;
import static org.assertj.core.api.Assertions.*;

import com.chanakanlabs.theparlor.catalog.internal.domain.GameDifficulty;
import java.util.*;
import org.junit.jupiter.api.Test;

class GameSearchCriteriaTests {
  @Test
  void equivalentFiltersKeepExistingCursorEncodingAndCanResumeTheSameSearch() {
    var categories = new ArrayList<>(List.of("Strategy", "Family", "Family"));
    var filters =
        new GameSearchCriteria("FUN", categories, List.of("Drafting"), GameDifficulty.EASY, 2);
    categories.clear();
    var equivalent =
        new GameSearchCriteria(
            "fun", List.of("Family", "Strategy"), List.of("Drafting"), GameDifficulty.EASY, 2);
    assertThat(filters).isEqualTo(equivalent);
    String encoded = CatalogCursor.gameFilters(filters);
    assertThat(encoded).isEqualTo("3:fun1:26:Family8:Strategy1:18:Drafting4:EASY1:2");
    String cursor = CatalogCursor.next("games", encoded, "position");
    assertThat(CatalogCursor.after(cursor, "games", CatalogCursor.gameFilters(equivalent)))
        .isEqualTo("position");
    var changed =
        new GameSearchCriteria(
            "fun", List.of("Family", "Strategy"), List.of("Drafting"), GameDifficulty.EASY, 3);
    assertThatThrownBy(
            () -> CatalogCursor.after(cursor, "games", CatalogCursor.gameFilters(changed)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void omittedFiltersAndLiteralWhitespaceRetainTheirSearchMeaning() {
    var omitted = new GameSearchCriteria(null, null, null, null, null);
    assertThat(omitted.categories()).isEmpty();
    assertThat(omitted.mechanics()).isEmpty();
    assertThat(CatalogCursor.gameFilters(omitted)).isEqualTo("-1:1:01:0-1:-1:");
    var whitespace = new GameSearchCriteria(" ", List.of(" "), null, null, 1);
    assertThat(whitespace.q()).isEqualTo(" ");
    assertThat(whitespace.categories()).containsExactly(" ");
  }

  @Test
  void searchLimitsCountUnicodeCodePointsAndSuppliedValuesBeforeDeduplication() {
    String query = "😀".repeat(MAX_SEARCH_TEXT_LENGTH);
    String key = "😀".repeat(MAX_TAXONOMY_KEY_LENGTH);
    assertThatCode(() -> new GameSearchCriteria(query, List.of(key), List.of(key), null, 1))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> new GameSearchCriteria(query + "x", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GameSearchCriteria(null, List.of(key + "x"), null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    var tooMany = Collections.nCopies(MAX_FILTER_VALUES + 1, "Family");
    assertThatThrownBy(() -> new GameSearchCriteria(null, tooMany, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GameSearchCriteria(null, null, tooMany, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsEmptySearchInvalidKeysAndNonpositivePlayers() {
    assertThatThrownBy(() -> new GameSearchCriteria("", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    for (String key : Arrays.asList(null, "")) {
      assertThatThrownBy(
              () -> new GameSearchCriteria(null, Collections.singletonList(key), null, null, null))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(
              () -> new GameSearchCriteria(null, null, Collections.singletonList(key), null, null))
          .isInstanceOf(IllegalArgumentException.class);
    }
    for (int players : List.of(0, -1)) {
      assertThatThrownBy(() -> new GameSearchCriteria(null, null, null, null, players))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}

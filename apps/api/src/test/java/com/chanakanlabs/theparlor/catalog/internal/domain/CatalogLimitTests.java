package com.chanakanlabs.theparlor.catalog.internal.domain;

import static com.chanakanlabs.theparlor.catalog.internal.CatalogLimits.*;
import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CatalogLimitTests {
  @Test
  void taxonomyEntitiesShareNonblankUnicodeKeyLimits() {
    String key = "😀".repeat(MAX_TAXONOMY_KEY_LENGTH);
    assertThat(GameCategory.create(key).getName()).isEqualTo(key);
    assertThat(GameMechanic.create(key).getName()).isEqualTo(key);
    for (String invalid : Arrays.asList(null, "", " ", key + "x")) {
      assertThatThrownBy(() -> GameCategory.create(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Taxonomy name must be nonblank and at most "
                  + MAX_TAXONOMY_KEY_LENGTH
                  + " characters");
      assertThatThrownBy(() -> GameMechanic.create(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Taxonomy name must be nonblank and at most "
                  + MAX_TAXONOMY_KEY_LENGTH
                  + " characters");
    }
  }

  @Test
  void categoryLimitAppliesToCreationAdditionAndReplacementWithoutPartialMutation() {
    var categories =
        IntStream.range(0, MAX_CATEGORIES)
            .mapToObj(i -> GameCategory.create("Category " + i))
            .toList();
    var game =
        Game.create(
            GameTests.details(),
            categories,
            List.of(),
            Map.of(REQUIRED_LOCALE, "Description"),
            true);
    game.addCategory(categories.getFirst());
    game.replaceCategories(categories);
    assertThat(game.getCategories()).containsExactlyInAnyOrderElementsOf(categories);
    var extra = GameCategory.create("Extra");
    assertThatThrownBy(() -> game.addCategory(extra))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("At most " + MAX_CATEGORIES + " categories are allowed");
    var tooMany = new ArrayList<>(categories);
    tooMany.add(extra);
    assertThatThrownBy(() -> game.replaceCategories(tooMany))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Game.create(
                    GameTests.details(),
                    tooMany,
                    List.of(),
                    Map.of(REQUIRED_LOCALE, "Description"),
                    true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(game.getCategories()).containsExactlyInAnyOrderElementsOf(categories);
  }

  @Test
  void mechanicLimitAppliesToCreationAdditionAndReplacementWithoutPartialMutation() {
    var mechanics =
        IntStream.range(0, MAX_MECHANICS)
            .mapToObj(i -> GameMechanic.create("Mechanic " + i))
            .toList();
    var game = GameTests.game();
    mechanics.forEach(game::addMechanic);
    game.addMechanic(mechanics.getFirst());
    game.replaceMechanics(mechanics);
    var extra = GameMechanic.create("Extra");
    assertThatThrownBy(() -> game.addMechanic(extra))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("At most " + MAX_MECHANICS + " mechanics are allowed");
    var tooMany = new ArrayList<>(mechanics);
    tooMany.add(extra);
    assertThatThrownBy(() -> game.replaceMechanics(tooMany))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Game.create(
                    GameTests.details(),
                    game.getCategories(),
                    tooMany,
                    Map.of(REQUIRED_LOCALE, "Description"),
                    true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(game.getMechanics()).containsExactlyInAnyOrderElementsOf(mechanics);
  }

  @Test
  void imageInputAndEntityAcceptTheSameUnicodeBoundaries() {
    var game = GameTests.game();
    String url = "x".repeat(MAX_IMAGE_URL_LENGTH);
    String alt = "😀".repeat(MAX_IMAGE_ALT_TEXT_LENGTH);
    assertThatCode(() -> new Game.ImageDetails(url, alt)).doesNotThrowAnyException();
    var image = game.addImage(url, alt);
    assertThatThrownBy(() -> new Game.ImageDetails(url + "x", alt))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> game.updateImage(image, url + "x", alt))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Game.ImageDetails(url, alt + "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> game.updateImage(image, url, alt + "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(image.getUrl()).isEqualTo(url);
    assertThat(image.getAltText()).isEqualTo(alt);
  }
}

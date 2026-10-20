package com.chanakanlabs.theparlor.catalog.internal.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GameTests {
  static Game.Details details() {
    return new Game.Details("Azul", null, GameDifficulty.EASY, 2, 4, 8, 30, 45, 5, null);
  }

  static Game game() {
    return Game.create(
        details(),
        List.of(GameCategory.create("Abstract")),
        List.of(),
        Map.of("en-US", "Place tiles"),
        true);
  }

  @Test
  void validatesCompleteDetailsBeforeMutation() {
    assertThatThrownBy(() -> new Game.Details(" ", null, null, 1, 2, null, 1, 2, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Game.Details("Game", null, null, 4, 2, null, 1, 2, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Game.Details("Game", null, null, 1, 2, null, 10, 2, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Game.Details("Game", null, null, 1, 2, -1, 1, 2, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Game.Details("Game", null, null, 1, 2, null, 1, 2, -1, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new Game.Details("x".repeat(201), null, null, 1, 2, null, 1, 2, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    var game = game();
    game.updateDetails(new Game.Details("Renamed", "Designer", null, 1, 5, null, 1, 90, 0, 0));
    game.deactivate();
    assertThat(game.getName()).isEqualTo("Renamed");
    assertThat(game.getMaxPlayers()).isEqualTo(5);
    assertThat(game.isActive()).isFalse();
    game.activate();
    assertThat(game.isActive()).isTrue();
  }

  @Test
  void controlsSharedTaxonomyAndProtectsTheRequiredCategory() {
    var game = game();
    var category = GameCategory.create("Family");
    game.addCategory(category);
    game.addCategory(category);
    assertThat(game.getCategories()).hasSize(2);
    game.removeCategory(category);
    assertThatThrownBy(() -> game.removeCategory(game.getCategories().iterator().next()))
        .isInstanceOf(IllegalArgumentException.class);
    var mechanic = GameMechanic.create("Drafting");
    game.addMechanic(mechanic);
    game.addMechanic(mechanic);
    assertThat(game.getMechanics()).containsExactly(mechanic);
    game.removeMechanic(mechanic);
    assertThat(game.getMechanics()).isEmpty();
    assertThatThrownBy(() -> game.getCategories().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> game.getMechanics().add(mechanic))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void validatesAndCanonicalizesDescriptionsWithoutLosingEnglish() {
    var game = game();
    game.putDescription("zh-hant-tw", "Tiles");
    assertThat(game.getDescriptions()).containsEntry("zh-Hant-TW", "Tiles");
    game.putDescription("zh-Hant-TW", "Updated");
    assertThat(game.getDescriptions()).containsEntry("zh-Hant-TW", "Updated");
    game.removeDescription("zh-Hant-TW");
    assertThat(game.getDescriptions()).containsOnlyKeys("en-US");
    assertThatThrownBy(() -> game.removeDescription("en-us"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> game.putDescription("en_US", "Text"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> game.putDescription("th-TH", "x".repeat(4001)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> game.getDescriptions().put("th-TH", "Text"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () ->
                Game.create(
                    details(), List.of(GameCategory.create("Family")), List.of(), Map.of(), true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ownsImagesAndKeepsOrderingStableAfterRemoval() {
    var game = game();
    var first = game.addImage("https://assets/one", null);
    var second = game.addImage("https://assets/two", "Two");
    assertThat(first.getGame()).isSameAs(game);
    game.updateImage(second, "https://assets/updated", null);
    game.removeImage(first);
    assertThat(first.getGame()).isNull();
    var third = game.addImage("https://assets/three", "Three");
    assertThat(game.getImages()).containsExactly(second, third);
    assertThat(second.getSortOrder()).isEqualTo(1);
    assertThat(third.getSortOrder()).isEqualTo(2);
    assertThatThrownBy(() -> game.updateImage(first, "https://assets/one", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> game().removeImage(second))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> game.getImages().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> game.addImage(" ", null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> game.updateImage(second, "changed", "x".repeat(201)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(second.getUrl()).isEqualTo("https://assets/updated");
  }

  @Test
  void taxonomyHashMembershipSurvivesGeneratedIdentity() {
    var category = GameCategory.create("Family");
    var categories = new HashSet<GameCategory>();
    categories.add(category);
    ReflectionTestUtils.setField(category, "id", 1L);
    var detached = GameCategory.create("Family");
    ReflectionTestUtils.setField(detached, "id", 1L);
    assertThat(categories.contains(detached)).isTrue();
    assertThat(GameCategory.create("Family")).isNotEqualTo(GameCategory.create("Family"));
    var mechanic = GameMechanic.create("Drafting");
    var mechanics = new HashSet<GameMechanic>();
    mechanics.add(mechanic);
    ReflectionTestUtils.setField(mechanic, "id", 2L);
    assertThat(mechanics.contains(mechanic)).isTrue();
  }
}

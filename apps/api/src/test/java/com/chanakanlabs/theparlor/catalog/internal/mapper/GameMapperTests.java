package com.chanakanlabs.theparlor.catalog.internal.mapper;

import static org.assertj.core.api.Assertions.*;

import com.chanakanlabs.theparlor.catalog.internal.domain.*;
import com.chanakanlabs.theparlor.contracts.model.Difficulty;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class GameMapperTests {
  private final GameMapper mapper = Mappers.getMapper(GameMapper.class);

  @Test
  void mapsOnlyHttpFieldsAndCopiesMutableCollections() {
    var game =
        Game.create(
            new Game.Details("Game", null, GameDifficulty.ADVANCED, 1, 4, null, 10, 20, null, 0),
            List.of(GameCategory.create("Family")),
            List.of(GameMechanic.create("Drafting")),
            Map.of("en-US", "Text"),
            true);
    game.addImage("https://assets/image", null);
    var dto = mapper.toContract(game);
    assertThat(dto.getName()).isEqualTo("Game");
    assertThat(dto.getDesigner()).isNull();
    assertThat(dto.getDifficulty()).isEqualTo(Difficulty.ADVANCED);
    assertThat(dto.getMinAge()).isNull();
    assertThat(dto.getTeachMinutes()).isNull();
    assertThat(dto.getSetupMinutes()).isZero();
    assertThat(dto.getCategories()).containsExactly("Family");
    assertThat(dto.getMechanics()).containsExactly("Drafting");
    assertThat(dto.getImages())
        .singleElement()
        .satisfies(
            image -> {
              assertThat(image.getUrl()).isEqualTo("https://assets/image");
              assertThat(image.getAltText()).isNull();
              assertThat(image.getSortOrder()).isZero();
            });
    dto.getDescriptions().clear();
    dto.getImages().clear();
    dto.getCategories().clear();
    assertThat(game.getDescriptions()).containsEntry("en-US", "Text");
    assertThat(game.getImages()).hasSize(1);
    assertThat(game.getCategories()).hasSize(1);
    assertThat(mapper.toContract(null)).isNull();
  }
}

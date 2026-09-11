package com.chanakanlabs.bgstore.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The entity's half of the mapping: what a command writes is what a read hands back. */
class GameEntityTest {

  private static final PlayGuide.Step DEAL =
      new PlayGuide.Step(
          new LocalizedText("Deal the cards", "แจกการ์ด"),
          new LocalizedText("Everyone takes a Defuse card.", null));

  private static final PlayGuide.Step END_TURN =
      new PlayGuide.Step(new LocalizedText("End your turn", null), LocalizedText.NONE);

  @Test
  void readsBackThePhotosAndGuideACommandWrote() {
    var guide =
        new PlayGuide(
            new LocalizedText("Be the last one standing.", "เอาตัวรอด"),
            new LocalizedText("2–5 (up to 10 with the Party Pack)", null),
            LocalizedText.NONE,
            List.of(DEAL, END_TURN));
    var game = new GameEntity(UUID.randomUUID());

    game.apply(command(List.of("https://cdn.example.com/box.jpg"), guide));

    var stored = game.toStoredGame();
    assertThat(stored.imageUrls()).containsExactly("https://cdn.example.com/box.jpg");
    assertThat(stored.guide()).isEqualTo(guide);
  }

  @Test
  void replacesTheStepsOnAnUpdateRatherThanAppendingToThem() {
    var game = new GameEntity(UUID.randomUUID());
    game.apply(command(List.of(), guideWith(DEAL, END_TURN)));

    game.apply(command(List.of(), guideWith(END_TURN)));

    assertThat(game.toStoredGame().guide().steps()).containsExactly(END_TURN);
  }

  @Test
  void clearsThePhotosAndGuideWhenAnUpdateCarriesNone() {
    var game = new GameEntity(UUID.randomUUID());
    game.apply(command(List.of("https://cdn.example.com/box.jpg"), guideWith(DEAL)));

    game.apply(command(List.of(), PlayGuide.EMPTY));

    var stored = game.toStoredGame();
    assertThat(stored.imageUrls()).isEmpty();
    assertThat(stored.guide()).isEqualTo(PlayGuide.EMPTY);
  }

  @Test
  void readsARowSavedBeforePhotosExistedAsHavingNone() {
    // A row written before the column was added holds null there, which
    // Flyway keeps the column nullable so rows from before the feature remain readable.
    var game = new GameEntity(UUID.randomUUID());
    game.apply(command(List.of(), PlayGuide.EMPTY));
    ReflectionTestUtils.setField(game, "imageUrls", null);

    assertThat(game.toStoredGame().imageUrls()).isEmpty();
  }

  private static PlayGuide guideWith(PlayGuide.Step... steps) {
    return new PlayGuide(
        LocalizedText.NONE, LocalizedText.NONE, LocalizedText.NONE, List.of(steps));
  }

  private static GameCommand command(List<String> imageUrls, PlayGuide guide) {
    return new GameCommand(
        new LocalizedText("Exploding Kittens", "เหมียวระเบิด"),
        LocalizedText.NONE,
        GameCategory.CARD,
        2,
        5,
        15,
        "Easy",
        List.of(),
        imageUrls,
        guide,
        GameLifecycle.ACTIVE,
        Map.of());
  }
}

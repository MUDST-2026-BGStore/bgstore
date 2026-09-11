package com.chanakanlabs.bgstore.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.chanakanlabs.bgstore.branches.Branch;
import com.chanakanlabs.bgstore.contract.model.BranchStock;
import com.chanakanlabs.bgstore.contract.model.CatalogueLocale;
import com.chanakanlabs.bgstore.contract.model.GameAvailability;
import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameLifecycle;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameResponsesTest {

  private static final Branch CENTRAL =
      new Branch(UUID.randomUUID(), "Central Rama II", null, null, null);
  private static final Branch BIG_C =
      new Branch(UUID.randomUUID(), "Big C Rama I", null, null, null);
  private static final Branch SUKHUMVIT =
      new Branch(UUID.randomUUID(), "Sukhumvit", null, null, null);
  private static final List<Branch> DIRECTORY = List.of(CENTRAL, BIG_C, SUKHUMVIT);

  private static final OffsetDateTime ADDED =
      OffsetDateTime.of(2025, 1, 12, 9, 0, 0, 0, ZoneOffset.UTC);

  @Test
  void listsEveryBranchOnTheDetailIncludingTheOnesHoldingNoCopies() {
    var detail =
        GameResponses.toDetail(
            game(GameLifecycle.ACTIVE),
            List.of(new BranchStockRow(CENTRAL.id(), 2, 1), new BranchStockRow(BIG_C.id(), 1, 1)),
            DIRECTORY);

    assertThat(detail.getStock())
        .extracting(
            BranchStock::getBranchName,
            BranchStock::getCopies,
            BranchStock::getAvailable,
            BranchStock::getInUse,
            BranchStock::getStatus)
        .containsExactly(
            tuple("Central Rama II", 2, 1, 1, GameAvailability.AVAILABLE),
            tuple("Big C Rama I", 1, 0, 1, GameAvailability.ALL_COPIES_OUT),
            tuple("Sukhumvit", 0, 0, 0, GameAvailability.NOT_STOCKED));
  }

  @Test
  void countsOnlyStockedBranchesTowardsTheHeaderTotals() {
    var detail =
        GameResponses.toDetail(
            game(GameLifecycle.ACTIVE),
            List.of(new BranchStockRow(CENTRAL.id(), 2, 1), new BranchStockRow(BIG_C.id(), 1, 1)),
            DIRECTORY);

    assertThat(detail.getTotalCopies()).isEqualTo(3);
    assertThat(detail.getBranchCount()).isEqualTo(2);
    assertThat(detail.getStatus()).isEqualTo(GameAvailability.AVAILABLE);
    assertThat(detail.getAddedAt()).isEqualTo(ADDED);
  }

  @Test
  void reportsARetiredGameAsRetiredAtEveryBranch() {
    var detail =
        GameResponses.toDetail(
            game(GameLifecycle.RETIRED),
            List.of(new BranchStockRow(CENTRAL.id(), 3, 0)),
            DIRECTORY);

    assertThat(detail.getStatus()).isEqualTo(GameAvailability.RETIRED);
    assertThat(detail.getStock())
        .extracting(BranchStock::getStatus)
        .containsOnly(GameAvailability.RETIRED);
  }

  @Test
  void publishesBothLanguagesSoTheBrowserCanChooseAndTheFormCanEditEither() {
    var detail =
        GameResponses.toDetail(
            game(GameLifecycle.ACTIVE), List.of(new BranchStockRow(CENTRAL.id(), 1, 0)), DIRECTORY);

    assertThat(detail.getTitle().getEn()).isEqualTo("Ticket to Ride");
    assertThat(detail.getTitle().getTh()).isEqualTo("ตั๋วรถไฟ");
    // An untranslated description travels as a null member rather than as an
    // English string standing in for the Thai one.
    assertThat(detail.getDescription().getEn()).isEqualTo("Build routes across the map.");
    assertThat(detail.getDescription().getTh()).isNull();
    assertThat(GameResponses.toSummary(summary(1, CENTRAL.id()), Map.of()).getTitle().getEn())
        .isEqualTo("Uno");
  }

  @Test
  void publishesNoDescriptionAtAllWhenNeitherLanguageCarriesOne() {
    var untranslated =
        new StoredGame(
            UUID.randomUUID(),
            new LocalizedText("Uno", null),
            LocalizedText.NONE,
            GameCategory.CARD,
            2,
            10,
            null,
            null,
            List.of(),
            List.of(),
            PlayGuide.EMPTY,
            GameLifecycle.ACTIVE,
            ADDED,
            null);

    var detail = GameResponses.toDetail(untranslated, List.of(), DIRECTORY);

    assertThat(detail.getDescription()).isNull();
    assertThat(detail.getTitle().getTh()).isNull();
  }

  @Test
  void publishesThePhotosAndTheGuideInTheOrderTheyWereWritten() {
    var detail = GameResponses.toDetail(game(GameLifecycle.ACTIVE), List.of(), DIRECTORY);

    assertThat(detail.getImageUrls())
        .containsExactly(
            "https://cdn.example.com/ttr-box.jpg", "https://cdn.example.com/ttr-board.jpg");
    var guide = detail.getGuide();
    assertThat(guide.getGoal().getTh()).isEqualTo("ครอบครองเส้นทางที่ยาวที่สุด");
    // A part nobody wrote travels as null, as an absent description does.
    assertThat(guide.getPlayers()).isNull();
    assertThat(guide.getEquipment().getTh()).isNull();
    assertThat(guide.getSteps())
        .extracting(step -> step.getTitle().getEn(), step -> step.getBody() == null)
        .containsExactly(tuple("Deal cards", false), tuple("Take a turn", true));
  }

  @Test
  void publishesAnEmptyGuideRatherThanNoneForAGameWithoutOne() {
    var bare =
        new StoredGame(
            UUID.randomUUID(),
            new LocalizedText("Uno", null),
            LocalizedText.NONE,
            GameCategory.CARD,
            2,
            10,
            null,
            null,
            List.of(),
            List.of(),
            PlayGuide.EMPTY,
            GameLifecycle.ACTIVE,
            ADDED,
            null);

    var detail = GameResponses.toDetail(bare, List.of(), DIRECTORY);

    assertThat(detail.getImageUrls()).isEmpty();
    assertThat(detail.getGuide().getSteps()).isEmpty();
    assertThat(detail.getGuide().getGoal()).isNull();
  }

  @Test
  void carriesThePlayTimeAndCoverOntoTheSummaryForTheCatalogueCards() {
    var summary = GameResponses.toSummary(summary(1, CENTRAL.id()), Map.of());

    assertThat(summary.getPlayTimeMinutes()).isEqualTo(15);
    assertThat(summary.getCoverImageUrl()).isEqualTo("https://cdn.example.com/uno.jpg");
  }

  @Test
  void namesTheBranchOnASummaryOnlyWhenTheFiguresComeFromExactlyOne() {
    var names = Map.of(CENTRAL.id(), CENTRAL.name(), BIG_C.id(), BIG_C.name());

    assertThat(GameResponses.toSummary(summary(1, CENTRAL.id()), names).getBranchName())
        .isEqualTo("Central Rama II");
    assertThat(GameResponses.toSummary(summary(2, CENTRAL.id()), names).getBranchName()).isNull();
    assertThat(GameResponses.toSummary(summary(0, null), names).getBranchName()).isNull();
  }

  @Test
  void reportsTheFilteredTotalsRatherThanThePageOnTheStatTiles() {
    var page = new GamePage(List.of(summary(1, CENTRAL.id())), 24L, 46L, 12L);
    var filter = new GameFilter(null, null, null, null, null, CatalogueLocale.EN, 0, 6);

    var response = GameResponses.toListResponse(page, filter, Map.of());

    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getStats().getTitles()).isEqualTo(24L);
    assertThat(response.getStats().getAvailableNow()).isEqualTo(46L);
    assertThat(response.getStats().getInUse()).isEqualTo(12L);
    assertThat(response.getPage().getTotalPages()).isEqualTo(4);
    assertThat(response.getPage().getNumber()).isZero();
    assertThat(response.getPage().getSize()).isEqualTo(6);
  }

  @Test
  void reportsNoPagesForAnEmptyResult() {
    var filter = new GameFilter(null, null, null, null, null, CatalogueLocale.EN, 0, 20);

    var response = GameResponses.toListResponse(GamePage.EMPTY, filter, Map.of());

    assertThat(response.getItems()).isEmpty();
    assertThat(response.getPage().getTotalPages()).isZero();
    assertThat(response.getStats().getTitles()).isZero();
  }

  private static StoredGame game(GameLifecycle lifecycle) {
    return new StoredGame(
        UUID.randomUUID(),
        new LocalizedText("Ticket to Ride", "ตั๋วรถไฟ"),
        new LocalizedText("Build routes across the map.", null),
        GameCategory.FAMILY,
        2,
        5,
        60,
        "Easy to teach",
        List.of("beginner friendly"),
        List.of("https://cdn.example.com/ttr-box.jpg", "https://cdn.example.com/ttr-board.jpg"),
        new PlayGuide(
            new LocalizedText("Claim the longest routes.", "ครอบครองเส้นทางที่ยาวที่สุด"),
            LocalizedText.NONE,
            new LocalizedText("A map, train cars and cards.", null),
            List.of(
                new PlayGuide.Step(
                    new LocalizedText("Deal cards", "แจกการ์ด"),
                    new LocalizedText("Each player takes four cards.", null)),
                new PlayGuide.Step(new LocalizedText("Take a turn", null), LocalizedText.NONE))),
        lifecycle,
        ADDED,
        null);
  }

  private static GameSummaryRow summary(int branchCount, UUID singleBranchId) {
    return new GameSummaryRow(
        UUID.randomUUID(),
        new LocalizedText("Uno", null),
        GameCategory.CARD,
        2,
        10,
        15,
        "https://cdn.example.com/uno.jpg",
        5,
        4,
        branchCount,
        singleBranchId,
        GameAvailability.AVAILABLE);
  }
}

package com.chanakanlabs.bgstore.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import org.springframework.lang.Nullable;

/** One row of the {@code game_guide_step} table, owned by {@link GameEntity}. */
@Embeddable
@SuppressWarnings("NullAway.Init")
class PlayGuideStepColumns {

  @Column(name = "title_en", nullable = false, length = 120)
  private String titleEn;

  @Column(name = "title_th", length = 120)
  private @Nullable String titleTh;

  @Column(name = "body_en", length = 600)
  private @Nullable String bodyEn;

  @Column(name = "body_th", length = 600)
  private @Nullable String bodyTh;

  protected PlayGuideStepColumns() {}

  PlayGuideStepColumns(PlayGuide.Step step) {
    titleEn = Objects.requireNonNull(step.title().english());
    titleTh = step.title().thai();
    bodyEn = step.body().english();
    bodyTh = step.body().thai();
  }

  PlayGuide.Step toStep() {
    return new PlayGuide.Step(
        new LocalizedText(titleEn, titleTh), new LocalizedText(bodyEn, bodyTh));
  }
}

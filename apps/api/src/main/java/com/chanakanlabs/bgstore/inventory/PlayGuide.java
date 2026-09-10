package com.chanakanlabs.bgstore.inventory;

import java.util.List;

/**
 * The how-to-play content the client catalogue shows beside a game: the goal, a note on player
 * count, the components in the box, and a short numbered walkthrough.
 *
 * <p>Every part is optional. Text members arrive normalised from {@link GameValidator}, the same
 * way a {@link LocalizedText} description does, so "absent" is always {@link LocalizedText#NONE}.
 */
record PlayGuide(
    LocalizedText goal, LocalizedText players, LocalizedText equipment, List<Step> steps) {

  /** A game nobody has written a guide for yet. */
  static final PlayGuide EMPTY =
      new PlayGuide(LocalizedText.NONE, LocalizedText.NONE, LocalizedText.NONE, List.of());

  /**
   * One numbered step.
   *
   * @param title always carries English, as a catalogue title does
   * @param body may carry neither language
   */
  record Step(LocalizedText title, LocalizedText body) {}
}

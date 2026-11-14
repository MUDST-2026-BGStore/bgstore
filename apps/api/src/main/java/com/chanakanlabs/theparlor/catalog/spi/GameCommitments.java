package com.chanakanlabs.theparlor.catalog.spi;

import java.util.UUID;

/**
 * Implemented by modules that commit a game to customers; catalog asks before taking a game
 * offline.
 */
public interface GameCommitments {
  boolean hasOpenCommitments(UUID gameId);
}

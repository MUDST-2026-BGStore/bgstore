package com.chanakanlabs.theparlor.catalog.internal.web;

import com.chanakanlabs.theparlor.catalog.internal.application.EditionsService;
import com.chanakanlabs.theparlor.contracts.api.EditionsApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.time.*;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EditionsController implements EditionsApi {
  private final EditionsService editions;

  public EditionsController(EditionsService editions) {
    this.editions = editions;
  }

  @Override
  public GameEdition createGameEdition(
      UUID gameId, CreateGameEditionRequest createGameEditionRequest) {
    return editions.create(gameId, createGameEditionRequest);
  }

  @Override
  public GameEdition getGameEdition(UUID gameId, UUID editionId) {
    return editions.get(gameId, editionId);
  }

  @Override
  public GameEditionPage listGameEditions(
      UUID gameId, Integer limit, @Nullable String cursor, @Nullable Boolean active) {
    return editions.list(gameId, limit, cursor, active);
  }

  @Override
  public GameEdition updateGameEdition(
      UUID gameId, UUID editionId, UpdateGameEditionRequest updateGameEditionRequest) {
    return editions.update(gameId, editionId, updateGameEditionRequest);
  }
}

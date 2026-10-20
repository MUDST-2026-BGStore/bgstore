package com.chanakanlabs.theparlor.catalog.internal.web;

import com.chanakanlabs.theparlor.catalog.internal.application.GamesService;
import com.chanakanlabs.theparlor.contracts.api.GamesApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.time.*;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class GamesController implements GamesApi {
  private final GamesService gamesService;

  public GamesController(GamesService gamesService) {
    this.gamesService = gamesService;
  }

  @Override
  public Game createGame(CreateGameRequest createGameRequest) {
    return gamesService.createGame(createGameRequest);
  }

  @Override
  public Game getGame(UUID gameId) {
    return gamesService
        .getGame(gameId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
  }

  @Override
  public GamePage listGames(
      Integer limit,
      @Nullable String cursor,
      @Nullable String q,
      @Nullable List<String> categories,
      @Nullable List<String> mechanics,
      @Nullable Difficulty difficulty,
      @Nullable Integer players) {
    return gamesService.listGames(limit, cursor, q, categories, mechanics, difficulty, players);
  }

  @Override
  public Game updateGame(UUID gameId, UpdateGameRequest updateGameRequest) {
    return gamesService.updateGame(gameId, updateGameRequest);
  }
}

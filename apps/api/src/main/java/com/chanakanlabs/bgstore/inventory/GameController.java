package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.api.GamesApi;
import com.chanakanlabs.bgstore.contract.model.GameAvailability;
import com.chanakanlabs.bgstore.contract.model.GameCategory;
import com.chanakanlabs.bgstore.contract.model.GameDetail;
import com.chanakanlabs.bgstore.contract.model.GameListResponse;
import com.chanakanlabs.bgstore.contract.model.GameRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class GameController implements GamesApi {

  private final GameService games;

  GameController(GameService games) {
    this.games = games;
  }

  @Override
  public ResponseEntity<GameListResponse> listGames(
      @Nullable UUID branchId,
      @Nullable GameCategory category,
      @Nullable GameAvailability status,
      @Nullable String search,
      Integer page,
      Integer size) {
    var filter = new GameFilter(branchId, category, status, search, page, size);

    return ResponseEntity.ok(games.list(filter));
  }

  @Override
  public ResponseEntity<GameDetail> getGame(UUID gameId) {
    return ResponseEntity.ok(games.get(gameId));
  }

  @Override
  public ResponseEntity<GameDetail> createGame(GameRequest gameRequest) {
    var created = games.create(gameRequest);

    return ResponseEntity.created(URI.create("/api/v1/games/" + created.getId())).body(created);
  }

  @Override
  public ResponseEntity<GameDetail> updateGame(UUID gameId, GameRequest gameRequest) {
    return ResponseEntity.ok(games.update(gameId, gameRequest));
  }

  @Override
  public ResponseEntity<Void> retireGame(UUID gameId) {
    games.retire(gameId);

    return ResponseEntity.noContent().build();
  }
}

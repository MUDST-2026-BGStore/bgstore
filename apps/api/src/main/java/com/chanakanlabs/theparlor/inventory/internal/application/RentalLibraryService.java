package com.chanakanlabs.theparlor.inventory.internal.application;

import com.chanakanlabs.theparlor.catalog.CatalogLookup;
import com.chanakanlabs.theparlor.inventory.RentalLibrary;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGame;
import com.chanakanlabs.theparlor.inventory.internal.domain.BranchGameId;
import com.chanakanlabs.theparlor.inventory.internal.domain.RentalCopy;
import com.chanakanlabs.theparlor.inventory.internal.persistence.BranchGameRepository;
import com.chanakanlabs.theparlor.inventory.internal.persistence.RentalCopyRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class RentalLibraryService implements RentalLibrary {
  private final CatalogLookup catalog;
  private final BranchGameRepository offerings;
  private final RentalCopyRepository copies;

  RentalLibraryService(
      CatalogLookup catalog, BranchGameRepository offerings, RentalCopyRepository copies) {
    this.catalog = catalog;
    this.offerings = offerings;
    this.copies = copies;
  }

  @Override
  public List<Shelf> shelves(UUID branchId, Collection<UUID> gameIds) {
    var ids = known(gameIds);
    return shelves(branchId, ids, copies.findByBranchIdAndGameIdIn(branchId, ids));
  }

  @Override
  public List<Shelf> allShelves(UUID branchId) {
    var offered =
        offerings.findByIdBranchId(branchId).stream().map(row -> row.getId().gameId()).toList();
    var active =
        catalog.activeGames(offered, null).stream()
            .map(game -> game.getId())
            .collect(Collectors.toCollection(TreeSet::new));
    return shelves(branchId, active, copies.findByBranchIdAndGameIdIn(branchId, active));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public List<Shelf> lockShelves(UUID branchId, Collection<UUID> gameIds) {
    var ids = known(gameIds);
    // Games in ID order, copies in ID order: every writer queues in the same sequence.
    var locked = new ArrayList<RentalCopy>();
    for (UUID gameId : ids) locked.addAll(copies.lockByBranchIdAndGameId(branchId, gameId));
    return shelves(branchId, ids, locked);
  }

  private TreeSet<UUID> known(Collection<UUID> gameIds) {
    var ids = new TreeSet<>(gameIds);
    if (catalog.activeGames(ids, null).size() != ids.size())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found");
    return ids;
  }

  private List<Shelf> shelves(UUID branchId, TreeSet<UUID> gameIds, List<RentalCopy> owned) {
    Map<UUID, BranchGame> offered =
        offerings
            .findAllById(gameIds.stream().map(id -> new BranchGameId(branchId, id)).toList())
            .stream()
            .collect(Collectors.toMap(row -> row.getId().gameId(), Function.identity()));
    Map<UUID, List<UUID>> usable =
        owned.stream()
            .filter(RentalCopy::isUsable)
            .sorted(java.util.Comparator.comparing(RentalCopy::getId))
            .collect(
                Collectors.groupingBy(
                    RentalCopy::getGameId,
                    Collectors.mapping(RentalCopy::getId, Collectors.toList())));
    return gameIds.stream()
        .map(
            gameId -> {
              var offering = offered.get(gameId);
              return offering == null
                  ? new Shelf(gameId, false, false, 0, List.of())
                  : new Shelf(
                      gameId,
                      true,
                      offering.isRentalEnabled(),
                      offering.getRentalHourlyRateMinor(),
                      usable.getOrDefault(gameId, List.of()));
            })
        .toList();
  }
}

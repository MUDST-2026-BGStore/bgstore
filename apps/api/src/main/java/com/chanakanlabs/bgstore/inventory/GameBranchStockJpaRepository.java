package com.chanakanlabs.bgstore.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** CRUD over {@link GameBranchStockEntity}; Spring Data supplies the implementation. */
interface GameBranchStockJpaRepository
    extends JpaRepository<GameBranchStockEntity, GameBranchStockId> {

  /** Derived query over the embedded key: {@code id.gameId}. */
  List<GameBranchStockEntity> findByIdGameId(UUID gameId);
}

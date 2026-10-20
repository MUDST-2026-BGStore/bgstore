package com.chanakanlabs.theparlor.catalog.internal.mapper;

import com.chanakanlabs.theparlor.catalog.internal.domain.*;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface GameMapper {
  com.chanakanlabs.theparlor.contracts.model.Game toContract(Game game);

  com.chanakanlabs.theparlor.contracts.model.Category toCategory(GameCategory category);

  com.chanakanlabs.theparlor.contracts.model.Mechanic toMechanic(GameMechanic mechanic);

  @org.mapstruct.Mapping(target = "editionKind", source = "kind")
  com.chanakanlabs.theparlor.contracts.model.GameEdition toEdition(GameEdition edition);

  default String categoryToString(GameCategory category) {
    return category.getName();
  }

  default String mechanicToString(GameMechanic mechanic) {
    return mechanic.getName();
  }

  com.chanakanlabs.theparlor.contracts.model.GameImage toContractImage(GameImage image);
}

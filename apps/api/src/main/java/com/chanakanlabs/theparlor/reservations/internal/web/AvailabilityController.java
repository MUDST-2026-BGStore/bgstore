package com.chanakanlabs.theparlor.reservations.internal.web;

import com.chanakanlabs.theparlor.contracts.api.AvailabilityApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import com.chanakanlabs.theparlor.reservations.internal.application.RentalAvailabilityService;
import java.time.*;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AvailabilityController implements AvailabilityApi {
  private final RentalAvailabilityService availability;

  public AvailabilityController(RentalAvailabilityService availability) {
    this.availability = availability;
  }

  @Override
  public BranchAvailability getBranchAvailability(
      UUID branchId,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      Integer partySize,
      @Nullable Set<UUID> gameId) {
    return availability.getBranchAvailability(branchId, startsAt, endsAt, partySize, gameId);
  }
}

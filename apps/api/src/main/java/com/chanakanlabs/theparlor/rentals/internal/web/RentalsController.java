package com.chanakanlabs.theparlor.rentals.internal.web;

import com.chanakanlabs.theparlor.contracts.api.RentalsApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.time.*;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RentalsController implements RentalsApi {

  @Override
  public CheckOutResult checkOutRental(UUID branchId, UUID rentalId) {
    // TODO(rentals-api): implement checkOutRental — POST
    // /api/v1/branches/{branchId}/rentals/{rentalId}/check-out — Staff-only game return, hourly
    // charge calculation and table release
    throw new UnsupportedOperationException(
        "POST /api/v1/branches/{branchId}/rentals/{rentalId}/check-out (checkOutRental) is not implemented yet");
  }

  @Override
  public Rental getRental(UUID branchId, UUID rentalId) {
    // TODO(rentals-api): implement getRental — GET /api/v1/branches/{branchId}/rentals/{rentalId} —
    // Read a rental and its locked hourly rates
    throw new UnsupportedOperationException(
        "GET /api/v1/branches/{branchId}/rentals/{rentalId} (getRental) is not implemented yet");
  }

  @Override
  public RentalPage listBranchRentals(
      UUID branchId, Integer limit, @Nullable String cursor, @Nullable RentalStatus status) {
    // TODO(rentals-api): implement listBranchRentals — GET /api/v1/branches/{branchId}/rentals —
    // List active and completed hourly rentals for staff
    throw new UnsupportedOperationException(
        "GET /api/v1/branches/{branchId}/rentals (listBranchRentals) is not implemented yet");
  }
}

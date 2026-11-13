package com.chanakanlabs.theparlor.rentals.internal.web;

import com.chanakanlabs.theparlor.contracts.api.RentalsApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import com.chanakanlabs.theparlor.rentals.internal.application.RentalsService;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RentalsController implements RentalsApi {
  private final RentalsService rentals;

  public RentalsController(RentalsService rentals) {
    this.rentals = rentals;
  }

  @Override
  public CheckOutResult checkOutRental(UUID branchId, UUID rentalId) {
    return rentals.checkOut(branchId, rentalId);
  }

  @Override
  public Rental getRental(UUID branchId, UUID rentalId) {
    return rentals.get(branchId, rentalId);
  }

  @Override
  public RentalPage listBranchRentals(
      UUID branchId, Integer limit, @Nullable String cursor, @Nullable RentalStatus status) {
    return rentals.list(branchId, limit, cursor, status);
  }
}

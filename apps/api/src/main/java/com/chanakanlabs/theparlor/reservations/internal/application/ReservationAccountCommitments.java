package com.chanakanlabs.theparlor.reservations.internal.application;

import com.chanakanlabs.theparlor.account.spi.AccountCommitments;
import com.chanakanlabs.theparlor.reservations.internal.persistence.ReservationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationAccountCommitments implements AccountCommitments {
  private final ReservationRepository reservations;

  public ReservationAccountCommitments(ReservationRepository reservations) {
    this.reservations = reservations;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasOpenCommitments(String subject) {
    return reservations.hasOpenForCustomer(subject);
  }
}

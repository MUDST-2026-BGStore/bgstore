package com.chanakanlabs.bgstore.reservations;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {

  public record PageResult<T>(List<T> items, int total, int page, int pageSize, int totalPages) {}

  private final JpaReservationRepository repository;

  public ReservationService(JpaReservationRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public PageResult<ReservationRecordData> listReservations(
      String clientSubject, @Nullable String status, int page, int pageSize) {
    int pageIndex = Math.max(0, page - 1);
    Page<ReservationEntity> resultPage =
        repository.findByClientAndStatus(
            clientSubject, status, PageRequest.of(pageIndex, pageSize));

    List<ReservationRecordData> items =
        resultPage.getContent().stream().map(ReservationEntity::toRecord).toList();

    int total = (int) resultPage.getTotalElements();
    int totalPages = Math.max(1, resultPage.getTotalPages());
    return new PageResult<>(items, total, page, pageSize, totalPages);
  }

  @Transactional(readOnly = true)
  public ReservationRecordData getReservation(String reservationId, String clientSubject) {
    return repository
        .findByIdAndClient(reservationId, clientSubject)
        .map(ReservationEntity::toRecord)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Reservation not found: " + reservationId));
  }

  @Transactional
  public ReservationRecordData cancelReservation(String reservationId, String clientSubject) {
    ReservationEntity entity =
        repository
            .findByIdAndClient(reservationId, clientSubject)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reservation not found: " + reservationId));

    if (!"Reserved".equals(entity.toRecord().status()) || !entity.toRecord().canCancel()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Reservation cannot be cancelled in status " + entity.toRecord().status());
    }

    entity.cancel();
    ReservationEntity saved = repository.save(entity);
    return saved.toRecord();
  }
}

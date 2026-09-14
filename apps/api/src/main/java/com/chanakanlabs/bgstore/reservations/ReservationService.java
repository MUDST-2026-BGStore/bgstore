package com.chanakanlabs.bgstore.reservations;

import com.chanakanlabs.bgstore.identity.AccessPolicy;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {

  public record PageResult<T>(List<T> items, int total, int page, int pageSize, int totalPages) {}

  private final JpaReservationRepository repository;
  private final ReservedSlots reservedSlots;
  private final AccessPolicy accessPolicy;

  public ReservationService(
      JpaReservationRepository repository, ReservedSlots reservedSlots, AccessPolicy accessPolicy) {
    this.repository = repository;
    this.reservedSlots = reservedSlots;
    this.accessPolicy = accessPolicy;
  }

  @Transactional(readOnly = true)
  public PageResult<ReservationRecordData> listReservations(
      @Nullable String status, int page, int pageSize) {
    String clientSubject = accessPolicy.requireClientOnly().subject();
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
  public ReservationRecordData getReservation(String reservationId) {
    String clientSubject = accessPolicy.requireClientOnly().subject();
    return repository
        .findByIdAndClient(reservationId, clientSubject)
        .map(ReservationEntity::toRecord)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Reservation not found: " + reservationId));
  }

  @Transactional
  public ReservationRecordData cancelReservation(String reservationId) {
    String clientSubject = accessPolicy.requireClientOnly().subject();
    ReservationEntity entity =
        repository
            .findByIdAndClient(reservationId, clientSubject)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reservation not found: " + reservationId));

    ReservationRecordData record = entity.toRecord();
    if (!"Reserved".equals(record.status()) || !record.canCancel()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Reservation cannot be cancelled in status " + record.status());
    }

    entity.cancel();
    ReservationEntity saved = repository.save(entity);
    reservedSlots.releaseFor(reservationId);
    return saved.toRecord();
  }
}

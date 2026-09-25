package com.chanakanlabs.bgstore.reservations;

import com.chanakanlabs.bgstore.branches.Branch;
import com.chanakanlabs.bgstore.branches.BranchDirectory;
import com.chanakanlabs.bgstore.contract.model.CreateReservationRequest;
import com.chanakanlabs.bgstore.contract.model.ReservationAvailabilityResponse;
import com.chanakanlabs.bgstore.contract.model.ReservationAvailabilityTable;
import com.chanakanlabs.bgstore.identity.AccessPolicy;
import com.chanakanlabs.bgstore.identity.AuthenticatedIdentity;
import com.chanakanlabs.bgstore.identity.CurrentIdentityProvider;
import com.chanakanlabs.bgstore.identity.IdentityAccountJpaRepository;
import com.chanakanlabs.bgstore.tables.TableRecordData;
import com.chanakanlabs.bgstore.tables.TableRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Real reservation operations shared by client self-service and staff booking. */
@Service
@Transactional
public class StaffReservationService {
  private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
  private final TableRepository tables;
  private final TableReservationJpaRepository tableReservations;
  private final JpaReservationRepository reservations;
  private final BranchDirectory branches;
  private final AccessPolicy accessPolicy;
  private final CurrentIdentityProvider identities;
  private final IdentityAccountJpaRepository accounts;

  public StaffReservationService(
      TableRepository tables,
      TableReservationJpaRepository tableReservations,
      JpaReservationRepository reservations,
      BranchDirectory branches,
      AccessPolicy accessPolicy,
      CurrentIdentityProvider identities,
      IdentityAccountJpaRepository accounts) {
    this.tables = tables;
    this.tableReservations = tableReservations;
    this.reservations = reservations;
    this.branches = branches;
    this.accessPolicy = accessPolicy;
    this.identities = identities;
    this.accounts = accounts;
  }

  @Transactional(readOnly = true)
  public ReservationAvailabilityResponse availability(
      String branchName, LocalDate date, String start, String end, int partySize) {
    Branch selectedBranch = branch(branchName);
    Interval interval = interval(selectedBranch, date, start, end, partySize);
    List<ReservationAvailabilityTable> result =
        tables.findAll(branchName, null, null, true).stream()
            .filter(table -> table.capacity() >= partySize)
            .filter(table -> selectedBranch.id().equals(table.branchId()))
            .map(
                table -> {
                  long tableId = Objects.requireNonNull(table.id(), "Table id must not be null");
                  return new ReservationAvailabilityTable(
                      tableId,
                      table.name(),
                      table.capacity(),
                      !tableReservations.existsOverlap(
                          tableId, interval.startsAt(), interval.endsAt()));
                })
            .toList();
    return new ReservationAvailabilityResponse(result);
  }

  public ReservationRecordData create(CreateReservationRequest request) {
    AuthenticatedIdentity identity = identities.currentIdentity();
    String branchName = required(request.getBranch(), "branch");
    LocalDate date = required(request.getDate(), "date");
    String startTime = required(request.getStartTime(), "startTime");
    String endTime = required(request.getEndTime(), "endTime");
    int partySize = required(request.getPartySize(), "partySize");
    long requestedTableId = required(request.getTableId(), "tableId");
    Branch selectedBranch = branch(branchName);
    Interval interval = interval(selectedBranch, date, startTime, endTime, partySize);
    TableRecordData table =
        tables.findById(requestedTableId).orElseThrow(() -> notFound("Table not found."));
    if (!selectedBranch.id().equals(table.branchId()) || table.capacity() < partySize)
      throw badRequest("The selected table does not fit this reservation.");
    long tableId = Objects.requireNonNull(table.id(), "Table id must not be null");
    if (tableReservations.existsOverlap(tableId, interval.startsAt(), interval.endsAt()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The selected table is no longer available.");
    String subject =
        identity.isClientOnly()
            ? identity.subject()
            : registeredClientSubject(request.getClientSubject());
    String customer =
        request.getCustomerName() == null || request.getCustomerName().isBlank()
            ? identity.isClientOnly()
                ? (identity.firstName() + " " + identity.lastName()).trim()
                : required(request.getCustomerName(), "customerName")
            : request.getCustomerName().trim();
    String phone = required(request.getPhoneNumber(), "phoneNumber");
    String id = UUID.randomUUID().toString();
    var reservation =
        new ReservationEntity(
            id,
            selectedBranch.id(),
            subject,
            "Table " + tableId,
            date.toString(),
            startTime + "–" + endTime,
            partySize,
            tableId,
            table.name(),
            table.capacity(),
            0,
            "Reserved",
            customer,
            phone,
            "-",
            "-",
            0,
            0,
            true,
            null,
            OffsetDateTime.now(BANGKOK));
    reservations.save(reservation);
    tableReservations.save(
        new TableReservationEntity(id, tableId, interval.startsAt(), interval.endsAt()));
    return reservation.toRecord();
  }

  private Branch branch(String name) {
    Branch branch =
        branches.findAll().stream()
            .filter(item -> item.name().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(() -> badRequest("Unknown branch."));
    if (!identities.currentIdentity().isClientOnly()) {
      accessPolicy.requireBranch(branch.id());
    }
    return branch;
  }

  private Interval interval(
      Branch branch, LocalDate date, String start, String end, int partySize) {
    if (partySize < 1 || partySize > 13) throw badRequest("Party size must be between 1 and 13.");
    LocalTime starts = parseTime(start), ends = parseTime(end);
    if (!starts.isBefore(ends)) throw badRequest("End time must be after start time.");
    if (branch.opensAt() != null
        && (starts.isBefore(branch.opensAt()) || ends.isAfter(branch.closesAt())))
      throw badRequest(
          "The reservation must fit branch opening hours ("
              + branch.opensAt()
              + "–"
              + branch.closesAt()
              + ").");
    LocalDate today = LocalDate.now(BANGKOK);
    if (date.isBefore(today.plusDays(1)) || date.isAfter(today.plusDays(60)))
      throw badRequest("Date must be within the next 60 days.");
    return new Interval(
        OffsetDateTime.of(date, starts, BANGKOK.getRules().getOffset(date.atTime(starts))),
        OffsetDateTime.of(date, ends, BANGKOK.getRules().getOffset(date.atTime(ends))));
  }

  private static LocalTime parseTime(String value) {
    try {
      return LocalTime.parse(value);
    } catch (DateTimeParseException e) {
      throw badRequest("Invalid time.");
    }
  }

  private static String required(@org.jspecify.annotations.Nullable String value, String field) {
    if (value == null || value.isBlank()) throw badRequest(field + " is required.");
    return value.trim();
  }

  private static <T> T required(@org.jspecify.annotations.Nullable T value, String field) {
    if (value == null) throw badRequest(field + " is required.");
    return value;
  }

  private @org.jspecify.annotations.Nullable String registeredClientSubject(
      @org.jspecify.annotations.Nullable String value) {
    String subject = value == null ? "" : value.trim();
    if (subject.isBlank()) return null;
    if (!accounts.existsBySubjectAndApplicationRole(subject, "CLIENT"))
      throw badRequest("The selected client account was not found.");
    return subject;
  }

  private static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }

  private record Interval(OffsetDateTime startsAt, OffsetDateTime endsAt) {}
}

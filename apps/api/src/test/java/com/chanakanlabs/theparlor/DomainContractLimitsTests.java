package com.chanakanlabs.theparlor;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanakanlabs.theparlor.account.internal.domain.AccountPreferencePolicy;
import com.chanakanlabs.theparlor.branches.internal.domain.*;
import com.chanakanlabs.theparlor.carts.internal.domain.*;
import com.chanakanlabs.theparlor.contracts.model.PreferredLocale;
import com.chanakanlabs.theparlor.inventory.internal.domain.*;
import com.chanakanlabs.theparlor.orders.internal.domain.*;
import com.chanakanlabs.theparlor.rentals.internal.domain.*;
import com.chanakanlabs.theparlor.reservations.internal.domain.*;
import com.chanakanlabs.theparlor.shared.EnumStorageLengths;
import com.chanakanlabs.theparlor.shared.WorkflowTextLimits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Checks independently maintained domain, storage and HTTP contracts for accidental drift. */
class DomainContractLimitsTests {
  @Test
  void orderAndReservationTextLimitsMatchEveryWriteContract() throws IOException {
    for (String schema :
        List.of(
            "CreateOrderRequest",
            "CreateStaffOrderRequest",
            "CreateReservationRequest",
            "StaffBookingRequest",
            "WalkInReservationRequest",
            "RescheduleReservationRequest")) {
      maxLength(schema, "notes", WorkflowTextLimits.MAX_NOTES_LENGTH);
    }
    maxLength("ReasonRequest", "reason", WorkflowTextLimits.MAX_CANCELLATION_REASON_LENGTH);
  }

  @Test
  void branchAndInventoryLimitsMatchTheirContracts() throws IOException {
    for (String schema : List.of("Branch", "CreateBranchRequest", "UpdateBranchRequest")) {
      maxLength(schema, "name", Branch.MAX_NAME_LENGTH);
      maxLength(schema, "address", Branch.MAX_ADDRESS_LENGTH);
      maxLength(schema, "phone", Branch.MAX_PHONE_LENGTH);
    }
    for (String schema : List.of("Table", "CreateTableRequest", "UpdateTableRequest")) {
      maxLength(schema, "label", BranchTable.MAX_LABEL_LENGTH);
    }
    for (String schema : List.of("StockAdjustment", "StockAdjustmentRequest")) {
      maxLength(schema, "reason", SaleStockAdjustment.MAX_REASON_LENGTH);
    }
  }

  @Test
  void supportedAccountLocalesMatchTheSourceContractAndGeneratedModel() throws IOException {
    var schema = read("PreferredLocale");
    assertThat(AccountPreferencePolicy.SUPPORTED_LOCALES)
        .containsExactlyInAnyOrderElementsOf((List<String>) schema.get("enum"));
    assertThat(Arrays.stream(PreferredLocale.values()).map(PreferredLocale::getValue).toList())
        .containsExactlyInAnyOrderElementsOf(AccountPreferencePolicy.SUPPORTED_LOCALES);
    assertThat(child(read("AccountPreferences"), "properties"))
        .containsKey(AccountPreferencePolicy.PREFERRED_LOCALE_KEY);
  }

  @Test
  void everyPersistedWorkflowEnumFitsItsStorageConvention() {
    for (Class<? extends Enum<?>> type :
        List.of(
            OrderStatus.class,
            ReservationStatus.class,
            CartStatus.class,
            RentalStatus.class,
            RentalCopyOperationalStatus.class,
            TableOperationalStatus.class)) {
      fits(type, EnumStorageLengths.STATUS);
    }
    for (Class<? extends Enum<?>> type : List.of(OrderSource.class, ReservationSource.class)) {
      fits(type, EnumStorageLengths.SOURCE);
    }
    for (Class<? extends Enum<?>> type :
        List.of(OrderFulfillmentMethod.class, CartFulfillmentMethod.class)) {
      fits(type, EnumStorageLengths.FULFILLMENT_METHOD);
    }
    fits(RentalChargeType.class, RentalCharge.CHARGE_TYPE_STORAGE_LENGTH);
  }

  private static void fits(Class<? extends Enum<?>> type, int length) {
    for (Enum<?> value : type.getEnumConstants()) {
      assertThat(value.name().length())
          .as("%s.%s storage width", type.getSimpleName(), value)
          .isLessThanOrEqualTo(length);
    }
  }

  private static void maxLength(String schema, String field, int expected) throws IOException {
    assertThat(child(child(read(schema), "properties"), field))
        .as("%s.%s", schema, field)
        .containsEntry("maxLength", expected);
  }

  private static Map<String, Object> read(String schema) throws IOException {
    try (var input =
        Files.newInputStream(Path.of("../../openapi/components/schemas/" + schema + ".yaml"))) {
      return new Yaml().load(input);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> child(Map<String, Object> value, String key) {
    assertThat(value.get(key)).as("Contract node %s", key).isInstanceOf(Map.class);
    return (Map<String, Object>) value.get(key);
  }
}

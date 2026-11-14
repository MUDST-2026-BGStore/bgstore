package com.chanakanlabs.theparlor.payments.internal.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.chanakanlabs.theparlor.payments.Payments.Kind;
import com.chanakanlabs.theparlor.payments.Payments.Method;
import com.chanakanlabs.theparlor.payments.Payments.Payable;
import com.chanakanlabs.theparlor.payments.Payments.Status;
import com.chanakanlabs.theparlor.payments.internal.domain.CardBrand;
import com.chanakanlabs.theparlor.payments.internal.domain.Payment;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway.Authorization;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway.GatewayDeclined;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway.GatewayFailure;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway.PromptPayCharge;
import com.chanakanlabs.theparlor.payments.internal.persistence.PaymentRepository;
import com.chanakanlabs.theparlor.shared.ProblemException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PaymentsServiceTests {
  final UUID orderId = UUID.randomUUID();
  final Payable order = Payable.order(orderId);
  final PaymentRepository payments = mock(PaymentRepository.class);
  final PaymentGateway gateway = mock(PaymentGateway.class);
  final Instant now = Instant.parse("2026-11-14T03:00:00Z");
  final PaymentsService service =
      new PaymentsService(payments, gateway, Clock.fixed(now, ZoneOffset.UTC));

  @BeforeEach
  void gateway() {
    when(gateway.name()).thenReturn("test");
    when(payments.save(any())).thenAnswer(call -> call.getArgument(0));
  }

  Payment stored(Payment payment) {
    when(payments.lockByOrderId(orderId)).thenReturn(Optional.of(payment));
    return payment;
  }

  @Test
  void authorizesThroughTheGatewayAndKeepsOnlyBrandAndLastFour() {
    when(gateway.authorize("tok", 5000, "order:" + orderId))
        .thenReturn(new Authorization("ref-1", CardBrand.VISA, "4242"));
    var summary = service.authorizeCard(order, 5000, "tok");
    assertThat(summary.method()).isEqualTo(Method.CARD);
    assertThat(summary.status()).isEqualTo(Status.AUTHORIZED);
    assertThat(summary.cardBrand()).isEqualTo("VISA");
    assertThat(summary.cardLast4()).isEqualTo("4242");
  }

  @Test
  void reportsDeclinesAndProviderFailuresWithoutStoringAPayment() {
    when(gateway.authorize(any(), anyLong(), any())).thenThrow(new GatewayDeclined("declined"));
    assertThatThrownBy(() -> service.authorizeCard(order, 5000, "tok"))
        .isInstanceOfSatisfying(
            ProblemException.class,
            problem -> {
              assertThat(problem.status()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
              assertThat(problem.code()).isEqualTo("PAYMENT_DECLINED");
            });
    reset(gateway);
    when(gateway.authorize(any(), anyLong(), any())).thenThrow(new GatewayFailure("down"));
    assertThatThrownBy(() -> service.authorizeCard(order, 5000, "tok"))
        .isInstanceOfSatisfying(
            ProblemException.class,
            problem -> assertThat(problem.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
    verify(payments, never()).save(any());
  }

  @Test
  void capturesOnceAndNeverVoidsCapturedMoney() {
    var payment =
        stored(Payment.authorizedCard(order, 5000, "test", "ref-1", CardBrand.VISA, "4242"));
    assertThat(service.capture(order).status()).isEqualTo(Status.CAPTURED);
    assertThat(service.capture(order).status()).isEqualTo(Status.CAPTURED);
    verify(gateway, times(1)).capture("ref-1", 5000);
    assertThatThrownBy(() -> service.cancel(order)).isInstanceOf(IllegalStateException.class);
    verify(gateway, never()).cancel(any());
    assertThat(payment.getStatus()).isEqualTo(Status.CAPTURED);
  }

  @Test
  void voidsAnAuthorizationOnceAndNeverCapturesItAfterwards() {
    stored(Payment.authorizedCard(order, 5000, "test", "ref-1", CardBrand.VISA, "4242"));
    assertThat(service.cancel(order).status()).isEqualTo(Status.VOIDED);
    assertThat(service.cancel(order).status()).isEqualTo(Status.VOIDED);
    verify(gateway, times(1)).cancel("ref-1");
    assertThatThrownBy(() -> service.capture(order)).isInstanceOf(IllegalStateException.class);
    verify(gateway, never()).capture(any(), anyLong());
  }

  @Test
  void counterSalesSettleWithoutEverCallingTheGateway() {
    assertThat(service.settleAtCounter(order, 900).status()).isEqualTo(Status.PENDING);
    stored(Payment.atCounter(order, 900));
    assertThat(service.capture(order).status()).isEqualTo(Status.CAPTURED);
    verify(gateway, never()).capture(any(), anyLong());
  }

  @Test
  void refusesToSendAReferenceToADifferentProvider() {
    stored(Payment.authorizedCard(order, 5000, "other", "ref-1", CardBrand.VISA, "4242"));
    assertThatThrownBy(() -> service.capture(order)).isInstanceOf(ProblemException.class);
    verify(gateway, never()).capture(any(), anyLong());
  }

  @Test
  void paysAReservationPreOrderUnderItsOwnReference() {
    var reservationId = UUID.randomUUID();
    var reservation = Payable.reservation(reservationId);
    when(gateway.authorize("tok", 1800, "reservation:" + reservationId))
        .thenReturn(new Authorization("ref-2", CardBrand.MASTERCARD, "4444"));
    assertThat(service.authorizeCard(reservation, 1800, "tok").status())
        .isEqualTo(Status.AUTHORIZED);
    var stored =
        Payment.authorizedCard(reservation, 1800, "test", "ref-2", CardBrand.MASTERCARD, "4444");
    assertThat(stored.getOrderId()).isNull();
    assertThat(stored.getReservationId()).isEqualTo(reservationId);
    when(payments.lockByReservationId(reservationId)).thenReturn(Optional.of(stored));
    when(payments.findByReservationIdIn(List.of(reservationId))).thenReturn(List.of(stored));
    assertThat(service.cancel(reservation).status()).isEqualTo(Status.VOIDED);
    verify(gateway).cancel("ref-2");
    verify(payments, never()).lockByOrderId(any());
    assertThat(service.of(Kind.RESERVATION, List.of(reservationId)))
        .containsOnlyKeys(reservationId);
  }

  Payment promptPay() {
    return Payment.promptPay(
        order, 5000, "test", "chrg_1", "data:image/png;base64,AA", now.plusSeconds(900));
  }

  @Test
  void startsAPromptPayChargeThatExpiresInFifteenMinutes() {
    var deadline = now.plus(Duration.ofMinutes(15));
    when(gateway.createPromptPay(5000, "order:" + orderId, deadline))
        .thenReturn(new PromptPayCharge("chrg_1", "data:image/png;base64,AA", deadline));
    var summary = service.startPromptPay(order, 5000);
    assertThat(summary.method()).isEqualTo(Method.PROMPTPAY);
    assertThat(summary.status()).isEqualTo(Status.PENDING);
    assertThat(summary.promptPayQr()).isEqualTo("data:image/png;base64,AA");
    assertThat(summary.expiresAt()).isEqualTo(deadline);
    assertThat(summary.cardBrand()).isNull();
  }

  @Test
  void keepsTheProvidersEarlierDeadline() {
    var earlier = now.plus(Duration.ofMinutes(10));
    when(gateway.createPromptPay(anyLong(), any(), any()))
        .thenReturn(new PromptPayCharge("chrg_1", "data:image/png;base64,AA", earlier));
    assertThat(service.startPromptPay(order, 5000).expiresAt()).isEqualTo(earlier);
  }

  @Test
  void reportsAPromptPayProviderFailureWithoutStoringAPayment() {
    when(gateway.createPromptPay(anyLong(), any(), any())).thenThrow(new GatewayFailure("down"));
    assertThatThrownBy(() -> service.startPromptPay(order, 5000))
        .isInstanceOfSatisfying(
            ProblemException.class,
            problem -> assertThat(problem.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
    verify(payments, never()).save(any());
  }

  @Test
  void anUnpaidPromptPayCannotBeCapturedOrConfirmed() {
    var payment = stored(promptPay());
    when(gateway.isPaid("chrg_1")).thenReturn(false);
    assertThatThrownBy(() -> service.capture(order))
        .isInstanceOfSatisfying(
            ProblemException.class,
            problem -> {
              assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.code()).isEqualTo("PAYMENT_NOT_RECEIVED");
            });
    assertThatThrownBy(() -> service.confirmReceived(order)).isInstanceOf(ProblemException.class);
    assertThat(payment.getStatus()).isEqualTo(Status.PENDING);
    verify(gateway, never()).capture(any(), anyLong());
  }

  @Test
  void aPaidPromptPayIsCapturedOnceAndHidesItsQr() {
    var payment = stored(promptPay());
    when(gateway.isPaid("chrg_1")).thenReturn(true);
    assertThat(service.refreshPromptPay(order)).isTrue();
    assertThat(payment.getStatus()).isEqualTo(Status.CAPTURED);
    var captured = service.capture(order);
    assertThat(captured.status()).isEqualTo(Status.CAPTURED);
    assertThat(captured.promptPayQr()).isNull();
    assertThat(captured.expiresAt()).isNull();
    assertThat(service.confirmReceived(order).status()).isEqualTo(Status.CAPTURED);
    verify(gateway, times(1)).isPaid("chrg_1");
    verify(gateway, never()).capture(any(), anyLong());
  }

  @Test
  void cancellingAPromptPayChecksTheProviderFirst() {
    var unpaid = stored(promptPay());
    when(gateway.isPaid("chrg_1")).thenReturn(false);
    assertThat(service.cancel(order).status()).isEqualTo(Status.VOIDED);
    assertThat(service.cancel(order).status()).isEqualTo(Status.VOIDED);
    assertThat(unpaid.getStatus()).isEqualTo(Status.VOIDED);

    var paid = stored(promptPay());
    when(gateway.isPaid("chrg_1")).thenReturn(true);
    assertThatThrownBy(() -> service.cancel(order))
        .isInstanceOfSatisfying(
            ProblemException.class,
            problem -> assertThat(problem.code()).isEqualTo("PAYMENT_ALREADY_RECEIVED"));
    assertThat(paid.getStatus()).isEqualTo(Status.CAPTURED);
  }

  @Test
  void cashOnDeliveryStaysPendingThroughDispatchUntilConfirmed() {
    assertThat(service.payOnDelivery(order, 7000).method()).isEqualTo(Method.COD);
    var payment = stored(Payment.cashOnDelivery(order, 7000));
    assertThat(service.capture(order).status()).isEqualTo(Status.PENDING);
    assertThat(service.confirmReceived(order).status()).isEqualTo(Status.CAPTURED);
    assertThat(service.confirmReceived(order).status()).isEqualTo(Status.CAPTURED);
    assertThatThrownBy(() -> service.cancel(order))
        .isInstanceOfSatisfying(
            ProblemException.class,
            problem -> assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT));
    assertThat(payment.getStatus()).isEqualTo(Status.CAPTURED);
    verifyNoMoreInteractionsWithMoney();
  }

  @Test
  void cancelledCashOnDeliveryCannotBeConfirmed() {
    stored(Payment.cashOnDelivery(order, 7000));
    assertThat(service.cancel(order).status()).isEqualTo(Status.VOIDED);
    assertThatThrownBy(() -> service.confirmReceived(order))
        .isInstanceOfSatisfying(
            ProblemException.class,
            problem -> assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void onlyPendingCashOrPromptPayCanBeConfirmed() {
    stored(Payment.authorizedCard(order, 5000, "test", "ref-1", CardBrand.VISA, "4242"));
    assertThatThrownBy(() -> service.confirmReceived(order)).isInstanceOf(ProblemException.class);
    stored(Payment.atCounter(order, 900));
    assertThatThrownBy(() -> service.confirmReceived(order)).isInstanceOf(ProblemException.class);
  }

  @Test
  void aProviderNoticeIsOnlyAHint() {
    var payment = promptPay();
    when(payments.lockByGatewayReference("test", "chrg_1")).thenReturn(Optional.of(payment));
    when(gateway.isPaid("chrg_1")).thenReturn(false);
    service.gatewayChargeChanged("chrg_1");
    assertThat(payment.getStatus()).isEqualTo(Status.PENDING);
    when(gateway.isPaid("chrg_1")).thenReturn(true);
    service.gatewayChargeChanged("chrg_1");
    assertThat(payment.getStatus()).isEqualTo(Status.CAPTURED);
  }

  @Test
  void ignoresNoticesForUnknownChargesAndCards() {
    when(payments.lockByGatewayReference(any(), any())).thenReturn(Optional.empty());
    service.gatewayChargeChanged("chrg_unknown");
    service.gatewayChargeChanged(null);
    service.gatewayChargeChanged(" ");
    var card = Payment.authorizedCard(order, 5000, "test", "chrg_2", CardBrand.VISA, "4242");
    when(payments.lockByGatewayReference("test", "chrg_2")).thenReturn(Optional.of(card));
    service.gatewayChargeChanged("chrg_2");
    assertThat(card.getStatus()).isEqualTo(Status.AUTHORIZED);
    verify(gateway, never()).isPaid(any());
  }

  @Test
  void aNoticeForAVoidedChargeChangesNothing() {
    var payment = promptPay();
    payment.cancel();
    when(payments.lockByGatewayReference("test", "chrg_1")).thenReturn(Optional.of(payment));
    when(gateway.isPaid("chrg_1")).thenReturn(true);
    service.gatewayChargeChanged("chrg_1");
    assertThat(payment.getStatus()).isEqualTo(Status.VOIDED);
  }

  @Test
  void listsExpiredUnpaidPayablesByKind() {
    var expired = List.of(orderId);
    when(payments.expiredUnpaidOrders(now)).thenReturn(expired);
    assertThat(service.expiredUnpaid(Kind.ORDER, now)).isEqualTo(expired);
    verify(payments, never()).expiredUnpaidReservations(any());
  }

  void verifyNoMoreInteractionsWithMoney() {
    verify(gateway, never()).capture(any(), anyLong());
    verify(gateway, never()).cancel(any());
    verify(gateway, never()).isPaid(any());
  }
}

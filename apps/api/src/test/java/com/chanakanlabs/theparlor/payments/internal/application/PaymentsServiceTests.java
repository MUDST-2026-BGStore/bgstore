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
import com.chanakanlabs.theparlor.payments.internal.persistence.PaymentRepository;
import com.chanakanlabs.theparlor.shared.ProblemException;
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
  final PaymentsService service = new PaymentsService(payments, gateway);

  @BeforeEach
  void gateway() {
    when(gateway.name()).thenReturn("test");
    when(payments.save(any())).thenAnswer(call -> call.getArgument(0));
  }

  Payment stored(Payment payment) {
    when(payments.findByOrderId(orderId)).thenReturn(Optional.of(payment));
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
    when(payments.findByReservationId(reservationId)).thenReturn(Optional.of(stored));
    when(payments.findByReservationIdIn(List.of(reservationId))).thenReturn(List.of(stored));
    assertThat(service.cancel(reservation).status()).isEqualTo(Status.VOIDED);
    verify(gateway).cancel("ref-2");
    verify(payments, never()).findByOrderId(any());
    assertThat(service.of(Kind.RESERVATION, List.of(reservationId)))
        .containsOnlyKeys(reservationId);
  }
}

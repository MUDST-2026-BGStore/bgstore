package com.chanakanlabs.theparlor.payments.internal.gateway.omise;

import static org.assertj.core.api.Assertions.*;

import com.chanakanlabs.theparlor.payments.internal.domain.CardBrand;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway.Card;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway.GatewayDeclined;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway.GatewayFailure;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the adapter against a local stand-in for Omise's API. Nothing reaches Omise. */
class OmiseGatewayTests {
  static final String SECRET = "skey_test_secret";

  record Seen(String method, String path, String authorization, String version, String body) {
    Map<String, String> form() {
      var form = new LinkedHashMap<String, String>();
      if (body == null || body.isEmpty()) return form;
      for (String pair : body.split("&")) {
        String[] parts = pair.split("=", 2);
        form.put(
            URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
            URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8));
      }
      return form;
    }
  }

  record Reply(int status, String contentType, byte[] body) {
    static Reply json(int status, String body) {
      return new Reply(status, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }
  }

  HttpServer server;
  String base;
  final Map<String, Reply> replies = new ConcurrentHashMap<>();
  final Map<String, String> redirects = new ConcurrentHashMap<>();
  final List<Seen> seen = new ArrayList<>();
  OmiseGateway gateway;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
    gateway =
        new OmiseGateway(
            new OmiseProperties(
                "pkey_test_public",
                SECRET,
                URI.create(base),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)));
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  void handle(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
    synchronized (seen) {
      seen.add(
          new Seen(
              exchange.getRequestMethod(),
              exchange.getRequestURI().getPath(),
              exchange.getRequestHeaders().getFirst("Authorization"),
              exchange.getRequestHeaders().getFirst("Omise-Version"),
              body));
    }
    var reply =
        replies.getOrDefault(
            key,
            Reply.json(
                404, "{\"object\":\"error\",\"code\":\"not_found\",\"message\":\"not found\"}"));
    exchange.getResponseHeaders().set("Content-Type", reply.contentType());
    if (redirects.containsKey(key))
      exchange.getResponseHeaders().set("Location", redirects.get(key));
    exchange.sendResponseHeaders(reply.status(), reply.body().length);
    exchange.getResponseBody().write(reply.body());
    exchange.close();
  }

  Seen last() {
    synchronized (seen) {
      return seen.getLast();
    }
  }

  static String basic() {
    return "Basic "
        + Base64.getEncoder().encodeToString((SECRET + ":").getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void refusesToStartWithoutKeys() {
    assertThatThrownBy(
            () ->
                new OmiseGateway(
                    new OmiseProperties(
                        "", "", URI.create(base), Duration.ofSeconds(1), Duration.ofSeconds(1))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void cardsAreTokenizedInTheBrowserNeverHere() {
    assertThat(gateway.browserTokenizationKey()).contains("pkey_test_public");
    assertThatThrownBy(
            () -> gateway.tokenize(new Card("4242424242424242", CardBrand.VISA, 12, 2030, "123")))
        .isInstanceOf(GatewayFailure.class)
        .hasMessageNotContaining("4242");
    assertThat(seen).isEmpty();
  }

  @Test
  void authorizesWithoutCapturingUsingTheSecretKeyAndPinnedVersion() {
    replies.put(
        "POST /charges",
        Reply.json(
            200,
            """
            {"object":"charge","id":"chrg_test_1","status":"pending","authorized":true,
             "paid":false,"card":{"brand":"MasterCard","last_digits":"4444"}}
            """));
    var authorization = gateway.authorize("tokn_test_1", 153000, "order:abc");
    assertThat(authorization.reference()).isEqualTo("chrg_test_1");
    assertThat(authorization.brand()).isEqualTo(CardBrand.MASTERCARD);
    assertThat(authorization.last4()).isEqualTo("4444");
    var request = last();
    assertThat(request.authorization()).isEqualTo(basic());
    assertThat(request.version()).isEqualTo(OmiseGateway.API_VERSION);
    assertThat(request.form())
        .containsEntry("amount", "153000")
        .containsEntry("currency", "THB")
        .containsEntry("card", "tokn_test_1")
        .containsEntry("capture", "false")
        .containsEntry("metadata[payable]", "order:abc");
  }

  @Test
  void aFailedChargeIsADecline() {
    replies.put(
        "POST /charges",
        Reply.json(
            200,
            """
            {"object":"charge","id":"chrg_test_2","status":"failed","authorized":false,
             "failure_code":"insufficient_fund","failure_message":"insufficient funds in the account"}
            """));
    assertThatThrownBy(() -> gateway.authorize("tokn_test_2", 5000, "order:abc"))
        .isInstanceOf(GatewayDeclined.class)
        .hasMessage("insufficient funds in the account");
  }

  @Test
  void tokenErrorsAreDeclinesAndOtherErrorsAreFailures() {
    replies.put(
        "POST /charges",
        Reply.json(
            400,
            "{\"object\":\"error\",\"code\":\"used_token\",\"message\":\"token was already used\"}"));
    assertThatThrownBy(() -> gateway.authorize("tokn_test_3", 5000, "order:abc"))
        .isInstanceOf(GatewayDeclined.class);
    replies.put(
        "POST /charges",
        Reply.json(
            401,
            "{\"object\":\"error\",\"code\":\"authentication_failure\",\"message\":\"authentication failed\"}"));
    assertThatThrownBy(() -> gateway.authorize("tokn_test_3", 5000, "order:abc"))
        .isInstanceOf(GatewayFailure.class)
        .hasMessageNotContaining(SECRET);
    replies.put("POST /charges", Reply.json(500, "oops"));
    assertThatThrownBy(() -> gateway.authorize("tokn_test_3", 5000, "order:abc"))
        .isInstanceOf(GatewayFailure.class);
  }

  @Test
  void anUnreachableProviderIsAFailure() {
    server.stop(0);
    assertThatThrownBy(() -> gateway.isPaid("chrg_test_1")).isInstanceOf(GatewayFailure.class);
  }

  @Test
  void refusesCardsThatNeedThreeDSecure() {
    replies.put(
        "POST /charges",
        Reply.json(
            200,
            """
            {"object":"charge","id":"chrg_test_4","status":"pending","authorized":false,
             "authorize_uri":"https://pay.omise.co/payments/x/authorize"}
            """));
    assertThatThrownBy(() -> gateway.authorize("tokn_test_4", 5000, "order:abc"))
        .isInstanceOf(GatewayDeclined.class)
        .hasMessageContaining("3-D Secure");
  }

  @Test
  void releasesAndRefusesAnUnsupportedCardBrand() {
    replies.put(
        "POST /charges",
        Reply.json(
            200,
            """
            {"object":"charge","id":"chrg_test_5","status":"pending","authorized":true,
             "card":{"brand":"Discover","last_digits":"1117"}}
            """));
    replies.put(
        "POST /charges/chrg_test_5/reverse",
        Reply.json(200, "{\"object\":\"charge\",\"id\":\"chrg_test_5\",\"reversed\":true}"));
    assertThatThrownBy(() -> gateway.authorize("tokn_test_5", 5000, "order:abc"))
        .isInstanceOf(GatewayDeclined.class);
    assertThat(last().path()).isEqualTo("/charges/chrg_test_5/reverse");
  }

  @Test
  void capturesAndReversesByChargeId() {
    replies.put(
        "POST /charges/chrg_test_6/capture",
        Reply.json(
            200,
            "{\"object\":\"charge\",\"id\":\"chrg_test_6\",\"status\":\"successful\",\"paid\":true}"));
    gateway.capture("chrg_test_6", 5000);
    assertThat(last().path()).isEqualTo("/charges/chrg_test_6/capture");
    assertThat(last().authorization()).isEqualTo(basic());

    replies.put(
        "POST /charges/chrg_test_7/capture",
        Reply.json(
            400,
            "{\"object\":\"error\",\"code\":\"failed_capture\",\"message\":\"failed capture\"}"));
    assertThatThrownBy(() -> gateway.capture("chrg_test_7", 5000))
        .isInstanceOf(GatewayDeclined.class);

    replies.put(
        "POST /charges/chrg_test_8/reverse",
        Reply.json(200, "{\"object\":\"charge\",\"id\":\"chrg_test_8\",\"reversed\":true}"));
    gateway.cancel("chrg_test_8");
    assertThat(last().path()).isEqualTo("/charges/chrg_test_8/reverse");
  }

  @Test
  void neverLetsAReferenceChangeTheRequestPath() {
    assertThatThrownBy(() -> gateway.capture("../tokens", 5000)).isInstanceOf(GatewayFailure.class);
    assertThatThrownBy(() -> gateway.isPaid("chrg/../x")).isInstanceOf(GatewayFailure.class);
    assertThat(seen).isEmpty();
  }

  @Test
  void createsAPromptPayChargeAndKeepsItsQrAsADataUrl() {
    var deadline = Instant.parse("2026-11-14T03:15:00.123Z");
    replies.put(
        "POST /charges",
        Reply.json(
            200,
            """
            {"object":"charge","id":"chrg_test_9","status":"pending","expires_at":"2026-11-14T03:15:00Z",
             "source":{"type":"promptpay","scannable_code":{"object":"barcode","type":"qr",
               "image":{"object":"document","download_uri":"%s/charges/chrg_test_9/documents/docu_1/downloads/abc"}}}}
            """
                .formatted(base)));
    replies.put(
        "GET /charges/chrg_test_9/documents/docu_1/downloads/abc",
        new Reply(200, "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8)));
    var charge = gateway.createPromptPay(153000, "order:abc", deadline);
    assertThat(charge.reference()).isEqualTo("chrg_test_9");
    assertThat(charge.expiresAt()).isEqualTo(Instant.parse("2026-11-14T03:15:00Z"));
    assertThat(charge.qr())
        .isEqualTo(
            "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString("<svg/>".getBytes(StandardCharsets.UTF_8)));
    Seen create;
    synchronized (seen) {
      create = seen.get(0);
    }
    assertThat(create.form())
        .containsEntry("amount", "153000")
        .containsEntry("currency", "THB")
        .containsEntry("source[type]", "promptpay")
        .containsEntry("expires_at", "2026-11-14T03:15:00Z");
    // The secret key is only ever sent to create the charge, never to fetch the image.
    assertThat(create.authorization()).isEqualTo(basic());
    assertThat(last().authorization()).isNull();
  }

  @Test
  void followsTheQrLinksRedirectWithoutTheKey() {
    replies.put(
        "POST /charges",
        Reply.json(
            200,
            """
            {"object":"charge","id":"chrg_test_12","status":"pending",
             "source":{"scannable_code":{"image":{"download_uri":"%s/charges/chrg_test_12/documents/docu_2/downloads/x"}}}}
            """
                .formatted(base)));
    // Like Omise: the link answers with a redirect to presigned storage elsewhere.
    String storage = base.replace("127.0.0.1", "localhost") + "/storage/qrcode.svg?signature=abc";
    replies.put(
        "GET /charges/chrg_test_12/documents/docu_2/downloads/x",
        new Reply(302, "text/html", new byte[0]));
    redirects.put("GET /charges/chrg_test_12/documents/docu_2/downloads/x", storage);
    replies.put(
        "GET /storage/qrcode.svg",
        new Reply(200, "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8)));
    var charge = gateway.createPromptPay(5000, "order:abc", Instant.now().plusSeconds(900));
    assertThat(charge.qr()).startsWith("data:image/svg+xml;base64,");
    assertThat(last().path()).isEqualTo("/storage/qrcode.svg");
    synchronized (seen) {
      assertThat(seen.stream().filter(request -> request.method().equals("GET")))
          .allSatisfy(request -> assertThat(request.authorization()).isNull());
    }
  }

  @Test
  void neverFollowsARedirectWithTheKey() {
    replies.put("GET /charges/chrg_test_13", new Reply(302, "text/html", new byte[0]));
    redirects.put(
        "GET /charges/chrg_test_13", base.replace("127.0.0.1", "localhost") + "/elsewhere");
    assertThatThrownBy(() -> gateway.isPaid("chrg_test_13")).isInstanceOf(GatewayFailure.class);
    synchronized (seen) {
      assertThat(seen).extracting(Seen::path).doesNotContain("/elsewhere");
    }
  }

  @Test
  void aPromptPayChargeWithoutAQrIsAFailure() {
    replies.put(
        "POST /charges",
        Reply.json(200, "{\"object\":\"charge\",\"id\":\"chrg_test_10\",\"status\":\"pending\"}"));
    assertThatThrownBy(() -> gateway.createPromptPay(5000, "order:abc", Instant.now()))
        .isInstanceOf(GatewayFailure.class);
  }

  @Test
  void aPromptPayChargeIsPaidOnlyWhenSuccessful() {
    replies.put(
        "GET /charges/chrg_test_11",
        Reply.json(200, "{\"object\":\"charge\",\"id\":\"chrg_test_11\",\"status\":\"pending\"}"));
    assertThat(gateway.isPaid("chrg_test_11")).isFalse();
    replies.put(
        "GET /charges/chrg_test_11",
        Reply.json(
            200, "{\"object\":\"charge\",\"id\":\"chrg_test_11\",\"status\":\"successful\"}"));
    assertThat(gateway.isPaid("chrg_test_11")).isTrue();
    replies.put(
        "GET /charges/chrg_test_11",
        Reply.json(200, "{\"object\":\"charge\",\"id\":\"chrg_test_11\",\"status\":\"expired\"}"));
    assertThat(gateway.isPaid("chrg_test_11")).isFalse();
  }

  @Test
  void mapsOmiseBrandNames() {
    assertThat(OmiseGateway.brand("Visa")).contains(CardBrand.VISA);
    assertThat(OmiseGateway.brand("MasterCard")).contains(CardBrand.MASTERCARD);
    assertThat(OmiseGateway.brand("JCB")).contains(CardBrand.JCB);
    assertThat(OmiseGateway.brand("American Express")).contains(CardBrand.AMEX);
    assertThat(OmiseGateway.brand("UnionPay")).contains(CardBrand.UNIONPAY);
    assertThat(OmiseGateway.brand("Discover")).isEmpty();
  }
}

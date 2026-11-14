package com.chanakanlabs.theparlor.payments.internal.gateway.omise;

import com.chanakanlabs.theparlor.payments.internal.domain.CardBrand;
import com.chanakanlabs.theparlor.payments.internal.gateway.PaymentGateway;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Omise (Opn Payments) through its REST API, following https://docs.omise.co. Cards are tokenized
 * in the customer's browser with Omise.js and the public key, so card details never reach this
 * server; this adapter only charges, captures and reverses those tokens with the secret key, and
 * creates and checks PromptPay QR charges.
 *
 * <p>Card charges are authorized with capture=false and captured or reversed later. A card that
 * needs 3-D Secure is refused, because checkout has no redirect step.
 *
 * <p>Keys, tokens and card data are never logged or put in exception messages.
 */
class OmiseGateway implements PaymentGateway {
  static final String NAME = "omise";

  /** Pinned so a change of the account's default version cannot change responses. */
  static final String API_VERSION = "2019-05-29";

  private static final String CURRENCY = "THB";

  /** Request errors that mean the customer's card or token is the problem, not the provider. */
  private static final Set<String> CARD_ERRORS =
      Set.of(
          "invalid_card",
          "invalid_card_token",
          "used_token",
          "missing_card",
          "failed_fraud_check",
          "brand_not_supported",
          "invalid_amount");

  private final OmiseProperties properties;

  /** Carries the secret key, so it never follows a redirect to another host. */
  private final RestClient client;

  /**
   * Fetches QR images. Their links redirect to presigned storage URLs that refuse any other
   * credentials, so it follows redirects and never sends a key.
   */
  private final RestClient downloads;

  private final JsonMapper json = JsonMapper.builder().build();

  OmiseGateway(OmiseProperties properties) {
    if (blank(properties.publicKey()) || blank(properties.secretKey()))
      throw new IllegalStateException(
          "theparlor.payments.omise.public-key and secret-key are required for the omise gateway");
    this.properties = properties;
    this.client = restClient(HttpClient.Redirect.NEVER);
    this.downloads = restClient(HttpClient.Redirect.NORMAL);
  }

  private RestClient restClient(HttpClient.Redirect redirects) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(redirects)
                .build());
    factory.setReadTimeout(properties.readTimeout());
    return RestClient.builder().requestFactory(factory).build();
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Optional<String> browserTokenizationKey() {
    return Optional.of(properties.publicKey());
  }

  @Override
  public IssuedToken tokenize(Card card) {
    // Omise only accepts card details sent from the browser through Omise.js.
    throw new GatewayFailure("Cards are tokenized in the browser for this payment provider");
  }

  @Override
  public Authorization authorize(String token, long amountMinor, String payableReference) {
    var form = new LinkedHashMap<String, String>();
    form.put("amount", Long.toString(amountMinor));
    form.put("currency", CURRENCY);
    form.put("card", token);
    form.put("capture", "false");
    form.put("description", payableReference);
    form.put("metadata[payable]", payableReference);
    var charge = call(HttpMethod.POST, "/charges", form, CARD_ERRORS);
    String id = required(charge, "id");
    String status = charge.path("status").asString("");
    if ("failed".equals(status)) throw declined(charge);
    if (!charge.path("authorized").asBoolean(false)) {
      if (!charge.path("authorize_uri").asString("").isEmpty())
        throw new GatewayDeclined(
            "This card needs 3-D Secure verification, which checkout does not support yet");
      throw new GatewayFailure("The payment provider did not authorize the charge");
    }
    var card = charge.path("card");
    String last4 = card.path("last_digits").asString("");
    var brand = brand(card.path("brand").asString(""));
    if (brand.isEmpty() || !last4.matches("[0-9]{4}")) {
      // Release the hold before refusing, so the customer is not left with reserved money.
      reverseQuietly(id);
      throw new GatewayDeclined("This card brand is not accepted");
    }
    return new Authorization(id, brand.get(), last4);
  }

  @Override
  public void capture(String reference, long amountMinor) {
    var charge =
        call(
            HttpMethod.POST,
            "/charges/" + path(reference) + "/capture",
            Map.of(),
            Set.of("failed_capture", "expired_charge"));
    if ("failed".equals(charge.path("status").asString(""))) throw declined(charge);
  }

  @Override
  public void cancel(String reference) {
    call(HttpMethod.POST, "/charges/" + path(reference) + "/reverse", Map.of(), Set.of());
  }

  @Override
  public PromptPayCharge createPromptPay(
      long amountMinor, String payableReference, Instant expiresAt) {
    var form = new LinkedHashMap<String, String>();
    form.put("amount", Long.toString(amountMinor));
    form.put("currency", CURRENCY);
    form.put("source[type]", "promptpay");
    form.put(
        "expires_at",
        DateTimeFormatter.ISO_INSTANT.format(expiresAt.truncatedTo(ChronoUnit.SECONDS)));
    form.put("description", payableReference);
    form.put("metadata[payable]", payableReference);
    var charge = call(HttpMethod.POST, "/charges", form, Set.of("invalid_amount"));
    String id = required(charge, "id");
    if ("failed".equals(charge.path("status").asString(""))) throw declined(charge);
    String download =
        charge
            .path("source")
            .path("scannable_code")
            .path("image")
            .path("download_uri")
            .asString("");
    if (download.isEmpty()) throw new GatewayFailure("The payment provider returned no QR code");
    Instant providerExpiry = expiresAt;
    String expiry = charge.path("expires_at").asString("");
    if (!expiry.isEmpty()) {
      try {
        providerExpiry = Instant.parse(expiry);
      } catch (RuntimeException ignored) {
        // Keep the requested deadline when the provider's is unreadable.
      }
    }
    return new PromptPayCharge(id, qrImage(download), providerExpiry);
  }

  @Override
  public boolean isPaid(String reference) {
    var charge = call(HttpMethod.GET, "/charges/" + path(reference), null, Set.of());
    return "successful".equals(charge.path("status").asString(""));
  }

  /**
   * The QR image as a data: URL, so the stored QR does not depend on how long the provider keeps
   * its download link. The link needs no key: it redirects to a presigned storage URL.
   */
  private String qrImage(String download) {
    URI uri;
    try {
      uri = URI.create(download);
    } catch (IllegalArgumentException invalid) {
      throw new GatewayFailure("The payment provider returned an unreadable QR code link");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme()) && !sameOrigin(uri))
      throw new GatewayFailure("The payment provider returned an insecure QR code link");
    try {
      return downloads
          .get()
          .uri(uri)
          .exchange(
              (request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful())
                  throw new GatewayFailure("The payment provider did not return the QR code");
                byte[] image = response.getBody().readAllBytes();
                if (image.length == 0 || image.length > 1_000_000)
                  throw new GatewayFailure("The payment provider returned an unusable QR code");
                var type = response.getHeaders().getContentType();
                String mime =
                    type != null && "image".equals(type.getType())
                        ? type.getType() + "/" + type.getSubtype()
                        : "image/svg+xml";
                return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image);
              });
    } catch (RestClientException failure) {
      throw new GatewayFailure("The payment provider could not be reached");
    }
  }

  /** Only for tests, whose stand-in provider has no TLS. */
  private boolean sameOrigin(URI uri) {
    var api = properties.apiUrl();
    return api.getScheme().equalsIgnoreCase(uri.getScheme())
        && api.getHost().equalsIgnoreCase(uri.getHost())
        && api.getPort() == uri.getPort();
  }

  /**
   * Sends one request and returns the response object. Error objects whose code is in cardErrors
   * become GatewayDeclined; every other failure becomes GatewayFailure.
   */
  private JsonNode call(
      HttpMethod method, String path, Map<String, String> form, Set<String> cardErrors) {
    try {
      var request =
          client
              .method(method)
              .uri(URI.create(stripSlash(properties.apiUrl()) + path))
              .headers(
                  headers -> {
                    authorize(headers);
                    headers.set("Omise-Version", API_VERSION);
                    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                  });
      if (form != null)
        request = request.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(encode(form));
      return request.exchange(
          (sent, response) -> {
            JsonNode body = parse(response.getBody().readAllBytes());
            if (response.getStatusCode().is2xxSuccessful() && !isError(body)) return body;
            String code = body.path("code").asString("");
            if (cardErrors.contains(code))
              throw new GatewayDeclined(message(body, "The payment was declined"));
            throw new GatewayFailure(
                "The payment provider failed" + (code.isEmpty() ? "" : " (" + code + ")"));
          });
    } catch (RestClientException failure) {
      throw new GatewayFailure("The payment provider could not be reached");
    }
  }

  private void authorize(HttpHeaders headers) {
    // HTTP Basic with the secret key as the user name and an empty password.
    headers.set(
        HttpHeaders.AUTHORIZATION,
        "Basic "
            + Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8)));
  }

  private void reverseQuietly(String reference) {
    try {
      cancel(reference);
    } catch (GatewayFailure | GatewayDeclined ignored) {
      // An unreleased authorization expires at the provider on its own.
    }
  }

  private JsonNode parse(byte[] body) throws IOException {
    try {
      return json.readTree(body);
    } catch (JacksonException unreadable) {
      throw new GatewayFailure("The payment provider returned an unreadable response");
    }
  }

  private static boolean isError(JsonNode body) {
    return "error".equals(body.path("object").asString(""));
  }

  /** A failed charge: the issuer or the customer's bank refused it. */
  private static GatewayDeclined declined(JsonNode charge) {
    return new GatewayDeclined(message(charge.path("failure_message"), "The payment was declined"));
  }

  private static String message(JsonNode node, String fallback) {
    String message = node.isObject() ? node.path("message").asString("") : node.asString("");
    return message.isBlank() ? fallback : message;
  }

  private static String required(JsonNode charge, String field) {
    String value = charge.path(field).asString("");
    if (value.isEmpty()) throw new GatewayFailure("The payment provider returned no charge");
    return value;
  }

  static Optional<CardBrand> brand(String omiseBrand) {
    String key = omiseBrand.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    return switch (key) {
      case "visa" -> Optional.of(CardBrand.VISA);
      case "mastercard" -> Optional.of(CardBrand.MASTERCARD);
      case "jcb" -> Optional.of(CardBrand.JCB);
      case "amex", "americanexpress" -> Optional.of(CardBrand.AMEX);
      case "unionpay", "chinaunionpay" -> Optional.of(CardBrand.UNIONPAY);
      default -> Optional.empty();
    };
  }

  private static String encode(Map<String, String> form) {
    return form.entrySet().stream()
        .map(
            entry ->
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  /** Charge IDs come back from the provider; never let one change the request path. */
  private static String path(String reference) {
    if (reference == null || !reference.matches("[A-Za-z0-9_]+"))
      throw new GatewayFailure("Unknown payment reference");
    return reference;
  }

  private static String stripSlash(URI uri) {
    return uri.toString().replaceAll("/+$", "");
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}

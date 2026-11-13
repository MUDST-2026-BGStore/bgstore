package com.chanakanlabs.theparlor.carts.internal.web;

import com.chanakanlabs.theparlor.carts.internal.application.CartsService;
import com.chanakanlabs.theparlor.contracts.api.CartsApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import com.chanakanlabs.theparlor.shared.Sha256;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HexFormat;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CartsController implements CartsApi {
  private final CartsService carts;
  private final HttpServletRequest request;

  public CartsController(CartsService carts, HttpServletRequest request) {
    this.carts = carts;
    this.request = request;
  }

  @Override
  public Cart addCartItem(
      UUID cartId, AddCartItemRequest addCartItemRequest, @Nullable String xCartToken) {
    return carts.addItem(cartId, xCartToken, addCartItemRequest);
  }

  @Override
  public Order checkoutCart(
      UUID cartId,
      UUID idempotencyKey,
      CheckoutCartRequest checkoutCartRequest,
      @Nullable String xCartToken) {
    return carts.checkout(cartId, idempotencyKey, xCartToken, checkoutCartRequest);
  }

  @Override
  public CartCreated createCart(UUID idempotencyKey) {
    // A guest has no subject, so retries are recognized by browser session. The session ID is
    // hashed because idempotency keys are stored and must not double as session credentials.
    String session =
        "session:" + HexFormat.of().formatHex(Sha256.of(request.getSession(true).getId()));
    return carts.create(idempotencyKey, session);
  }

  @Override
  public void deleteCart(UUID cartId, @Nullable String xCartToken) {
    carts.abandon(cartId, xCartToken);
  }

  @Override
  public Cart getCart(UUID cartId, @Nullable String xCartToken) {
    return carts.get(cartId, xCartToken);
  }

  @Override
  public Cart getMyCart() {
    return carts.getMine();
  }

  @Override
  public Cart mergeCart(MergeCartRequest mergeCartRequest) {
    return carts.merge(mergeCartRequest);
  }

  @Override
  public void removeCartItem(UUID cartId, UUID itemId, @Nullable String xCartToken) {
    carts.removeItem(cartId, itemId, xCartToken);
  }

  @Override
  public Cart updateCart(
      UUID cartId, UpdateCartRequest updateCartRequest, @Nullable String xCartToken) {
    return carts.updateFulfillment(cartId, xCartToken, updateCartRequest);
  }

  @Override
  public Cart updateCartItem(
      UUID cartId,
      UUID itemId,
      UpdateCartItemRequest updateCartItemRequest,
      @Nullable String xCartToken) {
    return carts.updateItem(cartId, itemId, xCartToken, updateCartItemRequest);
  }
}

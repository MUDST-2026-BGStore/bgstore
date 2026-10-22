package com.chanakanlabs.theparlor.carts.internal.web;

import com.chanakanlabs.theparlor.contracts.api.CartsApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.time.*;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CartsController implements CartsApi {

  @Override
  public Cart addCartItem(
      UUID cartId, AddCartItemRequest addCartItemRequest, @Nullable String xCartToken) {
    // TODO(carts-api): implement addCartItem — POST /api/v1/carts/{cartId}/items — Add a line to a
    // cart
    throw new UnsupportedOperationException(
        "POST /api/v1/carts/{cartId}/items (addCartItem) is not implemented yet");
  }

  @Override
  public Order checkoutCart(UUID cartId, UUID idempotencyKey, @Nullable String xCartToken) {
    // TODO(carts-api): implement checkoutCart — POST /api/v1/carts/{cartId}/checkout — Place an
    // order from a cart
    throw new UnsupportedOperationException(
        "POST /api/v1/carts/{cartId}/checkout (checkoutCart) is not implemented yet");
  }

  @Override
  public CartCreated createCart(UUID idempotencyKey) {
    // TODO(carts-api): implement createCart — POST /api/v1/carts — Create a cart for the caller
    throw new UnsupportedOperationException(
        "POST /api/v1/carts (createCart) is not implemented yet");
  }

  @Override
  public void deleteCart(UUID cartId, @Nullable String xCartToken) {
    // TODO(carts-api): implement deleteCart — DELETE /api/v1/carts/{cartId} — Abandon a cart
    throw new UnsupportedOperationException(
        "DELETE /api/v1/carts/{cartId} (deleteCart) is not implemented yet");
  }

  @Override
  public Cart getCart(UUID cartId, @Nullable String xCartToken) {
    // TODO(carts-api): implement getCart — GET /api/v1/carts/{cartId} — Read a cart with
    // revalidated lines
    throw new UnsupportedOperationException(
        "GET /api/v1/carts/{cartId} (getCart) is not implemented yet");
  }

  @Override
  public Cart getMyCart() {
    // TODO(carts-api): implement getMyCart — GET /api/v1/me/cart — Get the signed-in account&#39;s
    // open cart
    throw new UnsupportedOperationException(
        "GET /api/v1/me/cart (getMyCart) is not implemented yet");
  }

  @Override
  public Cart mergeCart(MergeCartRequest mergeCartRequest) {
    // TODO(carts-api): implement mergeCart — POST /api/v1/me/cart/merge — Merge a guest cart into
    // the account&#39;s open cart
    throw new UnsupportedOperationException(
        "POST /api/v1/me/cart/merge (mergeCart) is not implemented yet");
  }

  @Override
  public void removeCartItem(UUID cartId, UUID itemId, @Nullable String xCartToken) {
    // TODO(carts-api): implement removeCartItem — DELETE /api/v1/carts/{cartId}/items/{itemId} —
    // Remove a line from a cart
    throw new UnsupportedOperationException(
        "DELETE /api/v1/carts/{cartId}/items/{itemId} (removeCartItem) is not implemented yet");
  }

  @Override
  public Cart updateCart(
      UUID cartId, UpdateCartRequest updateCartRequest, @Nullable String xCartToken) {
    // TODO(carts-api): implement updateCart — PATCH /api/v1/carts/{cartId} — Choose pickup or
    // delivery for the whole cart
    throw new UnsupportedOperationException(
        "PATCH /api/v1/carts/{cartId} (updateCart) is not implemented yet");
  }

  @Override
  public Cart updateCartItem(
      UUID cartId,
      UUID itemId,
      UpdateCartItemRequest updateCartItemRequest,
      @Nullable String xCartToken) {
    // TODO(carts-api): implement updateCartItem — PATCH /api/v1/carts/{cartId}/items/{itemId} — Set
    // a line&#39;s quantity
    throw new UnsupportedOperationException(
        "PATCH /api/v1/carts/{cartId}/items/{itemId} (updateCartItem) is not implemented yet");
  }
}

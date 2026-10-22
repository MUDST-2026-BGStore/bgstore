package com.chanakanlabs.theparlor.orders.internal.web;

import com.chanakanlabs.theparlor.contracts.api.OrdersApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import java.time.*;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrdersController implements OrdersApi {

  @Override
  public Order cancelOrder(UUID branchId, UUID orderId, ReasonRequest reasonRequest) {
    // TODO(orders-api): implement cancelOrder — POST
    // /api/v1/branches/{branchId}/orders/{orderId}/cancel — Cancel a placed sale order
    throw new UnsupportedOperationException(
        "POST /api/v1/branches/{branchId}/orders/{orderId}/cancel (cancelOrder) is not implemented yet");
  }

  @Override
  public Order createMyOrder(
      UUID branchId, UUID idempotencyKey, CreateOrderRequest createOrderRequest) {
    // TODO(orders-api): implement createMyOrder — POST /api/v1/branches/{branchId}/orders — Place
    // an order for games sold by this branch
    throw new UnsupportedOperationException(
        "POST /api/v1/branches/{branchId}/orders (createMyOrder) is not implemented yet");
  }

  @Override
  public Order createStaffOrder(
      UUID branchId, UUID idempotencyKey, CreateStaffOrderRequest createStaffOrderRequest) {
    // TODO(orders-api): implement createStaffOrder — POST /api/v1/branches/{branchId}/staff/orders
    // — Record a counter sale for a registered customer or guest
    throw new UnsupportedOperationException(
        "POST /api/v1/branches/{branchId}/staff/orders (createStaffOrder) is not implemented yet");
  }

  @Override
  public Order fulfillOrder(UUID branchId, UUID orderId) {
    // TODO(orders-api): implement fulfillOrder — POST
    // /api/v1/branches/{branchId}/orders/{orderId}/fulfill — Staff-only handover of purchased games
    throw new UnsupportedOperationException(
        "POST /api/v1/branches/{branchId}/orders/{orderId}/fulfill (fulfillOrder) is not implemented yet");
  }

  @Override
  public Order getOrder(UUID branchId, UUID orderId) {
    // TODO(orders-api): implement getOrder — GET /api/v1/branches/{branchId}/orders/{orderId} —
    // Read an order as its owner or branch staff
    throw new UnsupportedOperationException(
        "GET /api/v1/branches/{branchId}/orders/{orderId} (getOrder) is not implemented yet");
  }

  @Override
  public OrderPage listBranchOrders(
      UUID branchId, Integer limit, @Nullable String cursor, @Nullable OrderStatus status) {
    // TODO(orders-api): implement listBranchOrders — GET /api/v1/branches/{branchId}/orders — List
    // branch sale orders for staff
    throw new UnsupportedOperationException(
        "GET /api/v1/branches/{branchId}/orders (listBranchOrders) is not implemented yet");
  }

  @Override
  public OrderPage listMyOrders(
      Integer limit, @Nullable String cursor, @Nullable OrderStatus status) {
    // TODO(orders-api): implement listMyOrders — GET /api/v1/me/orders — List the signed-in
    // customer&#39;s sale orders across branches
    throw new UnsupportedOperationException(
        "GET /api/v1/me/orders (listMyOrders) is not implemented yet");
  }
}

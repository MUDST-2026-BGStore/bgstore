package com.chanakanlabs.theparlor.orders.internal.web;

import com.chanakanlabs.theparlor.contracts.api.OrdersApi;
import com.chanakanlabs.theparlor.contracts.model.*;
import com.chanakanlabs.theparlor.orders.internal.application.OrdersService;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrdersController implements OrdersApi {
  private final OrdersService orders;

  public OrdersController(OrdersService orders) {
    this.orders = orders;
  }

  @Override
  public Order cancelOrder(UUID branchId, UUID orderId, ReasonRequest reasonRequest) {
    return orders.cancelOrder(branchId, orderId, reasonRequest.getReason());
  }

  @Override
  public Order createMyOrder(
      UUID branchId, UUID idempotencyKey, CreateOrderRequest createOrderRequest) {
    return orders.createMyOrder(branchId, idempotencyKey, createOrderRequest);
  }

  @Override
  public Order createStaffOrder(
      UUID branchId, UUID idempotencyKey, CreateStaffOrderRequest createStaffOrderRequest) {
    return orders.createStaffOrder(branchId, idempotencyKey, createStaffOrderRequest);
  }

  @Override
  public Order fulfillOrder(
      UUID branchId, UUID orderId, @Nullable FulfillOrderRequest fulfillOrderRequest) {
    return orders.fulfillOrder(branchId, orderId, fulfillOrderRequest);
  }

  @Override
  public Order getOrder(UUID branchId, UUID orderId) {
    return orders.getOrder(branchId, orderId);
  }

  @Override
  public OrderPage listBranchOrders(
      UUID branchId, Integer limit, @Nullable String cursor, @Nullable OrderStatus status) {
    return orders.listBranchOrders(branchId, limit, cursor, status);
  }

  @Override
  public OrderPage listMyOrders(
      Integer limit, @Nullable String cursor, @Nullable OrderStatus status) {
    return orders.listMyOrders(limit, cursor, status);
  }
}

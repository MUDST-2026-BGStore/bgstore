package com.chanakanlabs.theparlor.carts.internal.application;

import com.chanakanlabs.theparlor.account.CurrentAccount;
import com.chanakanlabs.theparlor.branches.BranchAccess;
import com.chanakanlabs.theparlor.carts.internal.domain.CartItem;
import com.chanakanlabs.theparlor.carts.internal.domain.CartItemId;
import com.chanakanlabs.theparlor.carts.internal.domain.CartStatus;
import com.chanakanlabs.theparlor.carts.internal.persistence.CartItemRepository;
import com.chanakanlabs.theparlor.carts.internal.persistence.CartRepository;
import com.chanakanlabs.theparlor.contracts.model.*;
import com.chanakanlabs.theparlor.idempotency.Idempotency;
import com.chanakanlabs.theparlor.idempotency.Idempotency.Scope;
import com.chanakanlabs.theparlor.identity.CurrentUser;
import com.chanakanlabs.theparlor.inventory.SaleInventory;
import com.chanakanlabs.theparlor.inventory.SaleInventory.Quote;
import com.chanakanlabs.theparlor.orders.OrderPlacement;
import com.chanakanlabs.theparlor.orders.ShippingPolicy;
import com.chanakanlabs.theparlor.shared.Sha256;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Persistent carts. A cart reserves nothing: every response reprices its lines and rechecks stock
 * at the cart's fulfillment location, and only checkout holds stock.
 *
 * <p>A cart is reachable by its account's session or by presenting its bearer token; anyone else
 * gets the same 404 as for a cart that does not exist.
 */
@Service
@Transactional(readOnly = true)
public class CartsService {
  /** Maximum distinct editions in a cart, matching the order line limit. */
  static final int MAX_LINES = 50;

  private final CartRepository carts;
  private final CartItemRepository items;
  private final SaleInventory inventory;
  private final BranchAccess branches;
  private final CurrentUser currentUser;
  private final CurrentAccount currentAccount;
  private final Idempotency idempotency;
  private final OrderPlacement orders;
  private final SecureRandom random = new SecureRandom();

  public CartsService(
      CartRepository carts,
      CartItemRepository items,
      SaleInventory inventory,
      BranchAccess branches,
      CurrentUser currentUser,
      CurrentAccount currentAccount,
      Idempotency idempotency,
      OrderPlacement orders) {
    this.carts = carts;
    this.items = items;
    this.inventory = inventory;
    this.branches = branches;
    this.currentUser = currentUser;
    this.currentAccount = currentAccount;
    this.idempotency = idempotency;
    this.orders = orders;
  }

  /**
   * Opens a cart, or returns the account's open cart. The response always carries a fresh token: a
   * retry means the first response, and its token, may never have arrived. anonymousCaller
   * identifies a guest's browser session for retry detection.
   */
  @Transactional
  public CartCreated create(UUID idempotencyKey, String anonymousCaller) {
    String caller = currentUser.find().map(user -> user.subject()).orElse(anonymousCaller);
    UUID accountId = currentAccount.findId().orElse(null);
    String token = newToken();
    byte[] tokenHash = Sha256.of(token);
    UUID cartId =
        idempotency.once(
            new Scope(caller, "POST", "/api/v1/carts"),
            idempotencyKey,
            "",
            () ->
                accountId == null
                    ? carts
                        .saveAndFlush(
                            com.chanakanlabs.theparlor.carts.internal.domain.Cart.open(
                                null, tokenHash))
                        .getId()
                    : openCartOf(accountId).getId());
    var cart = carts.lockById(cartId).orElseThrow(CartsService::missing);
    requireOpen(cart);
    cart.rotateToken(tokenHash);
    var view = view(cart);
    return new CartCreated(
        view.getId(),
        view.getFulfillment(),
        view.getItems(),
        view.getIssues(),
        view.getSubtotal(),
        view.getShippingFee(),
        view.getTotal(),
        view.getCheckoutReady(),
        view.getCreatedAt(),
        view.getUpdatedAt(),
        token);
  }

  @Transactional
  public Cart getMine() {
    return view(openCartOf(currentAccount.requireId()));
  }

  public Cart get(UUID cartId, @Nullable String token) {
    var cart = authorized(carts.findById(cartId), token);
    requireOpen(cart);
    return view(cart);
  }

  @Transactional
  public Cart updateFulfillment(UUID cartId, @Nullable String token, UpdateCartRequest request) {
    var cart = locked(cartId, token);
    var fulfillment = request.getFulfillment();
    if (fulfillment.getMethod() == UpdateCartRequestFulfillment.MethodEnum.PICKUP) {
      if (fulfillment.getPickupBranchId() == null)
        throw new IllegalArgumentException("Pickup needs pickupBranchId");
      branches.requireActive(fulfillment.getPickupBranchId());
      cart.pickUpAt(fulfillment.getPickupBranchId());
    } else {
      cart.ship();
    }
    carts.flush();
    return view(cart);
  }

  /** Adds units of one edition; unlike a quantity update, it never exceeds what can be bought. */
  @Transactional
  public Cart addItem(UUID cartId, @Nullable String token, AddCartItemRequest request) {
    var cart = locked(cartId, token);
    UUID editionId = request.getEditionId();
    var quote = inventory.quote(location(cart).orElse(null), List.of(editionId)).get(editionId);
    if (quote == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Edition not found");
    if (quote.blocker() != null)
      throw conflict("This edition is not sold at the cart's fulfillment location");
    var id = new CartItemId(cartId, editionId);
    var line = items.findById(id).orElse(null);
    int quantity =
        (line == null ? 0 : line.getQuantity())
            + (request.getQuantity() == null ? 1 : request.getQuantity());
    if (quantity > CartItem.MAX_QUANTITY || quantity > quote.available())
      throw conflict("Not enough stock to add that many");
    if (line != null) line.changeQuantity(quantity);
    else {
      if (items.findByIdCartIdOrderByAddedAtAscIdEditionIdAsc(cartId).size() >= MAX_LINES)
        throw conflict("A cart holds at most " + MAX_LINES + " different editions");
      items.save(CartItem.of(cartId, editionId, quantity));
    }
    items.flush();
    return view(cart);
  }

  /** Sets a quantity as asked; a shortage is reported as an issue rather than clamped. */
  @Transactional
  public Cart updateItem(
      UUID cartId, UUID editionId, @Nullable String token, UpdateCartItemRequest request) {
    var cart = locked(cartId, token);
    items
        .findById(new CartItemId(cartId, editionId))
        .orElseThrow(CartsService::missingLine)
        .changeQuantity(request.getQuantity());
    items.flush();
    return view(cart);
  }

  @Transactional
  public void removeItem(UUID cartId, UUID editionId, @Nullable String token) {
    locked(cartId, token);
    items.delete(
        items.findById(new CartItemId(cartId, editionId)).orElseThrow(CartsService::missingLine));
  }

  @Transactional
  public void abandon(UUID cartId, @Nullable String token) {
    locked(cartId, token).abandon(Sha256.of(newToken()));
  }

  /**
   * Moves a guest cart's lines into the caller's account cart and consumes the guest cart. Once the
   * token is accepted nothing below can fail, so a presented token never stays usable.
   */
  @Transactional
  public Cart merge(MergeCartRequest request) {
    UUID accountId = currentAccount.requireId();
    var guest = carts.lockById(request.getCartId()).orElseThrow(CartsService::missing);
    if (!Sha256.matches(guest.getTokenHash(), request.getCartToken())
        || (guest.getAccountId() != null && !guest.getAccountId().equals(accountId)))
      throw missing();
    requireOpen(guest);
    if (accountId.equals(guest.getAccountId()))
      throw conflict("This cart already belongs to the account");

    var mine = carts.lockById(openCartOf(accountId).getId()).orElseThrow(CartsService::missing);
    var lines = new ArrayList<>(items.findByIdCartIdOrderByAddedAtAscIdEditionIdAsc(mine.getId()));
    if (lines.isEmpty()) {
      // Nothing chosen on the account yet, so keep how the guest wanted to receive the order.
      if (guest.isShipping()) mine.ship();
      else mine.pickUpAt(guest.getPickupBranchId());
    }
    for (CartItem moved : items.findByIdCartIdOrderByAddedAtAscIdEditionIdAsc(guest.getId())) {
      UUID editionId = moved.getId().editionId();
      var existing =
          lines.stream().filter(line -> line.getId().editionId().equals(editionId)).findFirst();
      if (existing.isPresent())
        existing
            .get()
            .changeQuantity(
                Math.min(
                    CartItem.MAX_QUANTITY, existing.get().getQuantity() + moved.getQuantity()));
      else if (lines.size() < MAX_LINES)
        lines.add(items.save(CartItem.of(mine.getId(), editionId, moved.getQuantity())));
    }
    guest.abandon(Sha256.of(newToken()));
    items.flush();
    return view(mine);
  }

  /**
   * Turns the cart into one placed order. Stock is held, the payment is started and the cart is
   * converted in this transaction; any failure leaves the cart open and nothing held.
   */
  @Transactional
  public Order checkout(
      UUID cartId, UUID idempotencyKey, @Nullable String token, CheckoutCartRequest request) {
    var cart = authorized(carts.lockById(cartId), token);
    var user = currentUser.find();
    UUID accountId = currentAccount.findId().orElse(null);
    // A guest is identified by the cart itself: only a holder of its token gets this far.
    String caller = user.map(profile -> profile.subject()).orElse("cart:" + cartId);
    UUID orderId =
        idempotency.once(
            new Scope(caller, "POST", "/api/v1/carts/" + cartId + "/checkout"),
            idempotencyKey,
            request.toString(),
            () -> {
              requireOpen(cart);
              var lines = items.findByIdCartIdOrderByAddedAtAscIdEditionIdAsc(cartId);
              if (lines.isEmpty()) throw conflict("Cart is empty");
              UUID branchId =
                  location(cart)
                      .orElseThrow(() -> conflict("No branch can fulfill this cart right now"));
              if (cart.isShipping() != (request.getShippingAddress() != null))
                throw new IllegalArgumentException(
                    cart.isShipping()
                        ? "A delivery cart needs shippingAddress"
                        : "A pickup cart cannot have shippingAddress");

              var contact = request.getContact();
              var profile =
                  accountId == null
                      ? Optional.<Profile>empty()
                      : currentAccount.findCurrentProfile();
              if (contact == null && profile.isEmpty())
                throw new IllegalArgumentException("Guest checkout needs contact details");
              String name =
                  contact != null
                      ? contact.getName()
                      : (profile.get().getFirstName() + " " + profile.get().getLastName()).strip();
              String email =
                  contact != null && contact.getEmail() != null
                      ? contact.getEmail()
                      : profile.isPresent() ? user.get().email() : null;
              String phone =
                  contact != null && contact.getPhone() != null
                      ? contact.getPhone()
                      : profile.map(Profile::getPhoneNumber).orElse(null);

              var order =
                  orders.place(
                      new OrderPlacement.Placement(
                          branchId,
                          accountId,
                          accountId == null ? null : user.get().subject(),
                          name,
                          email,
                          phone,
                          lines.stream()
                              .map(
                                  line ->
                                      new OrderPlacement.Line(
                                          line.getId().editionId(), line.getQuantity()))
                              .toList(),
                          request.getShippingAddress(),
                          request.getNotes(),
                          request.getPaymentMethod() == null
                              ? null
                              : OrderPlacement.PaymentMethod.valueOf(
                                  request.getPaymentMethod().name()),
                          request.getPaymentToken()));
              cart.convert();
              return order.getId();
            });
    return orders.get(orderId);
  }

  /** The branch that would fulfill the cart: the chosen pickup branch, or the delivery branch. */
  private Optional<UUID> location(com.chanakanlabs.theparlor.carts.internal.domain.Cart cart) {
    return cart.isShipping()
        ? branches.deliveryBranch()
        : Optional.ofNullable(cart.getPickupBranchId());
  }

  private com.chanakanlabs.theparlor.carts.internal.domain.Cart openCartOf(UUID accountId) {
    carts.openForAccountIfAbsent(accountId, Sha256.of(newToken()));
    return carts
        .findByAccountIdAndStatus(accountId, CartStatus.OPEN)
        .orElseThrow(() -> new IllegalStateException("Account cart was not opened"));
  }

  /** A cart locked for change by a caller allowed to change it. */
  private com.chanakanlabs.theparlor.carts.internal.domain.Cart locked(
      UUID cartId, @Nullable String token) {
    var cart = authorized(carts.lockById(cartId), token);
    requireOpen(cart);
    return cart;
  }

  private com.chanakanlabs.theparlor.carts.internal.domain.Cart authorized(
      Optional<com.chanakanlabs.theparlor.carts.internal.domain.Cart> found,
      @Nullable String token) {
    var cart = found.orElseThrow(CartsService::missing);
    boolean owner =
        cart.getAccountId() != null
            && currentAccount.findId().filter(cart.getAccountId()::equals).isPresent();
    if (!owner && !Sha256.matches(cart.getTokenHash(), token)) throw missing();
    return cart;
  }

  private Cart view(com.chanakanlabs.theparlor.carts.internal.domain.Cart cart) {
    var lines = items.findByIdCartIdOrderByAddedAtAscIdEditionIdAsc(cart.getId());
    var quotes =
        inventory.quote(
            location(cart).orElse(null),
            lines.stream().map(line -> line.getId().editionId()).toList());
    var viewItems = new ArrayList<com.chanakanlabs.theparlor.contracts.model.CartItem>();
    var issues = new ArrayList<CartIssue>();
    long subtotal = 0;
    for (CartItem line : lines) {
      UUID editionId = line.getId().editionId();
      Quote quote = quotes.get(editionId);
      long lineTotal = Math.multiplyExact(quote.unitPriceMinor(), (long) line.getQuantity());
      subtotal = Math.addExact(subtotal, lineTotal);
      viewItems.add(
          new com.chanakanlabs.theparlor.contracts.model.CartItem(
              editionId,
              editionId,
              quote.gameId(),
              com.chanakanlabs.theparlor.contracts.model.CartItem.EditionKindEnum.fromValue(
                  quote.editionKind()),
              quote.editionLanguage(),
              quote.gameName(),
              line.getQuantity(),
              new Money(quote.unitPriceMinor()),
              new Money(lineTotal),
              quote.available()));
      if (quote.blocker() != null)
        issues.add(new CartIssue(editionId, CartIssue.CodeEnum.valueOf(quote.blocker().name())));
      else if (line.getQuantity() > quote.available())
        issues.add(
            new CartIssue(editionId, CartIssue.CodeEnum.INSUFFICIENT_STOCK)
                .available(quote.available()));
    }
    var fulfillment =
        new CartFulfillment(CartFulfillment.MethodEnum.valueOf(cart.getFulfillmentMethod().name()));
    fulfillment.setBranchId(cart.getPickupBranchId());
    long shippingFee = ShippingPolicy.feeMinor(cart.isShipping(), subtotal);
    return new Cart(
        cart.getId(),
        fulfillment,
        viewItems,
        issues,
        subtotal,
        new Money(shippingFee),
        new Money(Math.addExact(subtotal, shippingFee)),
        !viewItems.isEmpty() && issues.isEmpty(),
        cart.getCreatedAt().atOffset(ZoneOffset.UTC),
        cart.getUpdatedAt().atOffset(ZoneOffset.UTC));
  }

  /** 256 bits from a CSPRNG: unguessable, and long enough for the contract's 32 characters. */
  private String newToken() {
    byte[] secret = new byte[32];
    random.nextBytes(secret);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
  }

  private static void requireOpen(com.chanakanlabs.theparlor.carts.internal.domain.Cart cart) {
    if (!cart.isOpen()) throw conflict("Cart is no longer open");
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found");
  }

  private static ResponseStatusException missingLine() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart line not found");
  }

  private static ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }
}

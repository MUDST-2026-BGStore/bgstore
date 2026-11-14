import type { BranchGame, Order, RentalCopy } from '@theparlor/api-client';

export type InventoryChannel = 'shipping' | 'pickup' | 'counter';
export interface RetailStockSample {
  editionId?: string;
  gameId?: string;
  sku: string;
  name: string;
  edition: string;
  onHand: number;
  shippingHeld: number;
  pickupHeld: number;
  /** Null for live editions: the branch keeps no reorder threshold. */
  reorderAt: number | null;
}

export const retailStockSamples: readonly RetailStockSample[] = [
  {
    sku: 'AZUL-EN-B',
    name: 'Azul',
    edition: 'Base game · English',
    onHand: 12,
    shippingHeld: 3,
    pickupHeld: 2,
    reorderAt: 3,
  },
  {
    sku: 'CATAN-TH-B',
    name: 'Catan',
    edition: 'Base game · Thai',
    onHand: 5,
    shippingHeld: 0,
    pickupHeld: 3,
    reorderAt: 3,
  },
  {
    sku: 'WING-EN-B',
    name: 'Wingspan',
    edition: 'Base game · English',
    onHand: 4,
    shippingHeld: 2,
    pickupHeld: 2,
    reorderAt: 2,
  },
  {
    sku: 'CODE-EN-B',
    name: 'Codenames',
    edition: 'Base game · English',
    onHand: 0,
    shippingHeld: 0,
    pickupHeld: 0,
    reorderAt: 2,
  },
  {
    sku: 'PAND-EN-B',
    name: 'Pandemic',
    edition: 'Base game · English',
    onHand: 8,
    shippingHeld: 1,
    pickupHeld: 0,
    reorderAt: 2,
  },
];

export interface RentalCopySample {
  id?: string;
  gameId?: string;
  sku: string;
  assetCode: string;
  name: string;
  state: 'On shelf' | 'At table' | 'Maintenance';
  location: string;
}
export const rentalCopySamples: readonly RentalCopySample[] = [
  {
    sku: 'PZ-AZUL-RE',
    assetCode: 'AZ-C01',
    name: 'Azul',
    state: 'At table',
    location: 'Table 1',
  },
  {
    sku: 'PZ-AZUL-RE',
    assetCode: 'AZ-C02',
    name: 'Azul',
    state: 'On shelf',
    location: 'Shelf A2',
  },
  {
    sku: 'PZ-WING-RE',
    assetCode: 'WG-C01',
    name: 'Wingspan',
    state: 'At table',
    location: 'Table 2',
  },
  {
    sku: 'PZ-CATAN-RE',
    assetCode: 'CT-C01',
    name: 'Catan',
    state: 'Maintenance',
    location: 'Missing-piece check',
  },
  {
    sku: 'PZ-CODE-RE',
    assetCode: 'CN-C01',
    name: 'Codenames',
    state: 'On shelf',
    location: 'Shelf B1',
  },
];

export const channelLabels: Record<InventoryChannel, string> = {
  shipping: 'Delivery',
  pickup: 'Store pickup',
  counter: 'In-store sale',
};

export interface FulfillmentSample {
  orderId?: string;
  reference: string;
  channel: InventoryChannel;
  sku: string;
  itemName?: string;
  quantity: number;
  state: string;
  /** How the order is paid and where that stands, for server orders. */
  payment?: string;
  /** A PromptPay QR not yet paid: the order cannot be handed over. */
  awaitingPromptPay?: boolean;
  /** Delivered cash on delivery whose cash staff have not confirmed yet. */
  awaitingCash?: boolean;
}
export const fulfillmentSamples: readonly FulfillmentSample[] = [
  {
    reference: 'PO-1042',
    channel: 'shipping',
    sku: 'AZUL-EN-B',
    quantity: 2,
    state: 'Awaiting dispatch',
  },
  {
    reference: 'PO-1043',
    channel: 'pickup',
    sku: 'CATAN-TH-B',
    quantity: 1,
    state: 'Awaiting collection',
  },
  {
    reference: 'PO-1044',
    channel: 'counter',
    sku: 'PAND-EN-B',
    quantity: 1,
    state: 'Sale completed',
  },
];

function languageLabel(code: string): string {
  const lower = code.trim().toLowerCase();
  if (lower === 'en') return 'English';
  if (lower === 'th') return 'Thai';
  return code.toUpperCase();
}

/** Live editions have no SKU; a short form of the edition ID identifies the row. */
const shortId = (id: string) => id.slice(0, 8).toUpperCase();

/**
 * Sale stock rows for a branch's live editions. The API reports one held count
 * for placed orders, so it is not split by channel, and branches keep no reorder
 * threshold.
 */
export function toRetailStockRows(
  branchGames: readonly BranchGame[],
): RetailStockSample[] {
  return branchGames.flatMap((offering) =>
    offering.editions.map((edition) => ({
      editionId: edition.editionId,
      gameId: offering.game.id,
      sku: shortId(edition.editionId),
      name: offering.game.name,
      edition: `${edition.editionKind === 'EXPANSION' ? 'Expansion' : 'Base game'} · ${languageLabel(edition.languageCode)}`,
      onHand: edition.saleOnHand,
      shippingHeld: 0,
      pickupHeld: edition.saleHeld,
      reorderAt: null,
    })),
  );
}

/** A tracked rental copy, described only by what the server reports. */
export function toRentalCopyRow(
  copy: RentalCopy,
  gameName: string,
): RentalCopySample {
  const outOfService = copy.operationalStatus !== 'USABLE';
  const state: RentalCopySample['state'] = outOfService
    ? 'Maintenance'
    : copy.state === 'AVAILABLE'
      ? 'On shelf'
      : 'At table';
  const location = outOfService
    ? copy.reason || 'Out of service'
    : copy.state === 'RESERVED'
      ? 'Held for a reservation'
      : copy.state === 'IN_USE'
        ? 'In play'
        : 'Available';
  return {
    id: copy.id,
    gameId: copy.gameId,
    sku: shortId(copy.gameId),
    assetCode: copy.assetCode,
    name: gameName,
    state,
    location,
  };
}

export function toFulfillmentRow(order: Order): FulfillmentSample {
  const channel: InventoryChannel =
    order.source === 'STAFF_COUNTER'
      ? 'counter'
      : order.fulfillmentMethod === 'SHIPPING'
        ? 'shipping'
        : 'pickup';
  const [first, ...rest] = order.items;
  const state =
    order.status === 'FULFILLED'
      ? 'Sale completed'
      : order.status === 'CANCELLED'
        ? 'Cancelled'
        : channel === 'shipping'
          ? 'Awaiting dispatch'
          : 'Awaiting collection';
  return {
    orderId: order.id,
    reference: shortId(order.id),
    channel,
    sku: first ? shortId(first.editionId) : '',
    itemName: first
      ? `${first.gameName}${rest.length ? ` and ${rest.length} more` : ''}`
      : undefined,
    quantity: order.items.reduce((sum, item) => sum + item.quantity, 0),
    state,
    payment: paymentState(order.payment),
    awaitingPromptPay:
      order.status === 'PLACED' &&
      order.payment.method === 'PROMPTPAY' &&
      order.payment.status === 'PENDING',
    awaitingCash:
      order.status === 'FULFILLED' &&
      order.payment.method === 'COD' &&
      order.payment.status === 'PENDING',
  };
}

const paymentMethodLabels: Record<Order['payment']['method'], string> = {
  CARD: 'Card',
  COUNTER: 'Counter',
  PROMPTPAY: 'PromptPay',
  COD: 'Cash on delivery',
};

function paymentState(payment: Order['payment']): string {
  const method = paymentMethodLabels[payment.method];
  if (payment.status === 'CAPTURED') return `${method} · Paid`;
  if (payment.status === 'VOIDED') return `${method} · Not charged`;
  if (payment.status === 'AUTHORIZED') return `${method} · Authorized`;
  if (payment.method === 'PROMPTPAY') return `${method} · Awaiting payment`;
  if (payment.method === 'COD') return `${method} · Awaiting cash`;
  return `${method} · Due at handover`;
}

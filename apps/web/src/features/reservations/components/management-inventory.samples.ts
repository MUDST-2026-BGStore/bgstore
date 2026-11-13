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
  reorderAt: number;
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

const gameCodePrefixes: Record<string, string> = {
  azul: 'AZUL',
  catan: 'CATAN',
  wingspan: 'WING',
  codenames: 'CODE',
  pandemic: 'PAND',
  cascadia: 'CASC',
  'love letter': 'LOVE',
};

function gamePrefix(name: string): string {
  const lower = name.trim().toLowerCase();
  if (gameCodePrefixes[lower]) return gameCodePrefixes[lower];
  return (
    name
      .replace(/[^a-zA-Z0-9]/g, '')
      .slice(0, 4)
      .toUpperCase() || 'GAME'
  );
}

function languageLabel(code: string): string {
  const lower = code.trim().toLowerCase();
  if (lower === 'en') return 'English';
  if (lower === 'th') return 'Thai';
  return code.toUpperCase();
}

export function editionSkuForGame(
  gameName: string,
  languageCode: string,
  editionKind = 'BASE_GAME',
): string {
  const prefix = gamePrefix(gameName);
  const lang = languageCode.trim().toUpperCase() || 'EN';
  const kind = editionKind === 'EXPANSION' ? 'X' : 'B';
  return `${prefix}-${lang}-${kind}`;
}

export function rentalSkuForGame(gameName: string): string {
  return `PZ-${gamePrefix(gameName)}-RE`;
}

export function toRetailStockSamples(
  branchGames: readonly BranchGame[],
): RetailStockSample[] {
  const rows: RetailStockSample[] = [];
  for (const offering of branchGames) {
    for (const edition of offering.editions ?? []) {
      const sku = editionSkuForGame(
        offering.game.name,
        edition.languageCode,
        edition.editionKind,
      );
      const sample = retailStockSamples.find((s) => s.sku === sku);
      const kindLabel =
        edition.editionKind === 'EXPANSION' ? 'Expansion' : 'Base game';
      rows.push({
        editionId: edition.editionId,
        gameId: offering.game.id,
        sku,
        name: offering.game.name,
        edition: `${kindLabel} · ${languageLabel(edition.languageCode)}`,
        onHand: edition.saleOnHand,
        shippingHeld: 0,
        pickupHeld: edition.saleHeld,
        reorderAt: sample?.reorderAt ?? 2,
      });
    }
  }
  return rows;
}

export function toRentalCopySample(
  copy: RentalCopy,
  gameName: string,
  tableLocation?: string | null,
): RentalCopySample {
  const state: RentalCopySample['state'] =
    copy.state === 'AVAILABLE'
      ? 'On shelf'
      : copy.state === 'IN_USE' || copy.state === 'RESERVED'
        ? 'At table'
        : 'Maintenance';
  const location =
    state === 'Maintenance'
      ? copy.reason || 'Out-of-service check'
      : state === 'At table'
        ? tableLocation ||
          (copy.state === 'RESERVED' ? 'Reserved session' : 'Active table')
        : copy.reason || 'Shelf';
  return {
    id: copy.id,
    gameId: copy.gameId,
    sku: rentalSkuForGame(gameName),
    assetCode: copy.assetCode,
    name: gameName,
    state,
    location,
  };
}

export function toFulfillmentSample(order: Order): FulfillmentSample {
  const channel: InventoryChannel =
    order.source === 'STAFF_COUNTER'
      ? 'counter'
      : order.fulfillmentMethod === 'SHIPPING'
        ? 'shipping'
        : 'pickup';
  const first = order.items[0];
  const sku = first
    ? editionSkuForGame(first.gameName, first.editionLanguage, 'BASE_GAME')
    : order.id;
  const quantity = order.items.reduce((sum, item) => sum + item.quantity, 0);
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
    reference: order.id.startsWith('PO-')
      ? order.id
      : `PO-${order.id.slice(0, 8).toUpperCase()}`,
    channel,
    sku,
    itemName: first?.gameName,
    quantity,
    state,
  };
}

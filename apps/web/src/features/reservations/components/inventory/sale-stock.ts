import type { RetailStockSample } from '../management-inventory.samples';

export type StockStatus =
  'In stock' | 'Low stock' | 'Fully reserved' | 'Out of stock';
export interface SaleStockItem {
  id: string;
  sku: string;
  name: string;
  description: string;
  category: string;
  unit: string;
  onHand: number;
  reserved: number;
  available: number;
  /** Null when the branch has no reorder threshold for this item. */
  reorderPoint: number | null;
  status: StockStatus;
  tone: 'success' | 'warning' | 'danger';
  type: 'retail';
  source: RetailStockSample;
}

export function stockStatus(
  onHand: number,
  reserved: number,
  reorderPoint: number | null,
): StockStatus {
  if (onHand === 0) return 'Out of stock';
  if (onHand - reserved <= 0) return 'Fully reserved';
  if (reorderPoint !== null && onHand - reserved <= reorderPoint)
    return 'Low stock';
  return 'In stock';
}

export function saleStockItems(
  retail: readonly RetailStockSample[],
): SaleStockItem[] {
  return retail.map((source) => {
    const reserved = source.shippingHeld + source.pickupHeld;
    const status = stockStatus(source.onHand, reserved, source.reorderAt);
    return {
      id: `retail:${source.editionId ?? source.sku}`,
      sku: source.sku,
      name: source.name,
      description: source.edition,
      category: 'Retail games',
      unit: 'copies',
      onHand: source.onHand,
      reserved,
      available: source.onHand - reserved,
      reorderPoint: source.reorderAt,
      status,
      tone:
        status === 'Out of stock'
          ? 'danger'
          : status === 'In stock'
            ? 'success'
            : 'warning',
      type: 'retail' as const,
      source,
    };
  });
}

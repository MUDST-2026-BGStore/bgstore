import type { RetailStockSample } from '../management-inventory.samples';
import type { FoodDrinkStockItem } from '../../management/pantry.samples';

export type StockStatus =
  | 'In stock'
  | 'Low stock'
  | 'Fully reserved'
  | 'Out of stock';
export type SaleStockItem = {
  id: string;
  sku: string;
  name: string;
  description: string;
  category: string;
  unit: string;
  onHand: number;
  reserved: number;
  available: number;
  reorderPoint: number;
  status: StockStatus;
  tone: 'success' | 'warning' | 'danger';
} & (
  | { type: 'retail'; source: RetailStockSample }
  | { type: 'food'; source: FoodDrinkStockItem }
);

export function stockStatus(
  onHand: number,
  reserved: number,
  reorderPoint: number,
): StockStatus {
  if (onHand === 0) return 'Out of stock';
  if (onHand - reserved <= 0) return 'Fully reserved';
  if (onHand - reserved <= reorderPoint) return 'Low stock';
  return 'In stock';
}

const foodCategories: Record<FoodDrinkStockItem['category'], string> = {
  beverages: 'Beverages',
  snacks: 'Snacks & Fries',
  tea_coffee: 'Tea & Coffee',
  bakery_desserts: 'Bakery',
};

// Both saleable product types use one balance vocabulary. Pantry orders deduct
// on-hand stock immediately (useWorkspacePantry); they don't create reservations.
export function saleStockItems(
  retail: readonly RetailStockSample[],
  food: readonly FoodDrinkStockItem[],
): SaleStockItem[] {
  const rows = [
    ...retail.map((source) => ({
      id: `retail:${source.sku}`,
      sku: source.sku,
      name: source.name,
      description: source.edition,
      category: 'Retail games',
      unit: 'copies',
      onHand: source.onHand,
      reserved: source.shippingHeld + source.pickupHeld,
      reorderPoint: source.reorderAt,
      type: 'retail' as const,
      source,
    })),
    ...food.map((source) => ({
      id: `food:${source.id}`,
      sku: source.id.toUpperCase(),
      name: source.name,
      description: source.nameTh,
      category: foodCategories[source.category],
      unit: source.unit,
      onHand: source.currentStock,
      reserved: 0,
      reorderPoint: source.lowStockThreshold,
      type: 'food' as const,
      source,
    })),
  ];
  return rows.map((row) => {
    const status = stockStatus(row.onHand, row.reserved, row.reorderPoint);
    return {
      ...row,
      available: row.onHand - row.reserved,
      status,
      tone:
        status === 'Out of stock'
          ? 'danger'
          : status === 'In stock'
            ? 'success'
            : 'warning',
    };
  });
}

import fc from 'fast-check';
import { expect, it } from 'vitest';
import type { RetailStockSample } from '../management-inventory.samples';
import { saleStockItems } from './sale-stock';

const row: fc.Arbitrary<RetailStockSample> = fc
  .record({
    onHand: fc.integer({ min: 0, max: 500 }),
    shippingHeld: fc.integer({ min: 0, max: 200 }),
    pickupHeld: fc.integer({ min: 0, max: 200 }),
    reorderAt: fc.option(fc.integer({ min: 0, max: 50 }), { nil: null }),
  })
  // Held units are part of on-hand stock.
  .filter((r) => r.shippingHeld + r.pickupHeld <= r.onHand)
  .map((r) => ({
    ...r,
    sku: 'SKU-1',
    name: 'Game',
    edition: 'Base game · English',
  }));

it('derives availability and status consistently from the balances', () => {
  fc.assert(
    fc.property(fc.array(row, { maxLength: 20 }), (rows) => {
      for (const item of saleStockItems(rows)) {
        expect(item.available).toBe(item.onHand - item.reserved);
        expect(item.available).toBeGreaterThanOrEqual(0);
        expect(item.status === 'Out of stock').toBe(item.onHand === 0);
        if (item.onHand > 0)
          expect(item.status === 'Fully reserved').toBe(item.available === 0);
        // Without a reorder point an item is never reported as low.
        if (item.reorderPoint === null)
          expect(item.status).not.toBe('Low stock');
        expect(item.tone === 'success').toBe(item.status === 'In stock');
      }
    }),
  );
});

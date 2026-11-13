// Sample data only. TODO: replace retailStockSamples/rentalCopySamples/fulfillmentSamples with live branch inventory endpoints and delete these arrays.
export type InventoryChannel = 'shipping' | 'pickup' | 'counter';
export interface RetailStockSample {
  sku: string;
  name: string;
  edition: string;
  onHand: number;
  shippingHeld: number;
  pickupHeld: number;
  reorderAt: number;
}

export const retailStockSamples: readonly RetailStockSample[] = [
  { sku: 'AZUL-EN-B', name: 'Azul', edition: 'Base game · English', onHand: 12, shippingHeld: 3, pickupHeld: 2, reorderAt: 3 },
  { sku: 'CATAN-TH-B', name: 'Catan', edition: 'Base game · Thai', onHand: 5, shippingHeld: 0, pickupHeld: 3, reorderAt: 3 },
  { sku: 'WING-EN-B', name: 'Wingspan', edition: 'Base game · English', onHand: 4, shippingHeld: 2, pickupHeld: 2, reorderAt: 2 },
  { sku: 'CODE-EN-B', name: 'Codenames', edition: 'Base game · English', onHand: 0, shippingHeld: 0, pickupHeld: 0, reorderAt: 2 },
  { sku: 'PAND-EN-B', name: 'Pandemic', edition: 'Base game · English', onHand: 8, shippingHeld: 1, pickupHeld: 0, reorderAt: 2 },
];

export interface RentalCopySample {
  sku: string;
  assetCode: string;
  name: string;
  state: 'On shelf' | 'At table' | 'Maintenance';
  location: string;
}
export const rentalCopySamples: readonly RentalCopySample[] = [
  { sku: 'PZ-AZUL-RE', assetCode: 'AZ-C01', name: 'Azul', state: 'At table', location: 'Table 1' },
  { sku: 'PZ-AZUL-RE', assetCode: 'AZ-C02', name: 'Azul', state: 'On shelf', location: 'Shelf A2' },
  { sku: 'PZ-WING-RE', assetCode: 'WG-C01', name: 'Wingspan', state: 'At table', location: 'Table 2' },
  { sku: 'PZ-CATAN-RE', assetCode: 'CT-C01', name: 'Catan', state: 'Maintenance', location: 'Missing-piece check' },
  { sku: 'PZ-CODE-RE', assetCode: 'CN-C01', name: 'Codenames', state: 'On shelf', location: 'Shelf B1' },
];

export const channelLabels: Record<InventoryChannel, string> = {
  shipping: 'Delivery', pickup: 'Store pickup', counter: 'In-store sale',
};

interface FulfillmentSample {
  reference: string;
  channel: InventoryChannel;
  sku: string;
  quantity: number;
  state: string;
}
export const fulfillmentSamples: readonly FulfillmentSample[] = [
  { reference: 'PO-1042', channel: 'shipping', sku: 'AZUL-EN-B', quantity: 2, state: 'Awaiting dispatch' },
  { reference: 'PO-1043', channel: 'pickup', sku: 'CATAN-TH-B', quantity: 1, state: 'Awaiting collection' },
  { reference: 'PO-1044', channel: 'counter', sku: 'PAND-EN-B', quantity: 1, state: 'Sale completed' },
];

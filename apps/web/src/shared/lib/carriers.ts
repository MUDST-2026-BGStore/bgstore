import type { OrderCarrier } from '@theparlor/api-client';

/** Parcel carriers staff can dispatch with; keys are the API enum values. */
export const CARRIER_NAMES: Record<OrderCarrier, string> = {
  THAILAND_POST: 'Thailand Post',
  KERRY: 'Kerry Express (KEX)',
  FLASH: 'Flash Express',
  JT: 'J&T Express',
};

export const carrierOptions = (
  Object.entries(CARRIER_NAMES) as [OrderCarrier, string][]
).map(([value, label]) => ({ value, label }));

/** Copies text to the clipboard; clipboard access can be denied, so this never throws. */
export async function copyText(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    // The text stays visible on screen to copy by hand.
  }
}

import { describe, expect, it } from 'vitest';
import { formatReservedSlot } from './display';

describe('formatReservedSlot', () => {
  it('writes the Bangkok date in the Thai calendar with the time range', () => {
    expect(
      formatReservedSlot({
        startsAt: '2026-09-08T05:00:00Z',
        endsAt: '2026-09-08T06:00:00Z',
      }),
    ).toBe('08/09/2569 (12:00–13:00)');
  });

  it('uses Bangkok time even when the API answers in another offset', () => {
    expect(
      formatReservedSlot({
        startsAt: '2026-09-07T23:30:00Z',
        endsAt: '2026-09-08T01:00:00Z',
      }),
    ).toBe('08/09/2569 (06:30–08:00)');
  });

  it('writes midnight as 00:00 rather than 24:00', () => {
    expect(
      formatReservedSlot({
        startsAt: '2026-09-08T15:00:00Z',
        endsAt: '2026-09-08T17:00:00Z',
      }),
    ).toBe('08/09/2569 (22:00–00:00)');
  });
});

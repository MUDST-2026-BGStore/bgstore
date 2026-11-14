import { describe, expect, it } from 'vitest';
import {
  BRANCH_TIME_ZONE,
  PRE_ORDER_WINDOW_DAYS,
  branchDate,
  branchInstant,
  branchTime,
  canPreOrder,
  preOrderPreview,
} from './booking';

describe('branch-local booking times', () => {
  it('reads a date and time on the branch clock, whatever the browser zone', () => {
    expect(BRANCH_TIME_ZONE).toBe('Asia/Bangkok');
    expect(branchInstant('2026-11-20', '14:00')).toBe(
      '2026-11-20T07:00:00.000Z',
    );
    expect(branchInstant('2026-11-21', '01:30')).toBe(
      '2026-11-20T18:30:00.000Z',
    );
    expect(branchInstant('2026-11-20', '')).toBe('');
    expect(branchInstant('not-a-date', '10:00')).toBe('');
    // Found by the property test: impossible dates must not roll over.
    expect(branchInstant('2000-04-31', '00:00')).toBe('');
    expect(branchInstant('2026-02-29', '10:00')).toBe('');
    expect(branchInstant('2028-02-29', '24:00')).toBe('');
  });

  it('formats an instant back into branch date and time', () => {
    const instant = new Date('2026-11-20T18:30:00Z');
    expect(branchDate(instant)).toBe('2026-11-21');
    expect(branchTime(instant)).toBe('01:30');
  });
});

describe('food pre-orders', () => {
  const now = new Date('2026-11-13T03:00:00Z');

  it('are offered only inside the card authorization window', () => {
    const edge = new Date(
      now.getTime() + PRE_ORDER_WINDOW_DAYS * 86_400_000,
    ).toISOString();
    expect(canPreOrder(edge, now)).toBe(true);
    expect(
      canPreOrder(new Date(Date.parse(edge) + 60_000).toISOString(), now),
    ).toBe(false);
    expect(canPreOrder('', now)).toBe(false);
  });

  it('preview the server discount: 10% of the subtotal, rounded half up', () => {
    expect(preOrderPreview(9500)).toEqual({
      subtotalMinor: 9500,
      discountMinor: 950,
      totalMinor: 8550,
    });
    expect(preOrderPreview(5).discountMinor).toBe(1);
    expect(preOrderPreview(4).discountMinor).toBe(0);
  });
});

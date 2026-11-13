import { describe, expect, it } from 'vitest';
import {
  BRANCH_TIME_ZONE,
  PRE_ORDER_WINDOW_DAYS,
  addBranchDays,
  branchDate,
  formatBranchDay,
  formatBranchTimeRange,
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
  });

  it('formats an instant back into branch date and time', () => {
    const instant = new Date('2026-11-20T18:30:00Z');
    expect(branchDate(instant)).toBe('2026-11-21');
    expect(branchTime(instant)).toBe('01:30');
  });

  it('moves between branch calendar days across month and year ends', () => {
    expect(addBranchDays('2026-11-30', 1)).toBe('2026-12-01');
    expect(addBranchDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addBranchDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(addBranchDays('', 1)).toBe('');
  });

  it('displays the visit on the branch clock, including past midnight', () => {
    // 16:00Z is 23:00 in Bangkok; two hours later is 01:00 the next day.
    expect(
      formatBranchTimeRange('2026-11-20T16:00:00Z', '2026-11-20T18:00:00Z'),
    ).toBe('23:00–01:00');
    // 20:00Z on the 19th is already the 20th in Bangkok.
    expect(formatBranchDay('2026-11-19T20:00:00Z', 'en-US')).toBe(
      'Friday, November 20, 2026',
    );
    expect(formatBranchDay('nonsense', 'en-US')).toBe('');
    expect(formatBranchTimeRange('', '2026-11-20T18:00:00Z')).toBe('');
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

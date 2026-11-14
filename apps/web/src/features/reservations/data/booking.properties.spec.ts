// Property-based checks: each invariant runs against many generated inputs.
import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import {
  branchDate,
  branchInstant,
  branchTime,
  canPreOrder,
  plusHours,
  preOrderPreview,
  PRE_ORDER_WINDOW_DAYS,
} from './booking';

const pad = (value: number) => String(value).padStart(2, '0');
const dateText = fc
  .record({
    year: fc.integer({ min: 2000, max: 2100 }),
    month: fc.integer({ min: 1, max: 12 }),
    day: fc.integer({ min: 1, max: 31 }),
  })
  .map(({ year, month, day }) => `${year}-${pad(month)}-${pad(day)}`);
const timeText = fc
  .record({
    hour: fc.integer({ min: 0, max: 23 }),
    minute: fc.integer({ min: 0, max: 59 }),
  })
  .map(({ hour, minute }) => `${pad(hour)}:${pad(minute)}`);
const instant = fc.date({
  min: new Date('2000-01-01T00:00:00Z'),
  max: new Date('2100-01-01T00:00:00Z'),
  noInvalidDate: true,
});

describe('branch clock', () => {
  it('reads back exactly the date and time it was given, or rejects it', () => {
    fc.assert(
      fc.property(dateText, timeText, (date, time) => {
        const iso = branchInstant(date, time);
        // Calendar-impossible dates such as 2026-02-31 must not roll over.
        if (!iso) return;
        const at = new Date(iso);
        expect(branchDate(at)).toBe(date);
        expect(branchTime(at)).toBe(time);
      }),
    );
  });

  it('maps every instant to a branch date and time that lead back to its minute', () => {
    fc.assert(
      fc.property(instant, (at) => {
        const minute = new Date(Math.floor(at.getTime() / 60_000) * 60_000);
        expect(branchInstant(branchDate(at), branchTime(at))).toBe(
          minute.toISOString(),
        );
      }),
    );
  });

  it('adds hours without drifting', () => {
    fc.assert(
      fc.property(instant, fc.integer({ min: 0, max: 72 }), (at, hours) => {
        const later = plusHours(at.toISOString(), hours);
        expect(Date.parse(later) - at.getTime()).toBe(hours * 3_600_000);
      }),
    );
  });
});

describe('pre-order window', () => {
  it('allows exactly the visits that start within the window', () => {
    fc.assert(
      fc.property(
        instant,
        fc.integer({ min: -10 * 86_400_000, max: 30 * 86_400_000 }),
        (now, offset) => {
          const startsAt = new Date(now.getTime() + offset).toISOString();
          expect(canPreOrder(startsAt, now)).toBe(
            offset <= PRE_ORDER_WINDOW_DAYS * 86_400_000,
          );
        },
      ),
    );
  });
});

describe('pre-order price preview', () => {
  it('takes ten percent rounded half up, like the server, and never goes negative', () => {
    fc.assert(
      fc.property(fc.integer({ min: 0, max: 100_000_000 }), (subtotal) => {
        const preview = preOrderPreview(subtotal);
        const exactTenths = subtotal; // ten percent, in tenths of a satang
        const expected =
          Math.floor(exactTenths / 10) + (exactTenths % 10 >= 5 ? 1 : 0);
        expect(preview.discountMinor).toBe(expected);
        expect(preview.totalMinor).toBe(subtotal - preview.discountMinor);
        expect(preview.totalMinor).toBeGreaterThanOrEqual(0);
        expect(preview.totalMinor).toBeLessThanOrEqual(subtotal);
      }),
    );
  });
});

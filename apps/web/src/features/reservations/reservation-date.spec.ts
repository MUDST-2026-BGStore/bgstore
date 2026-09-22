import { describe, expect, it } from 'vitest';
import {
  createReservationDateRange,
  offsetReservationDate,
} from './reservation-date';

describe('reservation date window', () => {
  it('offsets days on the Bangkok calendar, not the browser one', () => {
    // 23:05 UTC is already the next day in Bangkok, so "tomorrow" there is the
    // day after the browser's tomorrow. The API validates in Bangkok time.
    const lateUtc = new Date('2026-09-22T23:05:00Z');

    expect(offsetReservationDate(lateUtc, 0)).toBe('2026-09-23');
    expect(offsetReservationDate(lateUtc, 1)).toBe('2026-09-24');
  });

  it('agrees with the browser calendar while Bangkok is on the same day', () => {
    const middayUtc = new Date('2026-09-22T15:30:00Z');

    expect(offsetReservationDate(middayUtc, 0)).toBe('2026-09-22');
    expect(offsetReservationDate(middayUtc, 1)).toBe('2026-09-23');
  });

  it('spans the sixty-day booking window the API allows', () => {
    const range = createReservationDateRange(new Date('2026-09-22T15:30:00Z'));

    expect(range.min).toBe('2026-09-22');
    expect(range.max).toBe('2026-11-21');
  });

  it('rolls over month and year boundaries', () => {
    expect(offsetReservationDate(new Date('2026-12-31T10:00:00Z'), 1)).toBe(
      '2027-01-01',
    );
    expect(offsetReservationDate(new Date('2026-01-01T10:00:00Z'), -1)).toBe(
      '2025-12-31',
    );
  });
});

import { describe, expect, it } from 'vitest';
import {
  EXPIRY_WARNING_SECONDS,
  billableHoursFor,
  computeSessionTiming,
  formatClock,
  formatStoreTime,
  serverClockOffsetMs,
} from './session-timing';

// 13:00-15:00 Bangkok (UTC+7) is 06:00-08:00Z.
const CHECKED_IN = '2026-05-01T13:10:00+07:00';
const END = '2026-05-01T08:00:00Z';
const at = (iso: string) => Date.parse(iso);

describe('computeSessionTiming', () => {
  it('keeps the booked end when the guest arrives late', () => {
    const t = computeSessionTiming({
      checkedInAt: CHECKED_IN,
      scheduledEnd: END,
      serverNowMs: at('2026-05-01T15:00:00+07:00'),
    });
    expect(t.elapsedSeconds / 60).toBe(110);
    expect(t.remainingSeconds).toBe(0);
    expect(t.billableHours).toBe(2);
    expect(t.ended).toBe(true);
    expect(t.completed).toBe(false);
  });

  it('counts elapsed and remaining time mid-session', () => {
    const t = computeSessionTiming({
      checkedInAt: CHECKED_IN,
      scheduledEnd: END,
      serverNowMs: at('2026-05-01T14:10:00+07:00'),
    });
    expect(t.elapsedSeconds).toBe(3600);
    expect(t.remainingSeconds).toBe(3000);
    expect(t.billableHours).toBe(1);
    expect(t.ended).toBe(false);
  });

  it('bills a started hour, minimum one, as checkout does', () => {
    expect(billableHoursFor(0)).toBe(1);
    expect(billableHoursFor(3600)).toBe(1);
    expect(billableHoursFor(3601)).toBe(2);
  });

  it('keeps counting and billing past the scheduled end until the session closes', () => {
    const t = computeSessionTiming({
      checkedInAt: CHECKED_IN,
      scheduledEnd: END,
      serverNowMs: at('2026-05-01T17:30:00+07:00'),
    });
    expect(t.elapsedSeconds / 60).toBe(260);
    expect(t.remainingSeconds).toBe(0);
    expect(t.overtimeSeconds / 60).toBe(150);
    expect(t.billableHours).toBe(5);
    expect(t.ended).toBe(true);
    expect(t.completed).toBe(false);
  });

  it('never goes negative before check-in time is reached', () => {
    const t = computeSessionTiming({
      checkedInAt: CHECKED_IN,
      scheduledEnd: END,
      serverNowMs: at('2026-05-01T13:00:00+07:00'),
    });
    expect(t.elapsedSeconds).toBe(0);
  });

  it('warns inside the threshold only, and not once ended or completed', () => {
    const base = { checkedInAt: CHECKED_IN, scheduledEnd: END };
    const end = at(END);
    const warn = (nowMs: number, completedAt?: string) =>
      computeSessionTiming({ ...base, serverNowMs: nowMs, completedAt })
        .expiringSoon;
    expect(warn(end - (EXPIRY_WARNING_SECONDS + 1) * 1000)).toBe(false);
    expect(warn(end - EXPIRY_WARNING_SECONDS * 1000)).toBe(true);
    expect(warn(end - 1000)).toBe(true);
    expect(warn(end)).toBe(false);
    expect(warn(end - 60_000, '2026-05-01T14:30:00+07:00')).toBe(false);
  });

  it('uses the early checkout time and shows zero remaining once completed', () => {
    const t = computeSessionTiming({
      checkedInAt: CHECKED_IN,
      scheduledEnd: END,
      completedAt: '2026-05-01T13:40:00+07:00',
      serverNowMs: at('2026-05-01T14:30:00+07:00'),
    });
    expect(t.elapsedSeconds / 60).toBe(30);
    expect(t.remainingSeconds).toBe(0);
    expect(t.completed).toBe(true);
    expect(t.ended).toBe(true);
  });
});

describe('server clock', () => {
  it('offsets a wrong device clock onto server time', () => {
    // Device is an hour fast: it received the response at 14:00 while the server said 13:00.
    const offset = serverClockOffsetMs(
      '2026-05-01T13:00:00+07:00',
      at('2026-05-01T14:00:00+07:00'),
    );
    expect(offset).toBe(-3_600_000);
  });
});

describe('store time display', () => {
  it('shows Asia/Bangkok from UTC and offset timestamps alike', () => {
    expect(formatStoreTime('2026-05-01T06:10:00Z')).toBe('13:10');
    expect(formatStoreTime('2026-05-01T13:10:00+07:00')).toBe('13:10');
    expect(formatStoreTime('2026-05-01T01:10:00-05:00')).toBe('13:10');
    expect(formatStoreTime('2026-05-01T17:00:00Z')).toBe('00:00');
  });

  it('formats clocks', () => {
    expect(formatClock(65)).toBe('1:05');
    expect(formatClock(6600)).toBe('1:50:00');
    expect(formatClock(-5)).toBe('0:00');
  });
});

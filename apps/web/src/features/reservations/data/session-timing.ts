// Pure timing for an in-store play session. Every input is an absolute instant
// (ISO-8601 with an offset or Z), never a wall-clock string, so the result does
// not depend on the device time zone. The scheduled end is fixed by the booking:
// a late arrival does not move it and nothing is accrued after it.

/** The store's time zone; staff see times here whatever the device uses. */
export const STORE_TIME_ZONE = 'Asia/Bangkok';

/** Warn staff this long before the scheduled end. */
export const EXPIRY_WARNING_SECONDS = 10 * 60;

const HOUR_SECONDS = 3600;

export interface SessionTimingInput {
  /** Actual check-in instant from the server. */
  checkedInAt: string;
  scheduledEnd: string;
  /** Server checkout instant, present once the session is completed. */
  completedAt?: string | null;
  /** The current instant on the server's clock, in epoch milliseconds. */
  serverNowMs: number;
}

export interface SessionTiming {
  elapsedSeconds: number;
  remainingSeconds: number;
  /** Table time the server bills if the displayed play time is final: started hours, minimum one. */
  billableHours: number;
  /** True once the scheduled end has passed or the session completed. */
  ended: boolean;
  completed: boolean;
  /** Active, not yet ended, and within the warning window. */
  expiringSoon: boolean;
}

/** Server rule (RentalPricingPolicy.billableHours): max(1, ceil(elapsed / 1h)). */
export function billableHoursFor(elapsedSeconds: number): number {
  return Math.max(1, Math.ceil(elapsedSeconds / HOUR_SECONDS));
}

export function computeSessionTiming(
  input: SessionTimingInput,
  warningSeconds = EXPIRY_WARNING_SECONDS,
): SessionTiming {
  const checkedIn = Date.parse(input.checkedInAt);
  const scheduledEnd = Date.parse(input.scheduledEnd);
  const completed = Boolean(input.completedAt);
  const checkout = input.completedAt
    ? Date.parse(input.completedAt)
    : input.serverNowMs;
  const effectiveEnd = Math.min(checkout, scheduledEnd);
  const elapsedSeconds = Math.max(
    0,
    Math.floor((effectiveEnd - checkedIn) / 1000),
  );
  const remainingSeconds = completed
    ? 0
    : Math.max(0, Math.ceil((scheduledEnd - input.serverNowMs) / 1000));
  return {
    elapsedSeconds,
    remainingSeconds,
    billableHours: billableHoursFor(elapsedSeconds),
    ended: completed || input.serverNowMs >= scheduledEnd,
    completed,
    expiringSoon:
      !completed && remainingSeconds > 0 && remainingSeconds <= warningSeconds,
  };
}

/**
 * Offset to add to the device clock to get server time. Anchored to when the
 * response was received, so a slow response never makes the server clock look
 * ahead; the error is at most the response's transit time.
 */
export function serverClockOffsetMs(
  serverNow: string,
  receivedAtMs: number,
): number {
  return Date.parse(serverNow) - receivedAtMs;
}

/** HH:mm in the store's time zone, from any ISO instant. */
export function formatStoreTime(iso: string): string {
  if (!iso) return '';
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: STORE_TIME_ZONE,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(iso));
}

/** H:MM:SS or M:SS countdown/elapsed text. */
export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(sec)}` : `${m}:${pad(sec)}`;
}

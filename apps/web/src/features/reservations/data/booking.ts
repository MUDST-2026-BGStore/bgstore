/**
 * Booking rules the customer pages preview. The server owns every one of them and
 * rechecks them on booking; these only keep the wizard honest before it asks.
 */

/** Every branch keeps Bangkok time, which has no daylight saving. */
export const BRANCH_TIME_ZONE = 'Asia/Bangkok';
const BRANCH_UTC_OFFSET_HOURS = 7;

/**
 * Food can be pre-ordered only for visits starting within this many days, because
 * the card is authorized at booking and captured at check-in, and online card
 * authorizations expire after about a week. Matches ReservationPreOrder.WINDOW.
 */
export const PRE_ORDER_WINDOW_DAYS = 6;

/** Percentage taken off every pre-order. Matches ReservationPreOrder.DISCOUNT_PERCENT. */
export const PRE_ORDER_DISCOUNT_PERCENT = 10;

/** The instant of a branch-local date (YYYY-MM-DD) and time (HH:mm), or '' if incomplete. */
export function branchInstant(date: string, time: string): string {
  const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  const clock = /^(\d{2}):(\d{2})$/.exec(time);
  if (!day || !clock) return '';
  const millis = Date.UTC(
    Number(day[1]),
    Number(day[2]) - 1,
    Number(day[3]),
    Number(clock[1]) - BRANCH_UTC_OFFSET_HOURS,
    Number(clock[2]),
  );
  return Number.isNaN(millis) ? '' : new Date(millis).toISOString();
}

function branchParts(instant: Date) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: BRANCH_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(instant);
  const part = (type: string) =>
    parts.find((p) => p.type === type)?.value ?? '';
  return part;
}

/** The branch-local calendar date (YYYY-MM-DD) of an instant. */
export function branchDate(instant: Date): string {
  const part = branchParts(instant);
  return `${part('year')}-${part('month')}-${part('day')}`;
}

/** The branch-local wall-clock time (HH:mm) of an instant. */
export function branchTime(instant: Date): string {
  const part = branchParts(instant);
  return `${part('hour')}:${part('minute')}`;
}

/** A branch-local date (YYYY-MM-DD) shifted by whole days. */
export function addBranchDays(date: string, days: number): string {
  const instant = branchInstant(date, '12:00');
  return instant
    ? branchDate(new Date(Date.parse(instant) + days * 86_400_000))
    : '';
}

/** The visit's day on the branch calendar, e.g. "Friday 20 November 2026". */
export function formatBranchDay(iso: string, locale: string): string {
  const instant = Date.parse(iso);
  if (Number.isNaN(instant)) return '';
  return new Intl.DateTimeFormat(locale, {
    timeZone: BRANCH_TIME_ZONE,
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(instant);
}

/** Branch wall-clock start and end, e.g. "23:00–01:00" for a visit past midnight. */
export function formatBranchTimeRange(
  startsAt: string,
  endsAt: string,
): string {
  const start = Date.parse(startsAt);
  const end = Date.parse(endsAt);
  if (Number.isNaN(start) || Number.isNaN(end)) return '';
  return `${branchTime(new Date(start))}–${branchTime(new Date(end))}`;
}

/** Adds whole hours to an ISO instant. */
export function plusHours(iso: string, hours: number): string {
  return iso ? new Date(Date.parse(iso) + hours * 3_600_000).toISOString() : '';
}

/** Whether a visit starting at startsAt may carry a food pre-order if booked now. */
export function canPreOrder(startsAt: string, now = new Date()): boolean {
  const start = Date.parse(startsAt);
  if (Number.isNaN(start)) return false;
  return start <= now.getTime() + PRE_ORDER_WINDOW_DAYS * 86_400_000;
}

/** What the server will charge for a pre-order subtotal, in minor units. */
export function preOrderPreview(subtotalMinor: number) {
  const discountMinor = Math.floor(
    (subtotalMinor * PRE_ORDER_DISCOUNT_PERCENT + 50) / 100,
  );
  return {
    subtotalMinor,
    discountMinor,
    totalMinor: subtotalMinor - discountMinor,
  };
}

/** THB for display: whole baht without decimals, satang when present. */
export function formatBaht(amountMinor: number): string {
  const baht = amountMinor / 100;
  return `฿${baht.toLocaleString('en-US', {
    minimumFractionDigits: Number.isInteger(baht) ? 0 : 2,
    maximumFractionDigits: 2,
  })}`;
}

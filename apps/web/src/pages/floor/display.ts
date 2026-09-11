import type { ReservedSlot } from '../../generated/api/types.gen';

const STORE_TIME_ZONE = 'Asia/Bangkok';

// The design writes slots as "08/09/2569 (12:00–13:00)": day-first, Thai
// (Buddhist) calendar year, store-local 24-hour time. That is how staff read
// dates in the store, so it does not follow the UI language.
const dateParts = new Intl.DateTimeFormat('th-TH-u-ca-buddhist-nu-latn', {
  timeZone: STORE_TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

const timeFormat = new Intl.DateTimeFormat('en-GB', {
  timeZone: STORE_TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** One reserved slot the way the floor overview's Reserved Slots column writes it. */
export function formatReservedSlot(slot: ReservedSlot): string {
  const startsAt = new Date(slot.startsAt);
  const parts = dateParts.formatToParts(startsAt);
  const partOf = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? '';

  const date = `${partOf('day')}/${partOf('month')}/${partOf('year')}`;
  const times = `${timeFormat.format(startsAt)}–${timeFormat.format(new Date(slot.endsAt))}`;

  return `${date} (${times})`;
}

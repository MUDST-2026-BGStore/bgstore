/**
 * The store's booking window is defined in Bangkok time, and the API validates
 * a reservation date against `LocalDate.now(Asia/Bangkok)`. Deriving the window
 * from the browser's calendar instead would make "tomorrow" mean a different
 * day for anyone whose timezone is behind Bangkok, and the API would reject it.
 */
const BANGKOK = 'Asia/Bangkok';

/** The calendar date in Bangkok at the given instant, as year, month and day. */
const bangkokCalendarDate = (value: Date) => {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: BANGKOK,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(value);
  const part = (type: 'year' | 'month' | 'day') =>
    Number(parts.find((entry) => entry.type === type)?.value ?? Number.NaN);
  return { year: part('year'), month: part('month'), day: part('day') };
};

/** Shifts the Bangkok calendar date by whole days and returns an ISO date. */
export const offsetReservationDate = (value: Date, days: number) => {
  const { year, month, day } = bangkokCalendarDate(value);
  return new Date(Date.UTC(year, month - 1, day + days))
    .toISOString()
    .slice(0, 10);
};

export const createReservationDateRange = (today = new Date()) => ({
  min: offsetReservationDate(today, 0),
  max: offsetReservationDate(today, 60),
});

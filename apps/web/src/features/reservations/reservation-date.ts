const toLocalIsoDate = (value: Date) => {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

export const offsetReservationDate = (value: Date, days: number) => {
  const result = new Date(value);
  result.setHours(12, 0, 0, 0);
  result.setDate(result.getDate() + days);
  return toLocalIsoDate(result);
};

export const createReservationDateRange = (today = new Date()) => ({
  min: offsetReservationDate(today, 0),
  max: offsetReservationDate(today, 60),
});

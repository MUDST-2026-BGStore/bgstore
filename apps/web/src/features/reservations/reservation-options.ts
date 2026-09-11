import type {
  ReservationBranchOption,
  ReservationClientOption,
  ReservationTableOption,
} from './reservation-types';

export const PARTY_SIZE_OPTIONS = Array.from(
  { length: 13 },
  (_, index) => index + 1,
);

export const TIME_OPTIONS = [
  '09:00',
  '10:00',
  '11:00',
  '12:00',
  '13:00',
  '14:00',
  '15:00',
  '16:00',
  '17:00',
  '18:00',
  '19:00',
  '20:00',
];

// UI fixtures are isolated here so API-backed queries can replace them later.
export const RESERVATION_CLIENTS: ReservationClientOption[] = [
  {
    id: 'client-local',
    displayName: 'Local Client · 081-234-5678',
    firstName: 'Local',
    lastName: 'Client',
    nickname: 'Local',
    phone: '081-234-5678',
  },
  {
    id: 'client-somsri',
    displayName: 'Somsri Jaidee · 089-555-0123',
    firstName: 'Somsri',
    lastName: 'Jaidee',
    nickname: 'Som',
    phone: '089-555-0123',
  },
];

export const RESERVATION_BRANCHES: ReservationBranchOption[] = [
  { id: 'central-rama-1', name: 'Central — Rama I Road, Pathum Wan' },
  { id: 'ari', name: 'Ari — Phahonyothin Road, Phaya Thai' },
];

// Placeholder availability is kept outside components for a future API query.
export const RESERVATION_TABLES: ReservationTableOption[] = [
  { id: 1, seats: 2, reserved: false },
  { id: 3, seats: 4, reserved: true },
  { id: 5, seats: 6, reserved: false },
  { id: 7, seats: 4, reserved: false },
  { id: 9, seats: 6, reserved: false },
  { id: 11, seats: 8, reserved: false },
  { id: 2, seats: 4, reserved: false },
  { id: 4, seats: 4, reserved: false },
  { id: 6, seats: 6, reserved: false },
  { id: 8, seats: 8, reserved: false },
  { id: 10, seats: 4, reserved: true },
  { id: 12, seats: 6, reserved: false },
];

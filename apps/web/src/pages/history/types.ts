export type ReservationStatus = 'Reserved' | 'Completed' | 'Cancelled';

export type HistoryTab = 'All' | 'Reserved' | 'Completed' | 'Cancelled';

export interface ReservationRecord {
  id: string;
  title: string;
  date: string;
  timeSlot: string;
  partySize: number;
  tableId: number;
  tableName: string;
  seats: number;
  ratePerHour: number;
  status: ReservationStatus;
  customerName: string;
  phoneNumber: string;
  checkInTime: string;
  actualCheckOut: string;
  overtimeMinutes: number;
  totalPrice: number;
  canCancel: boolean;
  thumbnailUrl?: string;
}

export interface PaginatedReservations {
  items: ReservationRecord[];
  totalElements: number;
  totalPages: number;
  page: number;
  pageSize: number;
}

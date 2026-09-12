export type ReservationStep = 1 | 2 | 3 | 4;

export interface ReservationClientDraft {
  clientSearch: string;
  clientId: string | null;
  branchId: string;
  firstName: string;
  lastName: string;
  nickname: string;
  phone: string;
}

export interface ReservationDraft {
  client: ReservationClientDraft;
  date: string;
  startTime: string;
  endTime: string;
  partySize: number;
  tableId: number | null;
}

export interface ReservationTableOption {
  id: number;
  seats: number;
  reserved: boolean;
}

export interface ReservationClientOption {
  id: string;
  displayName: string;
  firstName: string;
  lastName: string;
  nickname: string;
  phone: string;
}

export interface ReservationBranchOption {
  id: string;
  name: string;
}

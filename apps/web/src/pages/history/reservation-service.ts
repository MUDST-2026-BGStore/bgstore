import { reactive } from 'vue';
import { initialReservations } from './mock-data';
import type {
  HistoryTab,
  PaginatedReservations,
  ReservationRecord,
} from './types';

class ReservationService {
  private reservations: ReservationRecord[] = reactive(
    JSON.parse(JSON.stringify(initialReservations)),
  );

  async getReservations(
    options: {
      tab?: HistoryTab;
      page?: number;
      pageSize?: number;
      simulateDelay?: number;
    } = {},
  ): Promise<PaginatedReservations> {
    const {
      tab = 'All',
      page = 1,
      pageSize = 4,
      simulateDelay = 150,
    } = options;

    if (simulateDelay > 0) {
      await new Promise((resolve) => setTimeout(resolve, simulateDelay));
    }

    const filtered =
      tab === 'All'
        ? this.reservations
        : this.reservations.filter((r) => r.status === tab);

    const totalElements = filtered.length;
    const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));
    const safePage = Math.min(Math.max(1, page), totalPages);

    const startIndex = (safePage - 1) * pageSize;
    const items = filtered.slice(startIndex, startIndex + pageSize);

    return {
      items,
      totalElements,
      totalPages,
      page: safePage,
      pageSize,
    };
  }

  async getReservationById(
    id: string,
    simulateDelay = 100,
  ): Promise<ReservationRecord | undefined> {
    if (simulateDelay > 0) {
      await new Promise((resolve) => setTimeout(resolve, simulateDelay));
    }
    const found = this.reservations.find((r) => r.id === id);
    return found ? { ...found } : undefined;
  }

  async cancelReservation(
    id: string,
    simulateDelay = 150,
  ): Promise<ReservationRecord> {
    if (simulateDelay > 0) {
      await new Promise((resolve) => setTimeout(resolve, simulateDelay));
    }

    const reservation = this.reservations.find((r) => r.id === id);
    if (!reservation) {
      throw new Error(`Reservation with ID ${id} not found.`);
    }

    if (reservation.status !== 'Reserved' || !reservation.canCancel) {
      throw new Error(
        `Reservation ${id} cannot be cancelled in status ${reservation.status}.`,
      );
    }

    reservation.status = 'Cancelled';
    reservation.canCancel = false;

    return { ...reservation };
  }

  reset(): void {
    this.reservations.splice(
      0,
      this.reservations.length,
      ...JSON.parse(JSON.stringify(initialReservations)),
    );
  }
}

export const reservationService = new ReservationService();

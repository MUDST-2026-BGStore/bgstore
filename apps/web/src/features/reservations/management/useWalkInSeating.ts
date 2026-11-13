import { computed, ref, shallowRef, type Ref } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import {
  createStaffReservation,
  getBranchAvailability,
  type Reservation,
  type TableAvailability,
  type WalkInReservationRequest,
} from '@theparlor/api-client';
import { httpStatus, problemMessage } from '../../../shared/lib/api-errors';
import { formatBaht } from '../data/booking';

const HOUR_MS = 3_600_000;

/** Query keys whose data a seating changes. */
export const WALK_IN_AFFECTED_QUERIES = [
  ['listBranchReservations'],
  ['branchTables'],
  ['walkInAvailability'],
] as const;

const REASON_TEXT: Record<
  NonNullable<TableAvailability['unavailableReasons']>[number],
  string
> = {
  OCCUPIED: 'In play',
  RESERVED: 'Booked during this time',
  OUT_OF_SERVICE: 'Out of service',
  CAPACITY_EXCEEDED: 'Too small for the party',
  OUTSIDE_OPENING_HOURS: 'Branch closes before this ends',
};

/** Why the server says a table cannot take this walk-in, in the server's order. */
export function unavailableText(table: TableAvailability): string {
  const reasons = (table.unavailableReasons ?? []).map((r) => REASON_TEXT[r]);
  return reasons.length ? reasons.join(' · ') : 'Unavailable';
}

/** Gateway failures may hide a committed seating; every other HTTP answer is definite. */
function isDefinite(error: unknown): boolean {
  const status = httpStatus(error);
  return status !== undefined && ![502, 503, 504].includes(status);
}

export function walkInFailureMessage(error: unknown): string {
  const status = httpStatus(error);
  const detail = problemMessage(error);
  if (status === 409)
    return `${detail ?? 'The table is no longer free'}. Choose another table.`;
  if (status === 404)
    return 'That table no longer exists. Choose another table.';
  if (status === 400)
    return `${detail ?? 'The walk-in details were rejected'}. Check and try again.`;
  if (status === 401 || status === 403)
    return 'You cannot seat guests at this branch.';
  if (!isDefinite(error))
    return 'No answer from the server. Retry to finish seating; it will not seat the party twice.';
  return 'The server could not seat this party. Nothing was saved; try again.';
}

interface Attempt {
  key: string;
  signature: string;
  request: WalkInReservationRequest;
}

/**
 * Seats a walk-in through the server only. Table choices come from the branch's
 * availability for "now until the chosen end", the choice is rechecked before
 * sending, and the party is created and checked in by one idempotent request.
 * Nothing is written locally: the floor changes only when refetched server state
 * shows the seated visit.
 */
export function useWalkInSeating(branchId: Ref<string>) {
  const queryClient = useQueryClient();
  const open = ref(false);
  const tableId = ref('');
  const partySize = ref(2);
  const durationHours = ref(2);
  const guestName = ref('');
  const phone = ref('');
  const error = ref('');
  const submitting = ref(false);
  // A request whose outcome is unknown is retried with the same key and body.
  const attempt = shallowRef<Attempt | null>(null);

  const availability = useQuery({
    queryKey: ['walkInAvailability', branchId, partySize, durationHours],
    queryFn: ({ signal }) => {
      const now = Date.now();
      return getBranchAvailability(
        branchId.value,
        {
          startsAt: new Date(now).toISOString(),
          endsAt: new Date(now + durationHours.value * HOUR_MS).toISOString(),
          partySize: partySize.value,
        },
        undefined,
        signal,
      );
    },
    enabled: computed(
      () => open.value && !!branchId.value && partySize.value >= 1,
    ),
    staleTime: 0,
    refetchInterval: 30_000,
  });

  const tables = computed<TableAvailability[]>(
    () => availability.data.value?.tables ?? [],
  );
  const selected = computed(() =>
    tables.value.find((table) => table.tableId === tableId.value),
  );
  const tableOptions = computed(() =>
    tables.value.map((table) => ({
      id: table.tableId,
      label: table.label,
      description: `${table.capacity} seats · ${formatBaht(table.hourlyRate.amountMinor)}/hr\n${
        table.available ? 'Free' : unavailableText(table)
      }`,
      disabled: !table.available,
    })),
  );

  function signature() {
    return JSON.stringify([
      branchId.value,
      tableId.value,
      partySize.value,
      durationHours.value,
      guestName.value.trim(),
      phone.value.trim(),
    ]);
  }
  const replaying = computed(() => attempt.value?.signature === signature());

  /** What stops the selected table from being seated, if anything. */
  const blockedReason = computed(() => {
    if (replaying.value) return '';
    if (availability.isError.value)
      return 'Table availability could not be loaded.';
    if (availability.isSuccess.value && tables.value.length === 0)
      return 'No tables are set up for this branch.';
    if (selected.value && !selected.value.available)
      return `${selected.value.label}: ${unavailableText(selected.value)}.`;
    return '';
  });
  const canSubmit = computed(
    () =>
      !submitting.value &&
      (replaying.value || selected.value?.available === true),
  );

  function openFor(table?: { id: string; seats: number }) {
    // A seating whose answer never arrived may have happened; show what the server has.
    if (attempt.value) void refreshAuthoritativeState();
    attempt.value = null;
    tableId.value = table?.id ?? '';
    if (table) partySize.value = Math.max(1, table.seats);
    guestName.value = '';
    phone.value = '';
    error.value = '';
    open.value = true;
  }

  function selectTable(id: string) {
    tableId.value = id;
    const table = tables.value.find((t) => t.tableId === id);
    if (table && partySize.value > table.capacity)
      partySize.value = table.capacity;
  }

  function buildRequest(): WalkInReservationRequest {
    const displayName = guestName.value.trim() || 'Walk-in guests';
    const contact = phone.value.trim();
    return {
      source: 'WALK_IN',
      checkIn: true,
      tableId: tableId.value,
      partySize: partySize.value,
      endsAt: new Date(
        Date.now() + durationHours.value * HOUR_MS,
      ).toISOString(),
      games: [],
      customer: {
        kind: 'GUEST',
        displayName,
        ...(contact ? { phone: contact } : {}),
      },
    };
  }

  function refreshAuthoritativeState() {
    return Promise.all(
      WALK_IN_AFFECTED_QUERIES.map((queryKey) =>
        queryClient.invalidateQueries({ queryKey: [...queryKey] }),
      ),
    );
  }

  /** Resolves to the seated visit, or null when nothing was (known to be) seated. */
  async function submit(): Promise<Reservation | null> {
    if (submitting.value) return null;
    submitting.value = true;
    error.value = '';
    try {
      const sig = signature();
      let current = attempt.value?.signature === sig ? attempt.value : null;
      if (!current) {
        // The dialog may have been open for a while; ask the server again.
        const fresh = await availability.refetch();
        const table = fresh.data?.tables.find(
          (t) => t.tableId === tableId.value,
        );
        if (!table) {
          error.value = fresh.isError
            ? 'Table availability could not be loaded. Try again.'
            : 'Choose a table.';
          return null;
        }
        if (!table.available) {
          error.value = `${table.label} cannot be seated: ${unavailableText(table)}.`;
          return null;
        }
        current = {
          key: crypto.randomUUID(),
          signature: sig,
          request: buildRequest(),
        };
        attempt.value = current;
      }
      const branch = branchId.value;
      try {
        const seated = await createStaffReservation(branch, current.request, {
          headers: { 'Idempotency-Key': current.key },
        });
        attempt.value = null;
        open.value = false;
        await refreshAuthoritativeState();
        return seated;
      } catch (failure) {
        if (isDefinite(failure)) attempt.value = null;
        error.value = walkInFailureMessage(failure);
        void refreshAuthoritativeState();
        return null;
      }
    } finally {
      submitting.value = false;
    }
  }

  return {
    open,
    tableId,
    partySize,
    durationHours,
    guestName,
    phone,
    error,
    submitting,
    availability,
    tableOptions,
    blockedReason,
    canSubmit,
    openFor,
    selectTable,
    submit,
  };
}

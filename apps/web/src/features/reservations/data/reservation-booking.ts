/**
 * The customer booking request and its submission. The server owns availability,
 * prices, the pre-order discount and the booking itself; this module only builds the
 * request, checks it against the latest advisory availability and submits it safely.
 */
import {
  createMyReservation,
  createPaymentToken,
  type BranchAvailability,
  type BranchGame,
  type CreatePaymentTokenRequest,
  type CreateReservationRequest,
  type PaymentToken,
  type Reservation,
} from '@theparlor/api-client';
import { httpStatus, problemCode } from '../../../shared/lib/api-errors';
import type { FoodMenuItem, FoodOrderItem } from '../types';

/** Everything the customer chose. Equal intents produce equal requests. */
export interface BookingIntent {
  branchId: string;
  tableId: string;
  startsAt: string;
  endsAt: string;
  partySize: number;
  games: { gameId: string; quantity: number }[];
  notes?: string;
  /** Empty for a table-only booking, which needs no card. */
  preOrder: { menuItemId: string; quantity: number }[];
}

const byKey =
  <T>(key: (value: T) => string) =>
  (a: T, b: T) =>
    key(a).localeCompare(key(b));

function normalized(intent: BookingIntent): BookingIntent {
  return {
    ...intent,
    notes: intent.notes?.trim() || undefined,
    games: intent.games
      .filter((game) => game.quantity > 0)
      .sort(byKey((game) => game.gameId)),
    preOrder: intent.preOrder
      .filter((line) => line.quantity > 0)
      .sort(byKey((line) => line.menuItemId)),
  };
}

/** Identifies what is being booked, independent of the payment token. */
export function intentFingerprint(intent: BookingIntent): string {
  const {
    branchId,
    tableId,
    startsAt,
    endsAt,
    partySize,
    games,
    notes,
    preOrder,
  } = normalized(intent);
  return JSON.stringify([
    branchId,
    tableId,
    startsAt,
    endsAt,
    partySize,
    games,
    notes ?? null,
    preOrder,
  ]);
}

/** The API request for an intent. A pre-order is sent only with a payment token. */
export function reservationRequest(
  intent: BookingIntent,
  paymentToken?: string,
): CreateReservationRequest {
  const { tableId, startsAt, endsAt, partySize, games, notes, preOrder } =
    normalized(intent);
  const request: CreateReservationRequest = {
    tableId,
    startsAt,
    endsAt,
    partySize,
    games,
  };
  if (notes) request.notes = notes;
  if (preOrder.length) {
    if (!paymentToken) throw new Error('A pre-order needs a payment token');
    request.preOrder = { items: preOrder, paymentToken };
  }
  return request;
}

/** A game the customer can reserve at the chosen branch and time. */
export interface BookableGame {
  gameId: string;
  name: string;
  imageUrl?: string;
  minPlayers: number;
  maxPlayers: number;
  /** Copies free for the whole interval, as of the availability snapshot. */
  available: number;
  hourlyRateMinor?: number;
}

/**
 * Games with copies free for the whole interval, named from the branch library.
 * Availability decides what is offered; the library only supplies names, so a game
 * missing from either is not offered.
 */
export function bookableGames(
  availability: BranchAvailability | undefined,
  library: BranchGame[],
): BookableGame[] {
  if (!availability?.withinOpeningHours) return [];
  const shelf = new Map(library.map((entry) => [entry.game.id, entry]));
  return availability.games
    .flatMap((game): BookableGame[] => {
      const entry = shelf.get(game.gameId);
      if (!entry || !game.rentalEnabled || game.availableQuantity < 1)
        return [];
      return [
        {
          gameId: game.gameId,
          name: entry.game.name,
          imageUrl: entry.game.images?.[0]?.url,
          minPlayers: entry.game.minPlayers,
          maxPlayers: entry.game.maxPlayers,
          available: game.availableQuantity,
          hourlyRateMinor: game.hourlyRate?.amountMinor,
        },
      ];
    })
    .sort(byKey((game) => game.name));
}

/** Game selections limited to what is still offered; drops and reductions are reported. */
export function reconcileGames(
  selections: Record<string, number>,
  offered: BookableGame[],
): { selections: Record<string, number>; changed: string[] } {
  const next: Record<string, number> = {};
  const changed: string[] = [];
  for (const [gameId, quantity] of Object.entries(selections)) {
    if (quantity < 1) continue;
    const game = offered.find((candidate) => candidate.gameId === gameId);
    const allowed = Math.min(quantity, game?.available ?? 0);
    if (allowed > 0) next[gameId] = allowed;
    if (allowed !== quantity) changed.push(gameId);
  }
  return { selections: next, changed };
}

/**
 * Pre-order lines checked against a branch menu. Lines take the menu's current
 * item; items no longer served are removed and quantities above availability are
 * reduced, so the customer can see what changed.
 */
export function reconcilePreOrder(
  lines: FoodOrderItem[],
  menu: FoodMenuItem[],
): {
  lines: FoodOrderItem[];
  removed: FoodOrderItem[];
  reduced: FoodOrderItem[];
} {
  const removed: FoodOrderItem[] = [];
  const reduced: FoodOrderItem[] = [];
  const next: FoodOrderItem[] = [];
  for (const line of lines) {
    const item = menu.find((candidate) => candidate.id === line.item.id);
    const limit = Math.min(50, item?.available ?? 50);
    if (!item || limit < 1) {
      removed.push(line);
      continue;
    }
    const quantity = Math.min(line.quantity, limit);
    if (quantity < line.quantity) reduced.push({ item, quantity });
    next.push({ item, quantity });
  }
  return { lines: next, removed, reduced };
}

export type AvailabilityProblem =
  | { kind: 'closed' }
  | { kind: 'table' }
  | { kind: 'game'; gameId: string; available: number };

/** Why the latest availability no longer fits the intent; empty if it still does. */
export function availabilityProblems(
  availability: BranchAvailability,
  intent: BookingIntent,
): AvailabilityProblem[] {
  if (!availability.withinOpeningHours) return [{ kind: 'closed' }];
  const problems: AvailabilityProblem[] = [];
  const table = availability.tables.find((t) => t.tableId === intent.tableId);
  if (!table?.available || table.capacity < intent.partySize)
    problems.push({ kind: 'table' });
  for (const selection of intent.games) {
    const game = availability.games.find((g) => g.gameId === selection.gameId);
    const available = game?.rentalEnabled === true ? game.availableQuantity : 0;
    if (available < selection.quantity)
      problems.push({ kind: 'game', gameId: selection.gameId, available });
  }
  return problems;
}

/** Pre-order lines of the intent that the latest menu can no longer serve in full. */
export function preOrderShortfalls(
  menu: FoodMenuItem[],
  intent: BookingIntent,
): string[] {
  return intent.preOrder
    .filter((line) => {
      const item = menu.find((candidate) => candidate.id === line.menuItemId);
      return !item || Math.min(50, item.available ?? 50) < line.quantity;
    })
    .map((line) => line.menuItemId);
}

export interface CardForm {
  number: string;
  /** MM/YY */
  expiry: string;
  cvc: string;
  holder: string;
}

export type CardField = keyof CardForm;

/**
 * Card details as the tokenization request, or the fields that are plainly wrong.
 * The gateway does the real validation (Luhn, brand, expiry) when it tokenizes.
 */
export function cardRequest(
  form: CardForm,
  now = new Date(),
): { request: CreatePaymentTokenRequest } | { invalid: CardField[] } {
  const invalid: CardField[] = [];
  const digits = form.number.replace(/[\s-]/g, '');
  if (!/^\d{12,19}$/.test(digits)) invalid.push('number');
  const expiry = /^(\d{2})\s*\/\s*(\d{2})$/.exec(form.expiry.trim());
  const month = expiry ? Number(expiry[1]) : 0;
  const year = expiry ? 2000 + Number(expiry[2]) : 0;
  const thisMonth = now.getUTCFullYear() * 12 + now.getUTCMonth();
  if (month < 1 || month > 12 || year * 12 + month - 1 < thisMonth)
    invalid.push('expiry');
  if (!/^\d{3,4}$/.test(form.cvc)) invalid.push('cvc');
  if (!form.holder.trim()) invalid.push('holder');
  if (invalid.length) return { invalid };
  return {
    request: {
      number: digits,
      expMonth: month,
      expYear: year,
      cvc: form.cvc,
      holderName: form.holder.trim(),
    },
  };
}

export type BookingFailure =
  /** The card was declined; nothing was booked or charged. */
  | 'declined'
  /** The table, games, menu stock or branch changed; nothing was booked. */
  | 'unavailable'
  /** Card payments are unavailable; nothing was booked. */
  | 'gateway'
  /** The server rejected the request or the card details. */
  | 'invalid'
  | 'signed-out'
  /** The response was lost; the booking may or may not exist. */
  | 'unknown-outcome'
  /** An earlier attempt whose response was lost may have booked already. */
  | 'possibly-booked'
  /** The card is needed again to retry. */
  | 'card-required'
  | 'failed';

export class BookingError extends Error {
  constructor(
    readonly failure: BookingFailure,
    override readonly cause?: unknown,
  ) {
    super(`Booking failed: ${failure}`);
    this.name = 'BookingError';
  }
}

/**
 * A response that may never have come from the API, so the request may or may not
 * have been processed. A gateway Problem is a definite API answer; a bare 502 or 504
 * from a proxy is not.
 */
function outcomeUnknown(error: unknown): boolean {
  const status = httpStatus(error);
  if (status === undefined || status === 408 || status === 504) return true;
  return status === 502 && problemCode(error) !== 'PAYMENT_GATEWAY_ERROR';
}

function definiteFailure(error: unknown): BookingFailure {
  switch (httpStatus(error)) {
    case 400:
    case 422:
      return 'invalid';
    case 401:
      return 'signed-out';
    case 402:
      return 'declined';
    case 404:
    case 409:
      return 'unavailable';
    case 502:
      return 'gateway';
    default:
      return 'failed';
  }
}

/** Tokens are refused this close to expiry, so a slow request cannot outlive one. */
export const TOKEN_EXPIRY_MARGIN_MS = 60_000;

interface Attempt {
  key: string;
  fingerprint: string;
  /** The last request may have been processed; its key must not be abandoned. */
  outcomeUnknown: boolean;
  token?: PaymentToken;
}

export interface BookingSubmitterDeps {
  tokenize?: typeof createPaymentToken;
  create?: typeof createMyReservation;
  newKey?: () => string;
  now?: () => number;
}

/**
 * Submits one customer booking at a time with an Idempotency-Key per logical attempt.
 *
 * The server fingerprints the whole request, payment token included, and forgets a
 * key whenever the booking fails. So:
 * - After a definite answer nothing was booked. The key is kept only while the
 *   booking is unchanged, and the token is treated as spent because gateways consume
 *   it at authorization; the retry tokenizes the card again.
 * - After a lost response the booking may exist. The key is kept whatever changes.
 *   An identical retry reuses the token while it is comfortably unexpired, so the
 *   server replays the original result. Otherwise the card is tokenized again under
 *   the same key; if the first request did book, the different request is refused
 *   with 409 instead of creating a second booking.
 *
 * Card details are used only for tokenization and are never kept here.
 */
export function createBookingSubmitter(deps: BookingSubmitterDeps = {}) {
  const tokenize = deps.tokenize ?? createPaymentToken;
  const create = deps.create ?? createMyReservation;
  const newKey = deps.newKey ?? (() => crypto.randomUUID());
  const now = deps.now ?? Date.now;
  let attempt: Attempt | undefined;
  let busy = false;

  function usableToken(fingerprint: string): string | undefined {
    if (!attempt?.outcomeUnknown || attempt.fingerprint !== fingerprint)
      return undefined;
    const token = attempt.token;
    if (!token) return undefined;
    return Date.parse(token.expiresAt) - now() > TOKEN_EXPIRY_MARGIN_MS
      ? token.token
      : undefined;
  }

  async function submit(
    intent: BookingIntent,
    card?: CreatePaymentTokenRequest,
  ): Promise<Reservation> {
    if (busy) throw new Error('A booking is already being submitted');
    busy = true;
    try {
      const fingerprint = intentFingerprint(intent);
      if (
        !attempt ||
        (!attempt.outcomeUnknown && attempt.fingerprint !== fingerprint)
      )
        attempt = { key: newKey(), fingerprint, outcomeUnknown: false };
      const current = attempt;

      let paymentToken: string | undefined;
      if (intent.preOrder.some((line) => line.quantity > 0)) {
        paymentToken = usableToken(fingerprint);
        if (!paymentToken) {
          if (!card) throw new BookingError('card-required');
          let issued: PaymentToken;
          try {
            issued = await tokenize(card);
          } catch (error) {
            // Nothing was booked; the reservation request was never sent.
            const status = httpStatus(error);
            throw new BookingError(
              status === 400
                ? 'invalid'
                : status === 502
                  ? 'gateway'
                  : 'failed',
              error,
            );
          }
          current.token = issued;
          paymentToken = issued.token;
        }
      } else {
        current.token = undefined;
      }
      current.fingerprint = fingerprint;

      try {
        const reservation = await create(
          intent.branchId,
          reservationRequest(intent, paymentToken),
          { headers: { 'Idempotency-Key': current.key } },
        );
        attempt = undefined;
        return reservation;
      } catch (error) {
        if (outcomeUnknown(error)) {
          current.outcomeUnknown = true;
          throw new BookingError('unknown-outcome', error);
        }
        current.token = undefined;
        if (current.outcomeUnknown && httpStatus(error) === 409)
          // The key's first request may have booked. Keep the key so no retry
          // can book a second time; the customer checks My Reservations.
          throw new BookingError('possibly-booked', error);
        current.outcomeUnknown = false;
        throw new BookingError(definiteFailure(error), error);
      }
    } finally {
      busy = false;
    }
  }

  return {
    submit,
    /** Whether the last attempt may have booked; the UI should say so. */
    get outcomeUnknown() {
      return attempt?.outcomeUnknown ?? false;
    },
    /** Starts over after the customer has seen the result of an earlier booking. */
    reset() {
      attempt = undefined;
    },
  };
}

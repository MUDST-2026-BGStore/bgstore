import { ref, readonly } from 'vue';
import { isAxiosError } from 'axios';
import { me, type Profile } from '@theparlor/api-client';

export type AccountState =
  | { status: 'unknown' }
  | { status: 'missing' }
  | { status: 'ready'; account: Profile };

const state = ref<AccountState>({ status: 'unknown' });

let pending: Promise<AccountState> | null = null;
let generation = 0;

export function useAccount() {
  return readonly(state);
}

export function updateAccountProfile(account: Profile): void {
  if (state.value.status === 'ready' && state.value.account.id === account.id) {
    state.value = { status: 'ready', account };
  }
}

export function clearAccount(): void {
  generation++;
  pending = null;
  state.value = { status: 'unknown' };
}

export async function ensureAccount(): Promise<AccountState> {
  if (state.value.status !== 'unknown') {
    return state.value;
  }

  if (pending) return pending;

  const currentGeneration = generation;

  const request = (async (): Promise<AccountState> => {
    let result: AccountState;

    try {
      const account = await me();
      result = { status: 'ready', account };
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 404) {
        result = { status: 'missing' };
      } else {
        throw error;
      }
    }

    // Prevent stale requests from restoring cleared state.
    if (currentGeneration === generation) {
      state.value = result;
    }

    return currentGeneration === generation ? result : state.value;
  })();

  pending = request;

  try {
    return await request;
  } finally {
    if (pending === request) {
      pending = null;
    }
  }
}

export async function refreshAccount(): Promise<AccountState> {
  clearAccount();
  return ensureAccount();
}

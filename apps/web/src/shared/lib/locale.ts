import { readonly, ref, watch } from 'vue';
import { getMeQueryKey, updateMeSettings } from '@theparlor/api-client';
import { useAuth } from '@theparlor/auth';
import { updateAccountProfile, useAccount } from '@theparlor/account';
import { queryClient } from './api';

export type Locale = 'en-US' | 'th-TH';
type PendingLocale = { accountId: string; locale: Locale };
const localeKey = 'theparlor.locale';
const pendingKey = 'theparlor.locale.pending';
const isLocale = (value: unknown): value is Locale =>
  value === 'en-US' || value === 'th-TH';

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function write(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    /* Language switching still works when storage is unavailable. */
  }
}
function readPending(): PendingLocale | null {
  try {
    const value: unknown = JSON.parse(read(pendingKey) ?? 'null');
    if (
      value &&
      typeof value === 'object' &&
      'accountId' in value &&
      typeof value.accountId === 'string' &&
      'locale' in value &&
      isLocale(value.locale)
    ) {
      return { accountId: value.accountId, locale: value.locale };
    }
  } catch {
    /* Ignore invalid browser storage. */
  }
  return null;
}

const cached = read(localeKey);
const locale = ref<Locale>(
  isLocale(cached)
    ? cached
    : navigator.language.toLowerCase().startsWith('th')
      ? 'th-TH'
      : 'en-US',
);
const saveStatus = ref<'idle' | 'saving' | 'error'>('idle');
let pending = readPending();
let saving = false;
let selectedForSubject: string | null = null;

function currentAccount() {
  const account = useAccount().value;
  return useAuth().value.authenticated && account.status === 'ready'
    ? account.account
    : null;
}
function apply(value: Locale): void {
  locale.value = value;
  document.documentElement.lang = value;
  write(localeKey, value);
}
function rememberPending(value: PendingLocale | null): void {
  pending = value;
  write(pendingKey, value ? JSON.stringify(value) : null);
}

async function retrySave(): Promise<void> {
  const change = pending;
  if (saving || !change || currentAccount()?.id !== change.accountId) return;
  const subject = useAuth().value.subject;
  const stillCurrent = () =>
    useAuth().value.subject === subject &&
    currentAccount()?.id === change.accountId;
  saving = true;
  saveStatus.value = 'saving';
  try {
    const settings = await updateMeSettings({
      preferences: { preferredLocale: change.locale },
    });
    if (stillCurrent()) {
      await queryClient.cancelQueries({ queryKey: getMeQueryKey() });
      if (!stillCurrent()) return;
      const account = currentAccount();
      if (!account) return;
      const profile = {
        ...account,
        preferences: { ...account.preferences, ...settings.preferences },
      };
      updateAccountProfile(profile);
      queryClient.setQueryData(getMeQueryKey(), profile);
      if (pending === change) {
        rememberPending(null);
        saveStatus.value = 'idle';
      }
    }
  } catch {
    if (stillCurrent()) saveStatus.value = 'error';
  } finally {
    saving = false;
    // A newer selection waits for the previous request to finish.
    if (pending && pending !== change) void retrySave();
  }
}

function setLocale(value: Locale): void {
  apply(value);
  const account = currentAccount();
  if (account) {
    rememberPending({ accountId: account.id, locale: value });
    void retrySave();
  } else {
    selectedForSubject = useAuth().value.subject;
  }
}

/** Connect once at bootstrap; the returned stop function is useful in tests. */
export function initializeLocaleSync() {
  apply(locale.value);
  let boundAccount: string | null = null;
  let boundSubject: string | null = null;
  return watch(
    () => [useAuth().value.subject, currentAccount()?.id] as const,
    ([subject]) => {
      const account = currentAccount();
      if (!account) {
        boundAccount = null;
        saveStatus.value = 'idle';
        return;
      }
      if (boundAccount === account.id && boundSubject === subject) return;
      boundAccount = account.id;
      boundSubject = subject;
      if (selectedForSubject === subject) {
        if (saving || locale.value !== account.preferences.preferredLocale) {
          rememberPending({ accountId: account.id, locale: locale.value });
          void retrySave();
        } else if (pending?.accountId === account.id) {
          rememberPending(null);
        }
      } else if (pending?.accountId === account.id) {
        apply(pending.locale);
        void retrySave();
      } else if (isLocale(account.preferences.preferredLocale)) {
        apply(account.preferences.preferredLocale);
      }
      selectedForSubject = null;
    },
    { immediate: true, flush: 'post' },
  );
}

export function useLocale() {
  return {
    locale: readonly(locale),
    saveStatus: readonly(saveStatus),
    setLocale,
    retrySave,
  };
}

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { RouterLink, RouterView, useRoute } from 'vue-router';
import { ParlorShell, BLocaleSwitcher } from '@theparlor/ui';
import {
  StoreCartButton,
  StoreCartPanel,
  useStoreText,
} from '../../features/home';
import { useLocale } from '../../shared/lib/locale';
import { useAuthStore } from '../../shared/stores/auth';

const auth = useAuthStore();
const route = useRoute();
const { t } = useStoreText();
const { locale, setLocale, saveStatus, retrySave } = useLocale();
const accountRoot = ref<HTMLElement | null>(null);
const accountButton = ref<HTMLButtonElement | null>(null);
const accountPanel = ref<HTMLElement | null>(null);
const open = ref(false);

function close(restoreFocus = false) {
  open.value = false;
  if (restoreFocus) accountButton.value?.focus({ preventScroll: true });
}
async function show(focusLast = false) {
  open.value = true;
  await nextTick();
  const items = accountPanel.value?.querySelectorAll<HTMLElement>('a, button');
  if (open.value) items?.[focusLast ? items.length - 1 : 0]?.focus();
}
function move(event: KeyboardEvent) {
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  const items = Array.from(
    accountPanel.value?.querySelectorAll<HTMLElement>('a, button') ?? [],
  );
  const current = items.indexOf(document.activeElement as HTMLElement);
  const index =
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? items.length - 1
        : (current + (event.key === 'ArrowDown' ? 1 : -1) + items.length) %
          items.length;
  items[index]?.focus();
}
function leave(event: FocusEvent) {
  if (!accountRoot.value?.contains(event.relatedTarget as Node | null)) close();
}
function outside(event: PointerEvent) {
  if (!accountRoot.value?.contains(event.target as Node)) close();
}
function signOut() {
  close(true);
  void auth.logout();
}
watch(
  () => route.fullPath,
  () => close(),
);
watch(
  () => auth.authenticated,
  () => close(),
);
onMounted(() => document.addEventListener('pointerdown', outside));
onUnmounted(() => document.removeEventListener('pointerdown', outside));
</script>

<template>
  <ParlorShell
    class="customer-shell"
    sticky-header
    brand-href="/"
    :class="{
      'catalog-shell':
        route.name === 'HomeIndex' || route.name === 'GameProduct',
      'reservation-shell':
        route.name === 'CustomerReservePage' ||
        route.name === 'CustomerReservationsList',
    }"
  >
    <template #header-left>
      <div class="header-language">
        <BLocaleSwitcher
          :locale="locale"
          :label="t('Language')"
          @update:locale="setLocale"
        />
        <p
          v-if="saveStatus === 'saving'"
          class="language-feedback"
          role="status"
        >
          {{ t('Saving language…') }}
        </p>
        <div
          v-if="saveStatus === 'error'"
          class="language-feedback"
          role="alert"
        >
          {{
            t('Language changed on this device. Couldn’t save to your account.')
          }}
          <button type="button" @click="retrySave">{{ t('Retry') }}</button>
        </div>
      </div>
    </template>

    <template #header-right>
      <RouterLink
        v-if="auth.authenticated"
        to="/reservations"
        class="header-history-btn"
      >
        {{ t('My Reservations') }}
      </RouterLink>
      <RouterLink to="/reservations/new" class="header-reserve-btn">
        {{ t('Reserve Table') }}
      </RouterLink>
      <StoreCartButton />
      <div
        ref="accountRoot"
        class="account"
        @focusout="leave"
        @keydown.esc.stop.prevent="close(true)"
      >
        <button
          ref="accountButton"
          class="account-trigger"
          :class="{ 'is-open': open }"
          type="button"
          :aria-expanded="open"
          aria-controls="account-panel"
          :aria-label="t('Account')"
          @click="open ? close(true) : show()"
          @keydown.down.prevent="show()"
          @keydown.up.prevent="show(true)"
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            aria-hidden="true"
          >
            <circle cx="12" cy="8" r="3.5" />
            <path d="M5 21v-2a7 7 0 0 1 14 0v2" />
          </svg>
          <span class="account-label">{{ t('Account') }}</span>
          <svg
            class="chevron"
            width="10"
            height="6"
            viewBox="0 0 10 6"
            fill="none"
            aria-hidden="true"
          >
            <path d="m1 1 4 4 4-4" stroke="currentColor" stroke-width="1.25" />
          </svg>
        </button>
        <Transition name="account-menu">
          <nav
            v-if="open"
            id="account-panel"
            ref="accountPanel"
            class="account-popup"
            :aria-label="t('Your account')"
            @keydown="move"
          >
            <div class="account-heading">
              <span class="account-caption">{{
                t(auth.authenticated ? 'Signed in as' : 'Your account')
              }}</span>
              <span v-if="auth.authenticated" class="account-name">{{
                auth.displayName || auth.username
              }}</span>
              <span v-else class="account-description">{{
                t('Sign in to manage your profile.')
              }}</span>
            </div>
            <div class="account-options">
              <template v-if="auth.authenticated">
                <RouterLink :to="{ name: 'AuthProfile' }"
                  >{{ t('Your profile') }}
                  <span aria-hidden="true">↗</span></RouterLink
                >
                <RouterLink :to="{ name: 'AuthLogin' }"
                  >{{ t('Open your workspace') }}
                  <span aria-hidden="true">↗</span></RouterLink
                >
                <button type="button" class="sign-out" @click="signOut">
                  {{ t('Sign out') }} <span aria-hidden="true">→</span>
                </button>
              </template>
              <RouterLink v-else :to="{ name: 'AuthLogin' }"
                >{{ t('Sign in') }}
                <span aria-hidden="true">→</span></RouterLink
              >
            </div>
          </nav>
        </Transition>
      </div>
    </template>

    <RouterView />
    <StoreCartPanel />
  </ParlorShell>
</template>

<style scoped>
.catalog-shell {
  background: var(--color-parlor-page);
  background-image: none;
  color: var(--color-parlor-neutral-800);
  font-family: Arial, Helvetica, sans-serif;
}
.catalog-shell :deep(.felt-noise) {
  display: none;
}
/* One header for every customer page: green bar, gold rule, centered wordmark. */
.customer-shell :deep(> div.relative) {
  margin: 0;
  min-height: 100svh;
  border: 0;
}
.customer-shell :deep(> div.relative > header) {
  min-height: 64px;
  background: var(--color-parlor-green-800);
  border-bottom: 2px solid var(--color-parlor-gold-500);
  padding: 10px max(32px, calc((100vw - 1440px) / 2));
  font-family: 'Plus Jakarta Sans', Arial, sans-serif;
}
.catalog-shell :deep(> div.relative > header + div) {
  padding: 20px 32px 24px;
}
/* Reservation pages paint their own ivory page edge to edge. */
.reservation-shell {
  background: var(--color-parlor-ivory-100);
  background-image: none;
}
.reservation-shell :deep(.felt-noise) {
  display: none;
}
.reservation-shell :deep(> div.relative > header + div) {
  padding: 0;
}
.customer-shell
  :deep(> div.relative > header > :nth-child(2) > span:first-child),
.customer-shell
  :deep(> div.relative > header > :nth-child(2) > span:last-child) {
  display: inline;
  color: var(--color-parlor-gold-300);
  font-size: 13px;
}
.customer-shell
  :deep(> div.relative > header > :nth-child(2) > span:nth-child(2)) {
  color: var(--color-parlor-cream);
  font-size: 13px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}
.header-language {
  position: relative;
}
.language-feedback {
  position: absolute;
  z-index: 60;
  top: calc(100% + 8px);
  left: 0;
  width: 260px;
  padding: 12px;
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-800);
  font:
    12px/1.7 Arial,
    sans-serif;
  letter-spacing: normal;
  text-transform: none;
}
.language-feedback button {
  text-decoration: underline;
  cursor: pointer;
}
.account {
  position: relative;
}
.account-trigger {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 8px 12px;
  border: 1px solid transparent;
  border-bottom: 0;
  border-radius: 4px 4px 0 0;
  color: var(--color-parlor-cream);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: normal;
  text-transform: none;
  cursor: pointer;
  transition:
    background-color 150ms ease,
    color 150ms ease;
}
.account-trigger:hover {
  color: var(--color-parlor-white);
}
.account-trigger.is-open {
  z-index: 51;
  border-color: var(--color-parlor-neutral-300);
  background: var(--color-parlor-white);
  color: var(--color-parlor-green-900);
}
.account-trigger.is-open::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: var(--color-parlor-white);
  content: '';
}
.chevron {
  color: currentColor;
  opacity: 0.7;
  transition: transform 150ms ease;
}
.is-open .chevron {
  transform: rotate(180deg);
}
.account-popup {
  position: absolute;
  top: calc(100% - 1px);
  right: 0;
  z-index: 50;
  width: min(284px, calc(100vw - 64px));
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 4px 0 4px 4px;
  background: var(--color-parlor-white);
  color: var(--color-parlor-green-900);
  box-shadow: 0 8px 24px color-mix(in srgb, var(--color-parlor-green-900) 14%, transparent);
  transform-origin: top right;
}
.account-heading {
  display: grid;
  gap: 6px;
  padding: 20px 18px;
  border-bottom: 1px solid var(--color-parlor-neutral-300);
}
.account-caption {
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-parlor-neutral-600);
}
.account-name {
  overflow-wrap: anywhere;
  font-size: 15px;
}
.account-description {
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-parlor-neutral-600);
}
.account-options {
  padding: 4px;
}
.account-options a,
.account-options button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border-radius: 2px;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 140ms ease,
    color 140ms ease;
}
.account-options a:hover,
.account-options button:hover,
.account-options a:focus-visible,
.account-options button:focus-visible {
  background: var(--color-parlor-neutral-150);
}
.account-options span {
  color: var(--color-parlor-neutral-500);
}
.account-options .sign-out {
  border-top: 1px solid var(--color-parlor-neutral-300);
  margin-top: 4px;
  color: var(--color-parlor-danger-700);
}
a:focus-visible,
button:focus-visible {
  outline: 2px solid var(--color-parlor-gold-500);
  outline-offset: 2px;
}
.account-menu-enter-active,
.account-menu-leave-active {
  transition:
    opacity 160ms ease,
    transform 160ms cubic-bezier(0.2, 0.7, 0.2, 1);
}
.account-menu-leave-active {
  pointer-events: none;
}
.account-menu-enter-from,
.account-menu-leave-to {
  opacity: 0;
  transform: translateY(-5px) scale(0.985);
}
@media (max-width: 1024px) {
  .customer-shell :deep(> div.relative > header) {
    grid-template-columns: auto 1fr auto;
    padding: 10px 18px;
    gap: 12px;
  }
  .customer-shell :deep(> div.relative > header > :nth-child(3)) {
    gap: 8px;
    justify-content: flex-end;
  }
  .header-history-btn,
  .header-reserve-btn,
  .account-trigger {
    padding: 6px 10px;
    font-size: 12px;
  }
  .account-trigger {
    gap: 6px;
  }
}
@media (max-width: 700px) {
  .catalog-shell :deep(> div.relative > header + div) {
    padding: 18px 16px;
  }
  .customer-shell :deep(> div.relative > header) {
    grid-template-columns: auto minmax(0, 1fr) auto;
    padding: 12px;
    gap: 8px;
  }
  .customer-shell :deep(> div.relative > header > :nth-child(3)) {
    gap: 0;
  }
  .catalog-shell :deep(> div.relative > header > :nth-child(2)) {
    gap: 4px;
  }
  .catalog-shell
    :deep(> div.relative > header > :nth-child(2) > span:nth-child(2)) {
    font-size: 11px;
    letter-spacing: 0.08em;
  }
  .catalog-shell
    :deep(> div.relative > header > :nth-child(2) > span:first-child),
  .catalog-shell
    :deep(> div.relative > header > :nth-child(2) > span:last-child) {
    font-size: 10px;
  }
  .account-trigger {
    padding: 10px 6px;
  }
  .account-label,
  .account-trigger > .chevron {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .account-trigger,
  .chevron,
  .account-menu-enter-active,
  .account-menu-leave-active,
  .account-options a,
  .account-options button {
    transition: none;
  }
  .account-menu-enter-from,
  .account-menu-leave-to {
    transform: none;
  }
}
.header-history-btn,
.header-reserve-btn {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 8px 14px;
  border: 1px solid transparent;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: normal;
  text-transform: none;
  text-decoration: none;
  white-space: nowrap;
  transition:
    background-color 140ms ease,
    border-color 140ms ease,
    color 140ms ease;
}
.header-history-btn {
  background: transparent;
  border-color: color-mix(in srgb, var(--color-parlor-gold-500) 45%, transparent);
  color: var(--color-parlor-cream);
}
.header-history-btn:hover {
  background: color-mix(in srgb, var(--color-parlor-gold-500) 15%, transparent);
  border-color: var(--color-parlor-gold-500);
  color: var(--color-parlor-white);
}
.header-reserve-btn {
  background: var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
}
.header-reserve-btn:hover {
  background: var(--color-parlor-gold-300);
  color: var(--color-parlor-green-950);
}
@media (max-width: 600px) {
  .header-history-btn,
  .header-reserve-btn {
    min-height: 32px;
    padding: 6px 8px;
    font-size: 11px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .header-history-btn,
  .header-reserve-btn {
    transition: none;
  }
}
</style>

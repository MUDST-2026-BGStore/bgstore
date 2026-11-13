<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { RouterLink, RouterView, useRoute } from 'vue-router';
import { ParlorShell, BLocaleSwitcher } from '@theparlor/ui';
import {
  StoreCartButton,
  useStoreText,
  MyOrdersModal,
  useOrdersStore,
} from '../../features/home';
import { useLocale } from '../../shared/lib/locale';
import { useAuthStore } from '../../shared/stores/auth';
import { workspaceDestination } from '../router/access';

defineProps<{ hideHeaderSignOut?: boolean }>();

const auth = useAuthStore();
const ordersStore = useOrdersStore();
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
        route.name === 'HomeIndex' ||
        route.name === 'GameProduct' ||
        route.name === 'Cart' ||
        route.name === 'Checkout' ||
        route.name === 'BranchLocations',
      'cart-shell': route.name === 'Cart' || route.name === 'Checkout',
      'map-shell': route.name === 'BranchLocations',
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
                <RouterLink :to="{ name: workspaceDestination(auth.roles) }"
                  >{{ t('Open your workspace') }}
                  <span aria-hidden="true">↗</span></RouterLink
                >
                <RouterLink to="/reservations/new">{{
                  t('Reserve Table')
                }}</RouterLink>
                <RouterLink to="/reservations">{{
                  t('My Reservations')
                }}</RouterLink>
                <button
                  type="button"
                  @click="
                    close();
                    ordersStore.openOrdersModal();
                  "
                >
                  {{ t('My Orders') }}
                </button>
                <button
                  v-if="!hideHeaderSignOut && route.name !== 'WorkspaceIndex'"
                  type="button"
                  class="sign-out"
                  @click="signOut"
                >
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

    <slot><RouterView /></slot>
    <MyOrdersModal />
  </ParlorShell>
</template>

<style scoped>
.customer-shell :deep(.parlor-header) {
  background: #203c30;
  border-bottom: 2px solid #c6ad7a;
}

.catalog-shell {
  background: #fafbf9;
  background-image: none;
  color: #202e27;
  font-family: Arial, Helvetica, sans-serif;
}
.catalog-shell :deep(.felt-noise) {
  display: none;
}
.catalog-shell :deep(> div.relative) {
  margin: 0;
  min-height: 100svh;
  border: 0;
}
.catalog-shell :deep(> div.relative > header) {
  background: #203c30;
  border-bottom: 2px solid #c6ad7a;
  padding: 10px max(32px, calc((100vw - 1440px) / 2));
  font-family: 'Plus Jakarta Sans', Arial, sans-serif;
}
.catalog-shell :deep(> div.relative > header + div) {
  padding: 20px 32px 24px;
}
.catalog-shell.cart-shell {
  background: #f4f5f7;
}
.catalog-shell.cart-shell :deep(> div.relative > header + div) {
  padding: 0;
  background: #f4f5f7;
}
/* Branch map page: the map fills the viewport below the header, edge to edge. */
.catalog-shell.map-shell :deep(> div.relative) {
  height: 100svh;
}
.catalog-shell.map-shell :deep(> div.relative > header + div) {
  min-height: 0;
  padding: 0;
}
.catalog-shell .header-language {
  position: relative;
}
.language-feedback {
  position: absolute;
  z-index: 60;
  top: calc(100% + 8px);
  left: 0;
  width: 260px;
  padding: 12px;
  background: #fffaf1;
  border: 1px solid #c6ad7a;
  color: #243e32;
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
  gap: 10px;
  padding: 12px;
  border: 1px solid transparent;
  border-bottom: 0;
  border-radius: 4px 4px 0 0;
  color: #c6b58f;
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  cursor: pointer;
  transition:
    background-color 150ms ease,
    color 150ms ease;
}
.account-trigger:hover {
  color: #efe5d0;
}
.account-trigger.is-open {
  z-index: 51;
  border-color: #d3c4a8;
  background: #fffaf1;
  color: #14241c;
}
.account-trigger.is-open::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: #fffaf1;
  content: '';
}
.chevron {
  color: #82745e;
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
  border: 1px solid #d3c4a8;
  border-radius: 4px 0 4px 4px;
  background: #fffaf1;
  color: #14241c;
  box-shadow: 0 8px 24px #14241c24;
  transform-origin: top right;
}
.account-heading {
  display: grid;
  gap: 6px;
  padding: 20px 18px;
  border-bottom: 1px solid #e5dac6;
}
.account-caption {
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #6a6252;
}
.account-name {
  overflow-wrap: anywhere;
  font-size: 15px;
}
.account-description {
  font-size: 12px;
  line-height: 1.7;
  color: #6a6252;
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
  background: #eee4d1;
}
.account-options span {
  color: #82745e;
}
.account-options .sign-out {
  border-top: 1px solid #e5dac6;
  margin-top: 4px;
  color: #8c392b;
}
a:focus-visible,
button:focus-visible {
  outline: 2px solid #c6ad7a;
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
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    padding: 10px 18px;
    gap: 12px;
  }
  .customer-shell :deep(> div.relative > header > :nth-child(3)) {
    gap: 8px;
    justify-content: flex-end;
  }
  .header-history-btn {
    display: inline-flex;
    align-items: center;
    padding: 8px 12px;
    background: transparent;
    border: 1px solid rgba(198, 173, 122, 0.4);
    color: #efe5d0;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.05em;
    border-radius: 4px;
    text-decoration: none;
    transition: all 140ms ease;
    white-space: nowrap;
  }
  .header-history-btn:hover {
    background: rgba(198, 173, 122, 0.15);
    border-color: #c6ad7a;
    color: #ffffff;
  }
  .header-reserve-btn {
    padding: 6px 10px;
    font-size: 10.5px;
  }
  .account-trigger {
    padding: 8px 10px;
    gap: 6px;
  }
}
@media (max-width: 700px) {
  .catalog-shell :deep(> div.relative > header + div) {
    padding: 18px 16px;
  }
  .customer-shell :deep(> div.relative > header) {
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    padding: 12px;
    gap: 8px;
  }
  .customer-shell :deep(> div.relative > header > :nth-child(3)) {
    gap: 0;
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
.header-history-btn {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  background: transparent;
  border: 1px solid rgba(198, 173, 122, 0.4);
  color: #efe5d0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  border-radius: 4px;
  text-decoration: none;
  transition: all 140ms ease;
  white-space: nowrap;
}
.header-history-btn:hover {
  background: rgba(198, 173, 122, 0.15);
  border-color: #c6ad7a;
  color: #ffffff;
}
.header-reserve-btn {
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  background: #c6ad7a;
  color: #14281f;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: normal;
  text-transform: none;
  border-radius: 4px;
  text-decoration: none;
  transition: all 140ms ease;
  white-space: nowrap;
}
.header-reserve-btn:hover {
  background: #e0d0ac;
  color: #0b1a13;
}
@media (max-width: 600px) {
  .header-history-btn {
    padding: 6px 8px;
    font-size: 10px;
  }
  .header-reserve-btn {
    padding: 6px 8px;
    font-size: 10px;
  }
}

.header-orders-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: transparent;
  border: 1px solid rgba(198, 173, 122, 0.4);
  color: #efe5d0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  border-radius: 4px;
  cursor: pointer;
  transition: all 140ms ease;
  white-space: nowrap;
}
.header-orders-btn:hover {
  background: rgba(198, 173, 122, 0.15);
  border-color: #c6ad7a;
  color: #ffffff;
}
.header-orders-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 9px;
  background: #c6ad7a;
  color: #14281f;
  font-size: 10px;
  font-weight: 800;
}
@media (max-width: 600px) {
  .header-orders-btn {
    padding: 6px 8px;
    font-size: 10px;
  }
}
</style>

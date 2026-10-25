<script setup lang="ts">
import { ref } from 'vue';
import { OnboardingInvitation } from '@theparlor/ui';
import { createMe, type CreateAccountRequest } from '@theparlor/api-client';
import { clearAccount, ensureAccount } from '@theparlor/account';
import { useI18n } from 'vue-i18n';
import { useLocale } from '../../../shared/lib/locale';

const onboarding = ref<InstanceType<typeof OnboardingInvitation> | null>(null);
const isSubmitting = ref(false);
const failed = ref(false);
const { locale, setLocale } = useLocale();
const { t } = useI18n({ useScope: 'global' });

async function handleSubmit(payload: CreateAccountRequest) {
  if (isSubmitting.value) return;

  isSubmitting.value = true;
  failed.value = false;
  try {
    await createMe(payload);

    clearAccount();
    const account = await ensureAccount();
    if (account.status !== 'ready') {
      throw new Error('Account is not ready after onboarding');
    }

    // Updating account state lets the existing guard leave onboarding.
    onboarding.value?.showSuccess();
  } catch {
    failed.value = true;
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <fieldset
    class="m-0 min-w-0 border-0 p-0"
    :disabled="isSubmitting"
    :aria-busy="isSubmitting"
  >
    <OnboardingInvitation
      ref="onboarding"
      :locale="locale"
      @update:locale="setLocale"
      @submit="handleSubmit"
    />
  </fieldset>
  <div class="toast-position">
    <Transition name="onboarding-toast" mode="out-in">
      <div
        v-if="isSubmitting || failed"
        :key="failed ? 'error' : 'saving'"
        :role="failed ? 'alert' : 'status'"
        aria-atomic="true"
        class="onboarding-toast"
        :class="{ 'is-error': failed }"
      >
        <span class="toast-icon" aria-hidden="true">
          <svg
            v-if="failed"
            width="24"
            height="24"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
          >
            <path d="m12 2 10 10-10 10L2 12Z" />
            <path d="M12 7v6m0 4h.01" />
          </svg>
          <svg
            v-else
            class="toast-spinner"
            width="24"
            height="24"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="9" opacity="0.2" />
            <path d="M12 3a9 9 0 0 1 9 9" stroke-linecap="round" />
          </svg>
        </span>
        <p>{{ t(failed ? 'onboarding.error' : 'onboarding.saving') }}</p>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.toast-position {
  position: fixed;
  bottom: calc(24px + env(safe-area-inset-bottom, 0px));
  left: 50%;
  z-index: 100;
  width: calc(100% - 32px);
  max-width: 460px;
  transform: translateX(-50%);
  pointer-events: none;
}
.onboarding-toast {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 24px;
  border: 1px solid #c6ad7a;
  background: #244435;
  color: #f3ead8;
  box-shadow:
    0 14px 40px #00000066,
    0 0 0 4px #0e1d1680;
  font-family:
    'Plus Jakarta Sans',
    -apple-system,
    BlinkMacSystemFont,
    sans-serif;
  pointer-events: auto;
}
.onboarding-toast::before {
  position: absolute;
  inset: 5px;
  border: 1px solid #c6ad7a40;
  pointer-events: none;
  content: '';
}
.toast-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 40px;
  height: 28px;
  padding-right: 14px;
  border-right: 1px solid #c6ad7a55;
  color: #dfc48d;
}
.is-error .toast-icon {
  color: var(--color-danger);
}
.onboarding-toast p {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.6;
}
.toast-spinner {
  animation: toast-spin 900ms linear infinite;
}
@keyframes toast-spin {
  to {
    transform: rotate(360deg);
  }
}
.onboarding-toast-enter-active {
  transition:
    opacity 220ms ease,
    transform 220ms cubic-bezier(0.22, 1, 0.36, 1);
}
.onboarding-toast-leave-active {
  transition:
    opacity 120ms ease,
    transform 120ms ease;
}
.onboarding-toast-enter-from,
.onboarding-toast-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
@media (prefers-reduced-motion: reduce) {
  .toast-spinner {
    animation: none;
  }
  .onboarding-toast-enter-active,
  .onboarding-toast-leave-active {
    transition: none;
  }
}
</style>

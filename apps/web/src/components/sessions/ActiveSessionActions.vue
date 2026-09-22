<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';

defineProps<{ busy?: boolean }>();

const emit = defineEmits<{
  'call-staff': [];
  'end-playing': [];
}>();

const { t } = useI18n();
const isEndConfirmationOpen = ref(false);

const confirmEndPlaying = () => {
  isEndConfirmationOpen.value = false;
  emit('end-playing');
};
</script>

<template>
  <section class="session-actions" :aria-label="t('activeSession.actions')">
    <button class="primary-action" type="button" @click="emit('call-staff')">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
        <path d="M10 21h4" />
      </svg>
      {{ t('activeSession.callStaff') }}
    </button>
    <button
      class="secondary-action"
      type="button"
      @click="isEndConfirmationOpen = true"
    >
      {{ t('activeSession.endPlaying') }}
    </button>

    <div
      v-if="isEndConfirmationOpen"
      class="end-confirmation"
      role="dialog"
      aria-modal="false"
      :aria-labelledby="'end-playing-title'"
    >
      <h2 id="end-playing-title">{{ t('activeSession.endTitle') }}</h2>
      <p>{{ t('activeSession.endDescription') }}</p>
      <div>
        <button type="button" @click="isEndConfirmationOpen = false">
          {{ t('activeSession.cancel') }}
        </button>
        <button class="confirm-end" type="button" @click="confirmEndPlaying">
          {{ t('activeSession.confirmEnd') }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.session-actions {
  position: relative;
  display: grid;
  align-content: start;
  gap: 0.5rem;
}

.primary-action,
.secondary-action {
  display: flex;
  min-height: 2.75rem;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  padding: 0.5rem 1rem;
  border-radius: var(--session-radius-md, 0.5rem);
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  line-height: 1.4;
  cursor: pointer;
  transition: background 150ms ease;
}

.primary-action {
  border: 1px solid var(--session-primary, #497883);
  color: #fff;
  background: var(--session-primary, #497883);
}

.secondary-action {
  border: 1px solid #91a0b3;
  color: var(--session-primary-strong, #315b65);
  background: var(--session-surface, #fff);
}

.primary-action svg {
  width: 1rem;
  height: 1rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.primary-action:hover {
  background: var(--session-primary-strong, #315b65);
}

.secondary-action:hover {
  border-color: var(--session-primary, #497883);
  background: var(--session-primary-soft, #eaf2f3);
}

button:focus-visible {
  outline: 3px solid #20252d;
  outline-offset: 2px;
}

.end-confirmation {
  position: absolute;
  z-index: 10;
  right: 0;
  bottom: calc(100% + 0.75rem);
  width: min(100%, 23rem);
  padding: 1.15rem;
  border: 1px solid #dce2e6;
  border-radius: 0.75rem;
  background: #fff;
  box-shadow: 0 1rem 2.5rem rgb(32 37 45 / 16%);
}

.end-confirmation h2 {
  margin: 0;
  font-size: 1rem;
}

.end-confirmation p {
  margin: 0.45rem 0 1rem;
  color: #647080;
  font-size: 0.8rem;
  line-height: 1.5;
}

.end-confirmation > div {
  display: flex;
  justify-content: flex-end;
  gap: 0.55rem;
}

.end-confirmation button {
  min-height: 2.35rem;
  padding: 0.45rem 0.75rem;
  border: 1px solid #91a0b3;
  border-radius: 0.5rem;
  color: #20252d;
  background: #fff;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
}

.end-confirmation .confirm-end {
  border-color: #497883;
  color: #fff;
  background: #497883;
}

@media (prefers-reduced-motion: reduce) {
  .primary-action,
  .secondary-action {
    transition: none;
  }
}
</style>

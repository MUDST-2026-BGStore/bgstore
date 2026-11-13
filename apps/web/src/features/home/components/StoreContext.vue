<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { useBranchDirectoryStore } from '../stores/branch-directory';
import { useStoreContextStore } from '../stores/store-context';
import BranchSelect from './BranchSelect.vue';
import { useStoreText } from '../data/store-text';
const { t } = useStoreText();
const context = useStoreContextStore();
const directory = useBranchDirectoryStore();
const dialog = ref<HTMLDialogElement>();
const branches = computed(() => directory.branches);
const intent = ref('browse');
const selectedBranch = ref('');
const draft = computed(() =>
  intent.value === 'branch' ? selectedBranch.value : intent.value,
);
const sourceLabel = computed(() =>
  context.stockBranch ? t('Prices and stock') : t('Browsing preference'),
);
const currentLabel = computed(() =>
  context.branch
    ? t(context.branch.name)
    : context.choice === 'online'
      ? t('Online')
      : t('Browse all games'),
);
// Visiting is the other half of the store: book a table at the chosen branch.
const reserveLink = computed(() => ({
  path: '/reservations/new',
  query: context.branch ? { branch: context.branch.id } : {},
}));
function open() {
  const current = context.branch?.id || context.choice || 'browse';
  intent.value = directory.byId(current) ? 'branch' : current;
  selectedBranch.value = context.branch?.id || '';
  dialog.value?.showModal();
}
function save() {
  if (
    !['browse', 'online'].includes(draft.value) &&
    !directory.byId(draft.value)
  )
    return;
  context.select(draft.value);
  dialog.value?.close();
}
onMounted(async () => {
  // Wait for the directory so a saved branch is recognized before asking again.
  await directory.load();
  if (!context.choice) open();
});
defineExpose({ open });
</script>
<template>
  <div class="store-context">
    <button
      type="button"
      class="context-trigger"
      aria-haspopup="dialog"
      @click="open"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.6"
        aria-hidden="true"
      >
        <path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z" />
        <circle cx="12" cy="10" r="2.5" />
      </svg>
      <span>{{ currentLabel }}</span
      ><span class="change">{{ t('Change') }}</span>
    </button>
    <div class="context-actions">
      <RouterLink class="reserve-link" :to="reserveLink">{{
        context.branch
          ? t('Reserve a table at {branch}', {
              branch: t(context.branch.name),
            })
          : t('Reserve a table')
      }}</RouterLink>
      <span class="source-label">{{ sourceLabel }}</span>
    </div>
  </div>
  <dialog
    ref="dialog"
    class="context-dialog"
    aria-labelledby="context-title"
    @click="$event.target === dialog && dialog?.close()"
  >
    <div class="dialog-heading">
      <h2 id="context-title">{{ t('Choose your store') }}</h2>
      <button
        type="button"
        :aria-label="t('Close store selector')"
        @click="dialog?.close()"
      >
        ✕
      </button>
    </div>
    <p>
      {{ t('Prices and stock are shown for the store you choose.') }}
    </p>
    <fieldset>
      <legend>{{ t('Online') }}</legend>
      <label
        ><input
          v-model="intent"
          type="radio"
          name="store-context"
          value="online"
        /><span
          ><strong>{{ t('Online') }}</strong
          ><small>{{ t('Delivery prices and stock.') }}</small></span
        ></label
      >
    </fieldset>
    <fieldset>
      <legend>{{ t('In store') }}</legend>
      <label
        ><input
          v-model="intent"
          type="radio"
          name="store-context"
          value="branch"
        /><span
          ><strong>{{ t('Visit a branch') }}</strong
          ><small>{{ t('Prices and stock at this branch.') }}</small></span
        ></label
      >
      <BranchSelect
        v-if="intent === 'branch'"
        v-model="selectedBranch"
        :options="branches"
        name="store-branch"
        label="Branch"
        class="store-branch-select"
      />
      <RouterLink class="locations-link" to="/branches" @click="dialog?.close()"
        >{{ t('Branch locations') }}
        <span aria-hidden="true">→</span></RouterLink
      >
    </fieldset>
    <label class="browse-option"
      ><input
        v-model="intent"
        type="radio"
        name="store-context"
        value="browse"
      /><span
        ><strong>{{ t('Just browsing') }}</strong
        ><small>{{
          t('Explore all games without choosing a branch.')
        }}</small></span
      ></label
    >
    <button
      type="button"
      class="save-context"
      :disabled="
        !['browse', 'online'].includes(draft) && !directory.byId(draft)
      "
      @click="save"
    >
      {{ t('Save & continue') }}
    </button>
  </dialog>
</template>
<style scoped>
.locations-link {
  display: inline-block;
  margin-top: 10px;
  color: #244c38;
  font-size: 13px;
  text-decoration: underline;
}
.store-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  font-size: 13px;
}
.context-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 4px 18px;
  min-width: 0;
}
.reserve-link {
  color: #244c38;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.reserve-link:focus-visible {
  outline: 2px solid #244c38;
  outline-offset: 3px;
}
.context-trigger {
  min-width: 0;
  flex-shrink: 1;
  display: flex;
  align-items: center;
  gap: 9px;
  font-weight: 600;
  cursor: pointer;
  padding: 5px 0;
}
.context-trigger > span:first-of-type {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.context-trigger svg {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
}
.change {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 400;
  text-decoration: underline;
  text-underline-offset: 3px;
  margin-left: 5px;
}
.source-label {
  max-width: 45%;
  overflow-wrap: anywhere;
  color: #4e5b53;
  font-size: 12px;
}
.context-dialog {
  width: min(460px, calc(100vw - 28px));
  max-height: calc(100dvh - 32px);
  overflow-y: auto;
  margin: auto;
  padding: 24px;
  border: 1px solid #bac5bd;
  border-radius: 5px;
  color: #202e27;
  background: #fcfcfa;
  box-shadow: 0 16px 60px #12271c33;
}
.context-dialog::backdrop {
  background: #102c20a3;
}
.dialog-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
h2 {
  font-size: 21px;
  line-height: 1.3;
  font-weight: 600;
}
.dialog-heading button {
  padding: 3px;
  cursor: pointer;
}
p {
  font-size: 13px;
  line-height: 1.5;
  margin-top: 9px;
  color: #4e5b53;
}
fieldset {
  border: 0;
  padding: 0;
  margin: 18px 0 0;
}
legend {
  font-size: 12px;
  color: #4e5b53;
  margin-bottom: 6px;
}
label {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 10px;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 3px;
  transition:
    background-color 140ms ease,
    border-color 140ms ease;
}
label:hover {
  background: #edf1ec;
}
label:has(input:checked) {
  background: #e5ece6;
  border-color: #829087;
}
input {
  accent-color: #244c38;
  flex-shrink: 0;
  width: 16px;
  height: 16px;
}
strong {
  display: block;
  font-size: 14px;
  font-weight: 600;
}
small {
  display: block;
  font-size: 12px;
  color: #4e5b53;
  margin-top: 2px;
}
.browse-option {
  margin-top: 16px;
  border-top: 1px solid #d3d9d4;
}
.save-context {
  width: 100%;
  background: #244c38;
  color: white;
  font-size: 14px;
  padding: 11px;
  border-radius: 3px;
  margin-top: 18px;
  cursor: pointer;
  transition: background-color 140ms ease;
}
.store-branch-select {
  margin: 10px 0 0 28px;
}
.save-context:disabled {
  background: #e2e6e2;
  color: #4e5b53;
  cursor: not-allowed;
}
.save-context:hover:enabled {
  background: #193e2c;
}
button:focus-visible,
input:focus-visible {
  outline: 2px solid #244c38;
  outline-offset: 3px;
}
@media (prefers-reduced-motion: no-preference) {
  .context-dialog[open] {
    animation: context-in 180ms ease-out;
  }
  @keyframes context-in {
    from {
      opacity: 0;
      transform: translateY(6px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
}
@media (max-width: 480px) {
  .context-dialog {
    padding: 18px;
  }
  .store-context {
    align-items: flex-start;
  }
  .context-actions {
    flex-direction: column;
    align-items: flex-end;
    max-width: 55%;
    padding-top: 5px;
    text-align: right;
    overflow-wrap: anywhere;
  }
}
</style>

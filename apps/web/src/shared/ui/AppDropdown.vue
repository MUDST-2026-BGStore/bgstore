<script setup lang="ts">
import { onBeforeUnmount, ref, useId, watch } from 'vue';

const props = withDefaults(
  defineProps<{
    label: string;
    panelLabel?: string;
    triggerId?: string;
    popupRole?: 'dialog' | 'listbox' | 'menu';
    width?: number;
    disabled?: boolean;
    /** Fill the container's width, like a form field. */
    block?: boolean;
  }>(),
  { popupRole: 'dialog', width: 320, disabled: false, block: false },
);
const emit = defineEmits<{
  opened: [];
  closed: [];
  triggerKeydown: [event: KeyboardEvent];
}>();
const panelId = `${useId()}-dropdown`;
const trigger = ref<HTMLButtonElement>();
const panel = ref<HTMLDivElement>();
const isOpen = ref(false);
let observer: ResizeObserver | undefined;

function positionPanel() {
  if (!trigger.value || !panel.value) return;
  const anchor = trigger.value.getBoundingClientRect();
  const popup = panel.value;
  // Measure a closed popover before the browser paints its opening frame.
  const measuringClosed = !popup.matches(':popover-open');
  if (measuringClosed) {
    popup.style.visibility = 'hidden';
    popup.style.display = 'block';
  }
  const gutter = 12;
  const gap = 4;
  const width = Math.min(
    Math.max(props.width, anchor.width),
    window.innerWidth - gutter * 2,
  );
  popup.style.width = `${width}px`;
  popup.style.left = `${Math.max(gutter, Math.min(anchor.left, window.innerWidth - width - gutter))}px`;
  const below = Math.max(0, window.innerHeight - anchor.bottom - gap - gutter);
  const above = Math.max(0, anchor.top - gap - gutter);
  const desired = Math.min(popup.scrollHeight || 320, 320);
  const upwards = below < desired && above > below;
  const available = Math.min(
    upwards ? above : below,
    window.innerHeight - gutter * 2,
  );
  popup.style.maxHeight = `${available}px`;
  const height = popup.getBoundingClientRect().height;
  const preferredTop = upwards
    ? anchor.top - gap - height
    : anchor.bottom + gap;
  popup.style.top = `${Math.max(gutter, Math.min(preferredTop, window.innerHeight - height - gutter))}px`;
  if (measuringClosed) {
    popup.style.removeProperty('display');
    popup.style.removeProperty('visibility');
  }
}
function stopPositioning() {
  observer?.disconnect();
  observer = undefined;
  window.removeEventListener('resize', positionPanel);
  window.removeEventListener('scroll', positionPanel, true);
}
function onBeforeToggle(event: Event) {
  if ((event as ToggleEvent).newState === 'open') positionPanel();
}
function onToggle(event: Event) {
  isOpen.value = (event as ToggleEvent).newState === 'open';
  stopPositioning();
  if (isOpen.value) {
    positionPanel();
    observer = new ResizeObserver(positionPanel);
    if (panel.value) observer.observe(panel.value);
    if (trigger.value) observer.observe(trigger.value);
    window.addEventListener('resize', positionPanel);
    window.addEventListener('scroll', positionPanel, {
      capture: true,
      passive: true,
    });
    emit('opened');
  } else emit('closed');
}
function openPanel() {
  if (!props.disabled && panel.value && !panel.value.matches(':popover-open'))
    panel.value.showPopover();
}
function closePanel(restoreFocus = true) {
  if (panel.value?.matches(':popover-open')) panel.value.hidePopover();
  if (restoreFocus) trigger.value?.focus({ preventScroll: true });
}
watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) closePanel(false);
  },
);
onBeforeUnmount(stopPositioning);
defineExpose({
  open: openPanel,
  close: closePanel,
  focus: () => trigger.value?.focus({ preventScroll: true }),
});
</script>

<template>
  <span class="app-dropdown" :class="{ block }">
    <button
      :id="triggerId"
      ref="trigger"
      type="button"
      class="dropdown-trigger"
      :disabled="disabled"
      :popovertarget="panelId"
      :aria-label="label"
      :aria-haspopup="popupRole"
      :aria-expanded="isOpen"
      :aria-controls="panelId"
      @keydown="emit('triggerKeydown', $event)"
    >
      <slot name="trigger" :open="isOpen">{{ label }}</slot>
      <svg
        class="dropdown-chevron"
        :class="{ expanded: isOpen }"
        width="12"
        height="12"
        viewBox="0 0 12 12"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="m2 4 4 4 4-4"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>
    <div
      :id="panelId"
      ref="panel"
      popover="auto"
      class="dropdown-panel"
      :role="popupRole"
      :aria-label="panelLabel || label"
      @beforetoggle="onBeforeToggle"
      @toggle="onToggle"
      @keydown.esc.prevent.stop="closePanel()"
    >
      <slot :close="closePanel" />
    </div>
  </span>
</template>

<style scoped>
.app-dropdown {
  display: inline-flex;
  min-width: 0;
}
.app-dropdown.block {
  display: flex;
  width: 100%;
}
.app-dropdown.block .dropdown-trigger {
  flex: 1 1 auto;
}
.dropdown-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: var(--ui-control-height);
  min-width: 0;
  padding: 6px 10px;
  border: 1px solid var(--ui-stroke);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
  color: var(--ui-text);
  font: inherit;
  font-size: var(--ui-control-font-size);
  line-height: 20px;
  cursor: pointer;
}
.dropdown-trigger:hover:enabled,
.dropdown-trigger[aria-expanded='true'] {
  background: var(--ui-surface-hover);
  border-color: var(--ui-stroke-strong);
}
.dropdown-trigger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.dropdown-trigger:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: 2px;
}
.dropdown-chevron {
  flex: 0 0 auto;
  transition: transform 100ms ease;
}
.dropdown-chevron.expanded {
  transform: rotate(180deg);
}
.dropdown-panel {
  position: fixed;
  inset: auto;
  margin: 0;
  box-sizing: border-box;
  padding: 0;
  border: 1px solid var(--ui-divider);
  border-radius: var(--ui-radius-lg);
  background: var(--ui-surface);
  color: var(--ui-text);
  font: inherit;
  font-size: 14px;
  line-height: 20px;
  overflow: auto;
  overscroll-behavior: contain;
  box-shadow: 0 4px 12px rgb(0 0 0 / 16%);
}
@media (prefers-reduced-motion: reduce) {
  .dropdown-chevron {
    transition: none;
  }
}
@media (forced-colors: active) {
  .dropdown-trigger,
  .dropdown-panel {
    border-color: ButtonText;
  }
}
</style>

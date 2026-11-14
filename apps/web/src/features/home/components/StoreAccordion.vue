<script setup lang="ts">
import { ref, useId } from 'vue';
defineProps<{ title: string }>();
const expanded = ref(false);
const id = useId();
const trigger = ref<HTMLButtonElement>();
/** Opens the section and brings its heading into view. */
function open() {
  expanded.value = true;
  trigger.value?.scrollIntoView({ block: 'start', behavior: 'smooth' });
  trigger.value?.focus({ preventScroll: true });
}
defineExpose({ open });
</script>
<template>
  <section class="store-accordion">
    <button
      :id="`accordion-trigger-${id}`"
      ref="trigger"
      type="button"
      class="accordion-trigger"
      :aria-expanded="expanded"
      :aria-controls="`accordion-panel-${id}`"
      @click="expanded = !expanded"
    >
      <span>{{ title }}</span
      ><span class="accordion-icon" aria-hidden="true">+</span>
    </button>
    <div
      :id="`accordion-panel-${id}`"
      class="accordion-panel"
      :class="{ 'is-open': expanded }"
      :aria-hidden="!expanded"
      :inert="!expanded"
    >
      <div class="accordion-clip">
        <div class="accordion-content"><slot /></div>
      </div>
    </div>
  </section>
</template>
<style scoped>
.store-accordion {
  border-top: 1px solid #d3d9d4;
  color: #202e27;
}
.accordion-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 16px 0;
  text-align: left;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 250ms cubic-bezier(0.4, 0, 0.2, 1);
}
.accordion-trigger:hover {
  opacity: 0.7;
}
.accordion-trigger:focus-visible {
  outline: 2px solid #244c38;
  outline-offset: 3px;
}
.accordion-icon {
  flex-shrink: 0;
  font-size: 20px;
  line-height: 1;
  font-weight: 400;
  transition: transform 400ms cubic-bezier(0.4, 0, 0.2, 1);
}
.accordion-trigger[aria-expanded='true'] .accordion-icon {
  transform: rotate(45deg);
}
.accordion-panel {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 350ms cubic-bezier(0.4, 0, 0.2, 1);
}
.accordion-panel.is-open {
  grid-template-rows: 1fr;
}
.accordion-clip {
  min-height: 0;
  overflow: hidden;
}
.accordion-content {
  padding: 0 0 20px;
  opacity: 0;
  transform: translateY(-6px);
  transition:
    opacity 250ms cubic-bezier(0.4, 0, 0.2, 1),
    transform 250ms cubic-bezier(0.4, 0, 0.2, 1);
}
.is-open .accordion-content {
  opacity: 1;
  transform: translateY(0);
}
@media (prefers-reduced-motion: reduce) {
  .accordion-trigger,
  .accordion-icon,
  .accordion-panel,
  .accordion-content {
    transition: none;
  }
  .accordion-content {
    transform: none;
  }
}
</style>

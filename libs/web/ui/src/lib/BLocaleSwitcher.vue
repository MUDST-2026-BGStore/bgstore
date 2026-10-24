<script setup lang="ts">
defineProps<{ locale: 'th-TH' | 'en-US'; label?: string }>();
const emit = defineEmits<{ 'update:locale': [locale: 'th-TH' | 'en-US'] }>();
</script>
<template>
  <div
    class="locale-switcher"
    role="group"
    :aria-label="label || 'Language / ภาษา'"
  >
    <button
      v-for="option in ['th-TH', 'en-US'] as const"
      :key="option"
      type="button"
      :lang="option"
      :aria-pressed="locale === option"
      @click="emit('update:locale', option)"
    >
      {{ option === 'th-TH' ? 'ไทย' : 'EN' }}
    </button>
  </div>
</template>
<style scoped>
.locale-switcher {
  display: inline-flex;
  padding: 3px;
  border: 1px solid #c6ad7a80;
  background: #13281d;
  font-family: Arial, sans-serif;
  font-size: 12px;
  letter-spacing: normal;
  line-height: 1.5;
}
button {
  padding: 4px 9px;
  color: #f3e5c5;
  cursor: pointer;
  transition:
    color 140ms ease,
    background-color 140ms ease;
}
button:hover {
  background: #35523f;
}
button[aria-pressed='true'] {
  background: #c6ad7a;
  color: #14241c;
  font-weight: 600;
}
button:focus-visible {
  outline: 2px solid white;
  outline-offset: -2px;
}
@media (prefers-reduced-motion: reduce) {
  button {
    transition: none;
  }
}
</style>

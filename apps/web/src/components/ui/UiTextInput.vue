<script setup lang="ts">
import searchIcon from '../../assets/icons/search.svg';

withDefaults(
  defineProps<{
    id: string;
    placeholder?: string;
    /** Shows the leading search glyph from the Figma Input component. */
    search?: boolean;
    inputmode?: 'text' | 'numeric';
    /** Draws the error border and marks the input for assistive tech. */
    invalid?: boolean;
  }>(),
  {
    placeholder: undefined,
    search: false,
    inputmode: 'text',
    invalid: false,
  },
);

const model = defineModel<string>({ default: '' });
</script>

<template>
  <div
    class="flex h-10 items-center gap-2 rounded-md border bg-surface px-3 focus-within:border-primary"
    :class="invalid ? 'border-danger-border' : 'border-line'"
  >
    <img
      v-if="search"
      :src="searchIcon"
      alt=""
      class="block size-4 shrink-0"
      width="16"
      height="16"
    />
    <input
      :id="id"
      v-model="model"
      :type="search ? 'search' : 'text'"
      :inputmode="inputmode"
      :placeholder="placeholder"
      :aria-invalid="invalid ? 'true' : undefined"
      class="min-w-0 flex-1 bg-transparent text-[14px] leading-[22px] text-ink outline-none placeholder:text-ink-muted"
    />
  </div>
</template>

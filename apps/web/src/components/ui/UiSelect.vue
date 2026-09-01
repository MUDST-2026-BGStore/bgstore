<script setup lang="ts">
import chevronDown from '../../assets/icons/chevron-down.svg';
import type { SelectOption } from './types';

withDefaults(
  defineProps<{
    id: string;
    /**
     * Empty-value option, rendered in the Figma placeholder (muted) state.
     * Omit it when every choice is a real value, so the list carries no
     * duplicate of its own first option.
     */
    placeholder?: string;
    /**
     * Whether the empty value can be chosen. Filters need it (empty means
     * "no filter", and the user must be able to get back to it); a form
     * prompt such as "Select a category" does not.
     */
    placeholderSelectable?: boolean;
    /**
     * Option values are what the API expects (a category key, a branch id);
     * labels are what the reader sees.
     */
    options?: readonly SelectOption[];
    invalid?: boolean;
  }>(),
  {
    placeholder: undefined,
    placeholderSelectable: false,
    options: undefined,
    invalid: false,
  },
);

const model = defineModel<string>({ default: '' });
</script>

<template>
  <div
    class="relative flex h-10 items-center gap-2 rounded-md border bg-surface px-3 focus-within:border-primary"
    :class="invalid ? 'border-danger-border' : 'border-line'"
  >
    <select
      :id="id"
      v-model="model"
      class="min-w-0 flex-1 appearance-none bg-transparent text-[14px] leading-[22px] outline-none"
      :class="model ? 'text-ink' : 'text-ink-muted'"
    >
      <option v-if="placeholder" value="" :disabled="!placeholderSelectable">
        {{ placeholder }}
      </option>
      <option
        v-for="option in options"
        :key="option.value"
        :value="option.value"
      >
        {{ option.label }}
      </option>
    </select>
    <img
      :src="chevronDown"
      alt=""
      class="pointer-events-none block size-4 shrink-0"
      width="16"
      height="16"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import type { ButtonSize, ButtonVariant } from './types';

const props = withDefaults(
  defineProps<{
    variant?: ButtonVariant;
    size?: ButtonSize;
    /** Renders a router-link instead of a button when set. */
    to?: string;
    /** Renders a plain link, for a destination outside the SPA such as sign-in. */
    href?: string;
    type?: 'button' | 'submit' | 'reset';
    disabled?: boolean;
  }>(),
  {
    variant: 'primary',
    size: 'md',
    to: undefined,
    href: undefined,
    type: 'button',
    disabled: false,
  },
);

const variantClass = computed(
  () =>
    ({
      primary: 'bg-primary border border-primary text-primary-fg',
      outline: 'border border-line-strong text-ink-secondary',
      ghost: 'text-ink-secondary',
    })[props.variant],
);

const sizeClass = computed(() =>
  props.size === 'sm'
    ? 'h-8 gap-1 rounded-sm px-3 text-[12px] leading-[18px]'
    : 'h-10 gap-2 rounded-md px-4 text-[13px] leading-[20px] font-medium',
);

/*
 * One props object per rendered kind. Spreading avoids passing an `href`
 * (even undefined) to RouterLink, whose fallthrough would clobber the
 * internal href it computes from `to`.
 */
const componentProps = computed(() => {
  if (props.to !== undefined) return { to: props.to };
  if (props.href !== undefined) return { href: props.href };
  return { type: props.type, disabled: props.disabled };
});
</script>

<template>
  <component
    :is="to ? RouterLink : href ? 'a' : 'button'"
    v-bind="componentProps"
    class="inline-flex shrink-0 items-center justify-center text-center whitespace-nowrap focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50"
    :class="[variantClass, sizeClass]"
  >
    <slot />
  </component>
</template>

<script setup lang="ts">
import {
  AppBadge,
  AppButton,
  AppRecordLink,
  AppTableActions,
} from '../../../../shared/ui';

defineProps<{
  name: string;
  subtitle?: string;
  identifier: string;
  status: string;
  tone: 'neutral' | 'success' | 'warning' | 'danger';
  active: boolean;
  selected?: boolean;
}>();
const emit = defineEmits<{ inspect: [event: MouseEvent] }>();
</script>

<template>
  <tr
    data-record-row
    :data-active="active"
    :aria-selected="selected"
    @click="emit('inspect', $event)"
  >
    <slot name="selection" />
    <td class="inventory-identity">
      <strong>{{ name }}</strong>
      <span v-if="subtitle" class="inventory-secondary">{{ subtitle }}</span>
      <AppRecordLink
        class="inventory-identifier"
        data-record-trigger
        :aria-label="`View record ${identifier}`"
        :aria-expanded="active"
        @click.stop="emit('inspect', $event)"
        >{{ identifier }}</AppRecordLink
      >
    </td>
    <slot />
    <td>
      <AppBadge :tone="tone">{{ status }}</AppBadge>
    </td>
    <AppTableActions>
      <slot name="actions">
        <AppButton
          :aria-label="`View details for ${name}, ${identifier}`"
          :aria-expanded="active"
          @click.stop="emit('inspect', $event)"
          >Details</AppButton
        >
      </slot>
    </AppTableActions>
  </tr>
</template>

<style scoped>
.inventory-identity {
  min-width: 200px;
}
.inventory-secondary {
  display: block;
  margin-top: 4px;
  color: var(--ui-muted);
  font-size: 12px;
}
.inventory-identifier {
  display: block;
  margin-top: 4px;
  font-family: monospace;
  font-size: 12px;
}
</style>

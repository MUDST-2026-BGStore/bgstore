<script setup lang="ts">
import { useI18n } from 'vue-i18n';

defineProps<{ current: string }>();

const { t } = useI18n();
</script>

<template>
  <nav
    class="flex shrink-0 items-center gap-2 text-[18px] font-semibold text-ink"
  >
    <router-link to="/games">{{ t('games.breadcrumb.games') }}</router-link>
    <span aria-hidden="true">&rsaquo;</span>
    <span>{{ current }}</span>
  </nav>
</template>

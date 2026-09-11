<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import pagePrevious from '../../assets/icons/page-previous.svg';
import pageNext from '../../assets/icons/page-next.svg';
import { pageItems } from './pagination';

/** The Figma Pagination component: circular previous/next and page buttons. */
const props = defineProps<{ totalPages: number }>();

/** 1-based, like the API's `page` parameter. */
const page = defineModel<number>({ required: true });

const { t } = useI18n();

const items = computed(() => pageItems(page.value, props.totalPages));
</script>

<template>
  <nav class="flex items-center gap-2" :aria-label="t('pagination.label')">
    <button
      type="button"
      class="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface-sunken disabled:opacity-40"
      :aria-label="t('pagination.previous')"
      :disabled="page <= 1"
      @click="page -= 1"
    >
      <img
        :src="pagePrevious"
        alt=""
        class="block size-4"
        width="16"
        height="16"
      />
    </button>
    <template v-for="(item, index) in items" :key="`${index}-${item}`">
      <span
        v-if="item === '…'"
        class="flex size-9 shrink-0 items-center justify-center text-[13px] leading-5 font-medium text-primary"
        aria-hidden="true"
      >
        …
      </span>
      <button
        v-else
        type="button"
        class="flex size-9 shrink-0 flex-col items-center justify-center gap-1 rounded-full text-[13px] leading-5"
        :class="
          item === page
            ? 'font-semibold text-[#27545f]'
            : 'font-medium text-primary'
        "
        :aria-label="t('pagination.page', { page: item })"
        :aria-current="item === page ? 'page' : undefined"
        @click="page = item"
      >
        {{ item }}
        <span
          v-if="item === page"
          class="h-0.5 w-4 rounded-[2px] bg-[#27545f]"
          aria-hidden="true"
        />
      </button>
    </template>
    <button
      type="button"
      class="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface-sunken disabled:opacity-40"
      :aria-label="t('pagination.next')"
      :disabled="page >= totalPages"
      @click="page += 1"
    >
      <img :src="pageNext" alt="" class="block size-4" width="16" height="16" />
    </button>
  </nav>
</template>

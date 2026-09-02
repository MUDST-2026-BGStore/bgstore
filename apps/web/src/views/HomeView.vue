<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { useI18n } from 'vue-i18n';
import { helloQueryOptions } from '../queries/hello';

const { t } = useI18n();
const hello = useQuery(helloQueryOptions());
</script>

<template>
  <section class="home-hero" aria-labelledby="page-title">
    <p class="eyebrow">
      {{ t('home.eyebrow') }}
    </p>
    <h1 id="page-title">
      {{ t('app.title') }}
    </h1>
    <p class="lede">
      {{ t('home.description') }}
    </p>

    <div class="status-card" aria-live="polite">
      <template v-if="hello.isPending.value">
        <span class="status-dot status-dot--pending" aria-hidden="true" />
        <p>{{ t('status.connecting') }}</p>
      </template>

      <template v-else-if="hello.isError.value">
        <span class="status-dot status-dot--error" aria-hidden="true" />
        <div>
          <p class="status-title">{{ t('status.serviceUnavailable') }}</p>
          <p>{{ t('status.serviceUnavailableHint') }}</p>
        </div>
      </template>

      <template v-else>
        <span class="status-dot status-dot--ready" aria-hidden="true" />
        <div>
          <p class="status-title" data-testid="api-message">
            {{ hello.data.value?.message }}
          </p>
          <p>
            {{
              t('status.connected', {
                service: hello.data.value?.service,
                database: hello.data.value?.database,
              })
            }}
          </p>
        </div>
      </template>
    </div>
  </section>
</template>

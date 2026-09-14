<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import OwnerLayout from '../layouts/OwnerLayout.vue';
import UiButton from '../components/ui/UiButton.vue';
import {
  currentUserQueryOptions,
  hasManagerAccess,
} from '../queries/current-user';

const { t } = useI18n();
const currentUser = useQuery(currentUserQueryOptions());
const canManage = computed(() =>
  hasManagerAccess(currentUser.data.value?.roles ?? []),
);

const displayName = computed(() => {
  const user = currentUser.data.value;
  if (!user) return '';
  return (
    [user.firstName, user.lastName].filter(Boolean).join(' ') || user.username
  );
});

const initials = computed(() => {
  const user = currentUser.data.value;
  if (!user) return 'BG';
  const name = [user.firstName, user.lastName].filter(Boolean);
  return (name.length > 0 ? name : [user.username])
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
});
</script>

<template>
  <OwnerLayout active="profile">
    <section
      class="staff-page-content flex w-full flex-col gap-7 px-8 pt-8 pb-10 sm:px-10"
    >
      <header class="staff-profile-heading">
        <div>
          <p class="eyebrow mb-2">{{ t('navigation.staff') }}</p>
          <h1 class="staff-page-title">{{ t('userProfile.title') }}</h1>
          <p class="mt-3 max-w-2xl text-sm leading-6 text-ink-secondary">
            {{ t('userProfile.staffDescription') }}
          </p>
        </div>
        <span v-if="currentUser.data.value" class="staff-profile-role-badge">
          <span class="staff-profile-role-dot" aria-hidden="true" />
          {{ currentUser.data.value.roles.join(' · ') }}
        </span>
        <UiButton v-if="canManage" to="/staff/permissions" variant="outline">
          Staff branch access
        </UiButton>
      </header>

      <div
        v-if="currentUser.isPending.value"
        class="staff-profile-state"
        aria-live="polite"
      >
        {{ t('status.connecting') }}
      </div>
      <div
        v-else-if="currentUser.isError.value"
        class="staff-profile-state staff-profile-state--error"
        role="alert"
      >
        {{ t('status.serviceUnavailableHint') }}
      </div>

      <div v-else-if="currentUser.data.value" class="staff-profile-grid">
        <section
          class="staff-profile-summary-card"
          aria-labelledby="staff-profile-summary-title"
        >
          <div class="staff-profile-avatar" aria-hidden="true">
            {{ initials }}
          </div>
          <p class="staff-profile-card-kicker">{{ t('staff.account') }}</p>
          <h2 id="staff-profile-summary-title" class="staff-profile-name">
            {{ displayName }}
          </h2>
          <p class="staff-profile-username">
            {{ currentUser.data.value.username }}
          </p>

          <div class="staff-profile-roles" :aria-label="t('userProfile.roles')">
            <span
              v-for="role in currentUser.data.value.roles"
              :key="role"
              class="staff-profile-role"
            >
              {{ role }}
            </span>
          </div>

          <div class="staff-profile-summary-footer">
            <span class="staff-profile-status-dot" aria-hidden="true" />
            <span>{{ t('userProfile.activeAccount') }}</span>
          </div>
        </section>

        <section
          class="staff-profile-details-card"
          aria-labelledby="staff-profile-details-title"
        >
          <div class="staff-profile-card-heading">
            <div>
              <p class="staff-profile-card-kicker">
                {{ t('userProfile.identityDetails') }}
              </p>
              <h2
                id="staff-profile-details-title"
                class="staff-profile-section-title"
              >
                {{ t('userProfile.accountAccess') }}
              </h2>
            </div>
            <span class="staff-profile-provider-badge">
              {{ t('userProfile.managedByKeycloak') }}
            </span>
          </div>

          <dl class="staff-profile-details-list">
            <div class="staff-profile-detail">
              <dt>{{ t('userProfile.username') }}</dt>
              <dd>{{ currentUser.data.value.username }}</dd>
            </div>
            <div class="staff-profile-detail">
              <dt>{{ t('userProfile.email') }}</dt>
              <dd>{{ currentUser.data.value.email }}</dd>
            </div>
            <div class="staff-profile-detail">
              <dt>{{ t('userProfile.firstName') }}</dt>
              <dd>{{ currentUser.data.value.firstName || '—' }}</dd>
            </div>
            <div class="staff-profile-detail">
              <dt>{{ t('userProfile.lastName') }}</dt>
              <dd>{{ currentUser.data.value.lastName || '—' }}</dd>
            </div>
          </dl>

          <div class="staff-profile-provider-note">
            <span class="staff-profile-provider-icon" aria-hidden="true"
              >i</span
            >
            <p>{{ t('userProfile.accountAccessDescription') }}</p>
          </div>
        </section>
      </div>
    </section>
  </OwnerLayout>
</template>

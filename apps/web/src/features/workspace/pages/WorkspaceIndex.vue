<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import { useAuthStore } from '../../../shared/stores/auth';
import { useHello, useWhoami } from '@theparlor/api-client';
import { BButton, BCard } from '@theparlor/ui';

const auth = useAuthStore();

const {
  data: apiStatus,
  error: healthError,
  isPending,
  refetch: refreshHealth,
} = useHello();
const {
  data: identity,
  error: identityError,
  refetch: refreshIdentity,
} = useWhoami();
const apiError = computed(() => healthError.value ?? identityError.value);

function checkApiHealth() {
  void refreshHealth();
  void refreshIdentity();
}
</script>

<template>
  <main role="main" aria-label="Home">
    <header>
      <h1>The Parlor workspace</h1>
      <div v-if="auth.authenticated">
        <span aria-label="Signed-in user"> {{ auth.username }} </span>
        <BButton variant="secondary" @click="auth.logout()"> Sign Out </BButton>
      </div>
    </header>

    <nav aria-label="Workspace">
      <RouterLink :to="{ name: 'HomeIndex' }">Public home</RouterLink> ·
      <RouterLink :to="{ name: 'AuthProfile' }">Your profile</RouterLink>
    </nav>
    <section>
      <p v-if="identity" data-testid="api-identity">
        API identity: {{ identity.username }}
      </p>
      <BCard title="User Info">
        <div v-if="auth.authenticated">
          <p><strong>Username:</strong> {{ auth.username }}</p>
          <p><strong>Roles:</strong></p>
          <ul aria-label="Realm roles">
            <li v-for="role in auth.roles" :key="role">
              {{ role }}
            </li>
          </ul>
        </div>
        <p v-else>Authenticating…</p>
      </BCard>

      <BCard title="API Status">
        <div v-if="apiStatus">
          <p>
            <strong>Service:</strong>
            <span data-testid="api-service">{{ apiStatus.service }}</span>
          </p>
          <p>
            <strong>Status:</strong>
            <span>{{ apiStatus.status }}</span>
          </p>
        </div>
        <p v-if="apiError" role="alert">
          {{ apiError.message }}
        </p>
        <p v-if="isPending">Checking…</p>
        <BButton variant="secondary" @click="checkApiHealth()">
          Refresh
        </BButton>
      </BCard>
    </section>
  </main>
</template>

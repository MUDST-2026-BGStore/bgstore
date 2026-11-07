<script setup lang="ts">
import { RouterLink } from 'vue-router';
import { useAuthStore } from '../../../shared/stores/auth';
import { useCustomerReservationsStore } from '../../reservations/data/reservations-store';
import { computed } from 'vue';

const auth = useAuthStore();
const reservationsStore = useCustomerReservationsStore();

const activeCount = computed(() => {
  return reservationsStore.reservations.value.filter((r) => r.status === 'CHECKED_IN').length;
});
const upcomingCount = computed(() => {
  return reservationsStore.reservations.value.filter((r) => r.status === 'CONFIRMED').length;
});
</script>

<template>
  <main class="branch-mgmt-landing">
    <div class="mgmt-container">
      <header class="mgmt-header">
        <div class="mgmt-badge">
          <span class="gold-suit">♠</span>
          <span>THE PARLOR MANAGEMENT</span>
        </div>
        <h1 class="mgmt-title">Branch Management Console</h1>
        <p class="mgmt-subtitle">
          Operational control hub for branch managers and staff. Manage tables, live gaming sessions, walk-in customers, and counter snack tabs.
        </p>
      </header>

      <!-- Operational Hub Grid -->
      <section class="mgmt-cards-grid">
        <!-- Main Floor Operations Card -->
        <article class="mgmt-feature-card hero-card">
          <div class="feature-header">
            <span class="feature-icon">🎲</span>
            <div>
              <h2 class="feature-title">Live Floor & Table Operations</h2>
              <p class="feature-desc">
                Seat walk-in guests, monitor active playing times, add snacks & drinks to table tabs, and check bills with recomputed played fees.
              </p>
            </div>
          </div>

          <div class="feature-metrics">
            <div class="metric-box">
              <span class="metric-num">{{ activeCount }}</span>
              <span class="metric-name">Active Playing Tables</span>
            </div>
            <div class="metric-box">
              <span class="metric-num">{{ upcomingCount }}</span>
              <span class="metric-name">Upcoming Bookings</span>
            </div>
          </div>

          <div class="feature-actions">
            <RouterLink :to="{ name: 'StaffReservationsPage' }" class="btn-launch-hero">
              Launch Floor Console →
            </RouterLink>
          </div>
        </article>

        <!-- Profile & Workspace Navigation Card -->
        <article class="mgmt-feature-card">
          <div class="feature-header">
            <span class="feature-icon">👤</span>
            <div>
              <h2 class="feature-title">Manager & Staff Workspace</h2>
              <p class="feature-desc">
                Signed in as <strong>{{ auth.username }}</strong> (Roles: {{ auth.roles.join(', ') }}).
              </p>
            </div>
          </div>

          <div class="nav-links-list">
            <RouterLink :to="{ name: 'WorkspaceIndex' }" class="mgmt-nav-link">
              → Open General Workspace Hub
            </RouterLink>
            <RouterLink :to="{ name: 'AuthProfile' }" class="mgmt-nav-link">
              → Account & Staff Profile
            </RouterLink>
            <RouterLink :to="{ name: 'HomeIndex' }" class="mgmt-nav-link">
              → Public Storefront
            </RouterLink>
          </div>
        </article>
      </section>
    </div>
  </main>
</template>

<style scoped>
.branch-mgmt-landing {
  background: #fbf9f5;
  min-height: calc(100vh - 80px);
  padding: 32px 20px 60px;
  color: #1a2f24;
  font-family: Arial, Helvetica, sans-serif;
}

.mgmt-container {
  max-width: 1080px;
  margin: 0 auto;
}

.mgmt-header {
  margin-bottom: 32px;
}

.mgmt-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #c99a4e;
  background: #f3ede2;
  padding: 4px 10px;
  border-radius: 12px;
  margin-bottom: 10px;
}

.gold-suit {
  color: #c99a4e;
}

.mgmt-title {
  margin: 0 0 8px;
  font-size: 32px;
  font-weight: 800;
  color: #173224;
}

.mgmt-subtitle {
  margin: 0;
  font-size: 15px;
  color: #635b4c;
  max-width: 680px;
  line-height: 1.5;
}

.mgmt-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 24px;
}

.mgmt-feature-card {
  background: #ffffff;
  border: 1px solid #ded6c8;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 14px rgba(20, 43, 32, 0.04);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.hero-card {
  border-top: 5px solid #203c30;
}

.feature-header {
  display: flex;
  gap: 16px;
}

.feature-icon {
  font-size: 32px;
  line-height: 1;
}

.feature-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 800;
  color: #173224;
}

.feature-desc {
  margin: 0;
  font-size: 13px;
  color: #686051;
  line-height: 1.5;
}

.feature-metrics {
  display: flex;
  gap: 16px;
  background: #f8f6f0;
  border-radius: 12px;
  padding: 14px 18px;
}

.metric-box {
  display: flex;
  flex-direction: column;
}

.metric-num {
  font-size: 24px;
  font-weight: 800;
  color: #15803d;
}

.metric-name {
  font-size: 11px;
  font-weight: 700;
  color: #635a4b;
}

.btn-launch-hero {
  display: inline-block;
  background: #203c30;
  color: #ffffff;
  font-size: 14px;
  font-weight: 800;
  text-decoration: none;
  padding: 12px 24px;
  border-radius: 10px;
  box-shadow: 0 3px 8px rgba(32, 60, 48, 0.2);
  transition: all 120ms ease;
}

.btn-launch-hero:hover {
  background: #2c5242;
  transform: translateY(-1px);
}

.nav-links-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mgmt-nav-link {
  color: #203c30;
  font-weight: 700;
  font-size: 14px;
  text-decoration: none;
}

.mgmt-nav-link:hover {
  text-decoration: underline;
}
</style>

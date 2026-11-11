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
  <div class="branch-mgmt-landing">
    <div class="container">
      <header class="console-header-block">
        <div class="header-titles">
          <div class="header-eyebrow">
            <span class="gold-sparkle">✦</span>
            <span>THE PARLOR · MANAGEMENT CONSOLE</span>
          </div>
          <h1 class="page-title">Branch Management</h1>
          <p class="page-subtitle">
            Operational control console for branch managers and staff. Manage tables, live sessions, walk-in guests, and refreshments.
          </p>
        </div>
      </header>

      <!-- Operational Hub Grid -->
      <section class="mgmt-cards-grid">
        <!-- Main Floor Operations Card -->
        <article class="reservation-card hero-card">
          <div class="card-header">
            <div class="branch-meta">
              <span class="branch-name">
                <span class="gold-bullet">◆</span>
                Live Floor & Table Operations
              </span>
            </div>
            <span class="status-badge badge-confirmed">
              Active Module
            </span>
          </div>

          <div class="card-body">
            <p class="feature-text">
              Real-time table occupancy, check-in arrivals, snack/drink tabs, walk-in customer seating, and counter bill settlement.
            </p>

            <div class="feature-metrics">
              <div class="metric-box">
                <span class="metric-num">{{ activeCount }}</span>
                <span class="metric-name">Active Tables Playing</span>
              </div>
              <div class="metric-box">
                <span class="metric-num">{{ upcomingCount }}</span>
                <span class="metric-name">Upcoming Bookings</span>
              </div>
            </div>
          </div>

          <div class="card-footer">
            <RouterLink :to="{ name: 'StaffReservationsPage' }" class="btn btn-primary">
              Launch Branch Floor Console →
            </RouterLink>
          </div>
        </article>

        <!-- Manager Account & Portal Card -->
        <article class="reservation-card">
          <div class="card-header">
            <div class="branch-meta">
              <span class="branch-name">
                <span class="gold-bullet">◆</span>
                Staff & Manager Workspace
              </span>
            </div>
            <span class="status-badge badge-completed">
              {{ auth.username }}
            </span>
          </div>

          <div class="card-body">
            <p class="feature-text">
              Signed in as <strong>{{ auth.username }}</strong> with active realm roles: <code>{{ auth.roles.join(', ') }}</code>.
            </p>

            <div class="nav-links-list">
              <RouterLink :to="{ name: 'WorkspaceIndex' }" class="mgmt-nav-link">
                <span class="gold-bullet">◆</span> Open General Workspace Hub
              </RouterLink>
              <RouterLink :to="{ name: 'AuthProfile' }" class="mgmt-nav-link">
                <span class="gold-bullet">◆</span> Account & Staff Profile
              </RouterLink>
              <RouterLink :to="{ name: 'HomeIndex' }" class="mgmt-nav-link">
                <span class="gold-bullet">◆</span> Return to Public Storefront
              </RouterLink>
            </div>
          </div>
        </article>
      </section>
    </div>
  </div>
</template>

<style scoped>
.branch-mgmt-landing {
  background: var(--color-parlor-green-900);
  min-height: calc(100vh - 80px);
  padding: 36px 16px 80px;
  color: var(--color-parlor-green-900);
  font-family: 'FC Mee Sakul', 'Plus Jakarta Sans', system-ui, Arial, sans-serif;
}

.container {
  max-width: 960px;
  margin: 0 auto;
}

/* Page Header Block */
.console-header-block {
  background: var(--color-parlor-white);
  border: 1.5px solid var(--color-parlor-gold-500);
  border-radius: 12px;
  padding: 24px 28px;
  margin-bottom: 28px;
  box-shadow: 0 8px 24px color-mix(in srgb, var(--color-parlor-green-950) 28%, transparent);
}

.header-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: var(--color-parlor-gold-700);
  margin-bottom: 6px;
}

.gold-sparkle {
  color: var(--color-parlor-gold-500);
  font-size: 14px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 30px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
  letter-spacing: -0.02em;
}

.page-subtitle {
  margin: 0;
  font-size: 14.5px;
  color: var(--color-parlor-neutral-600);
  line-height: 1.5;
}

/* Cards Grid */
.mgmt-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 24px;
}

.reservation-card {
  background: var(--color-parlor-white);
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 12px;
  padding: 22px 24px;
  box-shadow: 0 4px 16px color-mix(in srgb, var(--color-parlor-green-900) 5%, transparent);
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.reservation-card:hover {
  border-color: var(--color-parlor-gold-500);
  box-shadow: 0 8px 24px color-mix(in srgb, var(--color-parlor-green-900) 10%, transparent), 0 0 12px color-mix(in srgb, var(--color-parlor-gold-500) 12%, transparent);
  transform: translateY(-2px);
}

.hero-card {
  border-color: var(--color-parlor-gold-500);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--color-parlor-neutral-150);
  padding-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.branch-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.branch-name {
  font-size: 17px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
  display: flex;
  align-items: center;
  gap: 6px;
}

.gold-bullet {
  color: var(--color-parlor-gold-500);
  font-size: 12px;
}

.status-badge {
  font-size: 12px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 14px;
  letter-spacing: 0.02em;
}

.badge-confirmed {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1px solid var(--color-parlor-gold-500);
  box-shadow: 0 0 8px color-mix(in srgb, var(--color-parlor-gold-500) 30%, transparent);
}

.badge-completed {
  background: var(--color-parlor-neutral-100);
  color: var(--color-parlor-neutral-600);
  border: 1px solid var(--color-parlor-neutral-300);
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1;
}

.feature-text {
  margin: 0;
  font-size: 13.5px;
  color: var(--color-parlor-neutral-600);
  line-height: 1.5;
}

.feature-metrics {
  display: flex;
  gap: 16px;
  background: var(--color-parlor-page);
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 8px;
  padding: 12px 16px;
}

.metric-box {
  display: flex;
  flex-direction: column;
}

.metric-num {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

.metric-name {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--color-parlor-neutral-600);
}

.card-footer {
  border-top: 1px solid var(--color-parlor-neutral-150);
  padding-top: 14px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 18px;
  border-radius: 8px;
  font-size: 13.5px;
  font-weight: 800;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.btn-primary {
  background: var(--color-parlor-green-900);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-gold-300);
  box-shadow: 0 2px 8px color-mix(in srgb, var(--color-parlor-green-900) 20%, transparent);
}

.btn-primary:hover {
  background: var(--color-parlor-green-800);
  border-color: var(--color-parlor-gold-300);
}

.nav-links-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
}

.mgmt-nav-link {
  color: var(--color-parlor-green-900);
  font-weight: 700;
  font-size: 13.5px;
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 6px;
}

.mgmt-nav-link:hover {
  text-decoration: underline;
  color: var(--color-parlor-gold-700);
}
</style>

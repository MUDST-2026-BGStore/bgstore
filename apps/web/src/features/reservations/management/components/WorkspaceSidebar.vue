<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import { useManagementWorkspace } from '../context';

const {
  auth,
  activeTab,
  isSidebarCollapsed,
  toggleSidebar,
  isManager,
  isAdmin,
  userRoleTitle,
  activeFloorReservations,
} = useManagementWorkspace();
const groups = computed(() => [
  {
    label: 'Operations',
    items: [
      { id: 'dashboard', label: 'Overview', icon: 'fa-border-all' },
      { id: 'floor', label: 'Tables & floor', icon: 'fa-table-cells-large' },
      { id: 'sessions', label: 'Live sessions', icon: 'fa-clock' },
      { id: 'bookings', label: 'Bookings', icon: 'fa-calendar-days' },
      { id: 'library', label: 'Game library', icon: 'fa-book-open' },
      {
        id: 'inventory',
        label: 'Inventory & fulfillment',
        icon: 'fa-boxes-stacked',
      },
    ],
  },
  ...(isManager.value
    ? [
        {
          label: 'Management',
          items: [
            {
              id: 'staff-roster',
              label: 'Branch staff',
              icon: 'fa-user-group',
            },
            {
              id: 'analytics',
              label: 'Revenue & analytics',
              icon: 'fa-chart-column',
            },
          ],
        },
      ]
    : []),
  ...(isAdmin.value
    ? [
        {
          label: 'Administration',
          items: [
            { id: 'settings', label: 'Venue settings', icon: 'fa-gear' },
            {
              id: 'policies',
              label: 'Venue policies',
              icon: 'fa-shield-halved',
            },
          ],
        },
      ]
    : []),
]);
function selectView(id: string) {
  // Navigation IDs are kept in the same union as the workspace's active view.
  activeTab.value = id as typeof activeTab.value;
  if (window.innerWidth <= 700) isSidebarCollapsed.value = true;
}
</script>

<template>
  <aside
    class="workspace-sidebar"
    :class="{ collapsed: isSidebarCollapsed }"
    aria-label="Workspace navigation"
  >
    <div class="workspace-brand">
      <span class="brand-mark" aria-hidden="true">♠</span>
      <div class="brand-copy">
        <strong>The Parlor</strong><span>Workspace</span>
      </div>
      <button
        type="button"
        class="sidebar-toggle"
        :aria-expanded="!isSidebarCollapsed"
        aria-controls="workspace-navigation"
        :aria-label="
          isSidebarCollapsed ? 'Expand navigation' : 'Collapse navigation'
        "
        :title="
          isSidebarCollapsed ? 'Expand navigation' : 'Collapse navigation'
        "
        @click="toggleSidebar"
      >
        <i
          class="fa-solid"
          :class="isSidebarCollapsed ? 'fa-angles-right' : 'fa-angles-left'"
          aria-hidden="true"
        ></i>
      </button>
    </div>
    <nav
      id="workspace-navigation"
      class="workspace-navigation"
      aria-label="Management views"
    >
      <section
        v-for="group in groups"
        :key="group.label"
        class="navigation-group"
        :aria-label="group.label"
      >
        <h2>{{ group.label }}</h2>
        <button
          v-for="item in group.items"
          :key="item.id"
          type="button"
          class="workspace-nav-item"
          :class="{ active: activeTab === item.id }"
          :aria-pressed="activeTab === item.id"
          :aria-current="activeTab === item.id ? 'page' : undefined"
          :aria-label="item.label"
          :title="item.label"
          @click="selectView(item.id)"
        >
          <i class="fa-solid" :class="item.icon" aria-hidden="true"></i>
          <span class="navigation-label">{{ item.label }}</span>
          <span
            v-if="item.id === 'sessions' && activeFloorReservations.length"
            class="session-count"
            >{{ activeFloorReservations.length }}</span
          >
        </button>
      </section>
    </nav>
    <div class="workspace-sidebar-footer">
      <RouterLink
        :to="{ name: 'HomeIndex' }"
        class="workspace-nav-item"
        aria-label="Customer storefront"
        title="Customer storefront"
        ><i
          class="fa-solid fa-arrow-up-right-from-square"
          aria-hidden="true"
        ></i
        ><span class="navigation-label">Customer storefront</span></RouterLink
      >
      <div class="workspace-user">
        <span class="user-initials" :title="auth.username || 'Staff member'">{{
          (auth.username || 'Staff').slice(0, 2).toUpperCase()
        }}</span>
        <div class="user-copy">
          <strong>{{ auth.username }}</strong
          ><span v-if="userRoleTitle">{{ userRoleTitle }}</span>
        </div>
        <button
          type="button"
          class="sidebar-signout"
          aria-label="Sign out"
          title="Sign out"
          @click="auth.logout()"
        >
          <i
            class="fa-solid fa-arrow-right-from-bracket"
            aria-hidden="true"
          ></i>
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.workspace-sidebar {
  width: 228px;
  flex-shrink: 0;
  overflow: hidden;
  transition: width 200ms cubic-bezier(0.2, 0.8, 0.2, 1);
  display: flex;
  flex-direction: column;
  min-height: 0;
  --ui-focus: #d9c18e;
  background: #183b2e;
  color: #f3efe3;
  border-right: 1px solid #153427;
}
.workspace-brand {
  position: relative;
  min-height: 76px;
  border-bottom: 1px solid #ffffff20;
}
.workspace-brand .sidebar-toggle {
  position: absolute;
  right: 16px;
  top: 22px;
}
.workspace-brand .brand-mark {
  position: absolute;
  left: 17px;
  top: 23px;
}
.workspace-brand .brand-copy {
  position: absolute;
  left: 48px;
  top: 15px;
  width: 122px;
  white-space: nowrap;
}
.brand-mark {
  color: #d9c18e;
  font-size: 29px;
  line-height: 1;
}
.brand-copy {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.brand-copy strong {
  font-family: Georgia, serif;
  font-size: 22px;
  font-weight: 500;
}
.brand-copy span {
  color: #d7dfd5;
  font-size: 12px;
}
.sidebar-toggle,
.sidebar-signout {
  display: inline-grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #f3efe3;
  cursor: pointer;
}
.sidebar-toggle:hover,
.sidebar-signout:hover {
  background: #2c503e;
}
.workspace-navigation {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px 0;
}
.navigation-group {
  margin-bottom: 16px;
}
.navigation-group h2 {
  margin: 10px 20px 6px;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 500;
  color: #c7d3c8;
}
.workspace-nav-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  min-height: 40px;
  padding: 8px 24px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: #eeeede;
  font: inherit;
  font-size: 13px;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
}
.workspace-nav-item > i {
  width: 18px;
  text-align: center;
  flex-shrink: 0;
  color: #c4d1c5;
}
.workspace-nav-item:hover {
  background: #284b39;
}
.workspace-nav-item.active {
  background: #ffffff16;
  color: #fff;
}
.workspace-nav-item.active > i {
  color: #fff;
}
.navigation-label {
  flex: 1;
  white-space: nowrap;
}
.brand-copy,
.brand-mark,
.navigation-label,
.session-count,
.navigation-group h2,
.user-copy,
.user-initials {
  transition: opacity 120ms ease;
}
.session-count {
  background: #ffffff1c;
  color: #f3efe3;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.workspace-sidebar-footer {
  padding: 8px 0;
  border-top: 1px solid #ffffff20;
}
.workspace-user {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 48px 4px 18px;
  min-height: 48px;
}
.workspace-user .sidebar-signout {
  position: absolute;
  right: 16px;
  bottom: 4px;
}
.user-initials {
  display: grid;
  place-items: center;
  background: #375b46;
  border: 1px solid #6b806a;
  width: 32px;
  height: 32px;
  border-radius: 4px;
  font-size: 12px;
}
.user-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  font-size: 12px;
}
.user-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
}
.user-copy span {
  color: #c7d3c8;
  font-size: 11px;
}
.collapsed {
  width: 68px;
}
.collapsed .brand-copy,
.collapsed .brand-mark,
.collapsed .navigation-label,
.collapsed .session-count,
.collapsed .navigation-group h2,
.collapsed .user-copy,
.collapsed .user-initials {
  opacity: 0;
  pointer-events: none;
}
@media (max-width: 700px) {
  .workspace-sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 30;
    height: 100dvh;
  }
  .workspace-sidebar:not(.collapsed) {
    width: min(280px, calc(100vw - 40px));
    box-shadow: 12px 0 32px #10251b40;
  }
}
@media (prefers-reduced-motion: reduce) {
  .workspace-sidebar,
  .brand-copy,
  .brand-mark,
  .navigation-label,
  .session-count,
  .navigation-group h2,
  .user-copy,
  .user-initials {
    transition: none;
  }
}
</style>

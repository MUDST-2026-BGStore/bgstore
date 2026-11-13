import { computed, ref, watch } from 'vue';
import { useAuthStore } from '../../../shared/stores/auth';

export function useWorkspaceNavigation() {
  const auth = useAuthStore();
  // Sidebar state & Role resolution
  const SIDEBAR_STORAGE_KEY = 'theparlor.staff_sidebar_collapsed';

  function getInitialSidebarCollapsed(): boolean {
    try {
      const saved = localStorage.getItem(SIDEBAR_STORAGE_KEY);
      if (saved !== null) {
        return saved === 'true';
      }
    } catch {
      // Fallback if localStorage is unavailable
    }
    return window.innerWidth <= 700;
  }

  // Synchronously initialized so initial render receives .collapsed immediately (no FOUC)
  const isSidebarCollapsed = ref(getInitialSidebarCollapsed());

  function toggleSidebar() {
    isSidebarCollapsed.value = !isSidebarCollapsed.value;
  }

  // Persist user manual toggles
  watch(isSidebarCollapsed, (val) => {
    try {
      localStorage.setItem(SIDEBAR_STORAGE_KEY, String(val));
    } catch {
      // ignore
    }
  });

  type NavTab =
    | 'dashboard'
    | 'floor'
    | 'sessions'
    | 'bookings'
    | 'library'
    | 'inventory'
    | 'staff-roster'
    | 'analytics'
    | 'settings'
    | 'policies';

  const activeTab = ref<NavTab>('dashboard');

  // Role hierarchy, from the server-verified session roles only.
  const userRoles = computed(() => auth.roles || []);
  const isAdmin = computed(() => userRoles.value.includes('admin'));
  const isManager = computed(
    () => isAdmin.value || userRoles.value.includes('manager'),
  );
  const isStaff = computed(
    () => isManager.value || userRoles.value.includes('staff'),
  );

  const roleBadgeLabel = computed(() => {
    if (isAdmin.value) return 'Administration';
    if (isManager.value) return 'Management';
    if (isStaff.value) return 'Operations';
    return '';
  });

  // No title is shown for an account without an operator role.
  const userRoleTitle = computed(() => {
    if (isAdmin.value) return 'Administrator';
    if (isManager.value) return 'Branch Manager';
    if (isStaff.value) return 'Floor Staff';
    return '';
  });

  // Guard management & admin tabs (inventory/stock is accessible to all staff)
  watch([activeTab, isManager, isAdmin], ([tab]) => {
    if (['staff-roster', 'analytics'].includes(tab) && !isManager.value) {
      activeTab.value = 'dashboard';
    }
    if (['settings', 'policies'].includes(tab) && !isAdmin.value) {
      activeTab.value = 'dashboard';
    }
  });

  // Breadcrumb context label for the active view (FortiSOAR-style location trail)
  const activeViewLabel = computed(() => {
    const labels: Record<NavTab, string> = {
      dashboard: 'Overview',
      floor: 'Tables & Floor',
      sessions: 'Live Sessions',
      bookings: 'Bookings & Timeline',
      library: 'Game Library',
      inventory: 'Inventory & Fulfillment',
      'staff-roster': 'Staff & Shifts',
      analytics: 'Revenue & Analytics',
      settings: 'Venue Settings',
      policies: 'Venue Policies',
    };
    return labels[activeTab.value];
  });
  return {
    auth,
    SIDEBAR_STORAGE_KEY,
    getInitialSidebarCollapsed,
    isSidebarCollapsed,
    toggleSidebar,
    activeTab,
    userRoles,
    isAdmin,
    isManager,
    roleBadgeLabel,
    userRoleTitle,
    activeViewLabel,
  };
}

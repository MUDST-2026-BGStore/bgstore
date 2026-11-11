<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRoute, RouterLink } from 'vue-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import {
  listBranches,
  listBranchReservations,
  checkInReservation,
  cancelReservation,
  markReservationNoShow,
  createStaffReservation,
  type Branch,
  type Reservation,
} from '@theparlor/api-client';
import TableBillModal from '../components/TableBillModal.vue';
import ManagementInventory from '../components/ManagementInventory.vue';
import { useCustomerReservationsStore } from '../data/reservations-store';
import { games, type CatalogGame } from '../../../shared/data/catalog';
import { useAuthStore } from '../../../shared/stores/auth';

const queryClient = useQueryClient();
const auth = useAuthStore();
const reservationsStore = useCustomerReservationsStore();
const route = useRoute();

// Sidebar state & Role resolution
const SIDEBAR_STORAGE_KEY = 'theparlor.staff_sidebar_collapsed';

function isManagementPath(
  path?: string,
  name?: string | symbol | null,
): boolean {
  if (
    path &&
    (path === '/workspace/management' ||
      path.startsWith('/workspace/management'))
  ) {
    return true;
  }
  if (name === 'WorkspaceManagement' || name === 'StaffReservationsPage') {
    return true;
  }
  return false;
}

function getInitialSidebarCollapsed(): boolean {
  // Opening or navigating to /workspace/management defaults to collapsed
  if (isManagementPath(route.path, route.name)) {
    return true;
  }
  try {
    const saved = localStorage.getItem(SIDEBAR_STORAGE_KEY);
    if (saved !== null) {
      return saved === 'true';
    }
  } catch {
    // Fallback if localStorage is unavailable
  }
  return false;
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

// When landing on /workspace/management from another path, enforce default collapsed state
watch(
  () => route.path,
  (newPath, oldPath) => {
    if (isManagementPath(newPath, route.name) && oldPath !== newPath) {
      isSidebarCollapsed.value = true;
    }
  },
);

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

// Role hierarchy
const userRoles = computed(() => auth.roles || []);
const isAdmin = computed(
  () =>
    userRoles.value.includes('admin') || userRoles.value.includes('developer'),
);
const isManager = computed(
  () => isAdmin.value || userRoles.value.includes('manager'),
);

const roleBadgeLabel = computed(() => {
  if (isAdmin.value) return 'ADMIN CONSOLE';
  if (userRoles.value.includes('manager')) return 'MANAGER CONSOLE';
  return 'STAFF CONSOLE';
});

const userRoleTitle = computed(() => {
  if (isAdmin.value) return 'Administrator';
  if (userRoles.value.includes('manager')) return 'Branch Manager';
  return 'Floor Staff';
});

// Guard management & admin tabs (inventory/stock is accessible to all staff)
watch(activeTab, (tab) => {
  if (['staff-roster', 'analytics'].includes(tab) && !isManager.value) {
    activeTab.value = 'dashboard';
  }
  if (['settings', 'policies'].includes(tab) && !isAdmin.value) {
    activeTab.value = 'dashboard';
  }
});

// Branch
const selectedBranchId = ref<string>('');
const currentTimeStr = ref(
  new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
);

// Time of day greeting
const greeting = computed(() => {
  const hr = new Date().getHours();
  if (hr < 12) return 'Good morning';
  if (hr < 17) return 'Good afternoon';
  return 'Good evening';
});

// Query branches
const branchesQuery = useQuery({
  queryKey: ['branches'],
  queryFn: ({ signal }) => listBranches({}, undefined, signal),
});
const branches = computed<Branch[]>(
  () => branchesQuery.data.value?.items ?? [],
);

watch(branches, (list) => {
  if (list.length > 0 && !selectedBranchId.value) {
    selectedBranchId.value = list[0].id;
  }
});

const selectedBranch = computed(() =>
  branches.value.find((b) => b.id === selectedBranchId.value),
);

// Query branch reservations
const reservationsQuery = useQuery({
  queryKey: ['listBranchReservations', selectedBranchId],
  queryFn: ({ signal }) =>
    listBranchReservations(selectedBranchId.value, {}, undefined, signal),
  enabled: computed(() => !!selectedBranchId.value),
});

// Auto 15-min No-Show Mutation
const autoNoShowMutation = useMutation({
  mutationFn: async ({ resId }: { resId: string }) => {
    return markReservationNoShow(selectedBranchId.value, resId, {
      reason:
        'Automatic No-Show: Customer did not arrive within 15 minutes of reservation start time.',
    });
  },
  onSuccess: () => {
    void queryClient.invalidateQueries({
      queryKey: ['listBranchReservations'],
    });
  },
});

const allReservations = computed<any[]>(() => {
  const now = Date.now();
  const apiItems = (reservationsQuery.data.value?.items ?? []).map((r) => {
    if (r.status === 'CONFIRMED' && !r.checkedInAt) {
      const startMs = new Date(r.startsAt).getTime();
      if (now - startMs > 15 * 60 * 1000) {
        void autoNoShowMutation.mutateAsync({ resId: r.id });
        return {
          ...r,
          status: 'NO_SHOW',
          notes: r.notes
            ? `${r.notes}\n[System]: Auto-marked No-Show after 15 min.`
            : '[System]: Auto-marked No-Show after 15 min.',
        };
      }
    }
    return r;
  });

  const localItems = reservationsStore.reservations.value.filter(
    (r) =>
      !selectedBranchId.value ||
      r.branchId === selectedBranchId.value ||
      r.branchName.toLowerCase() === selectedBranch.value?.name.toLowerCase(),
  );

  const combined = [...apiItems];
  for (const loc of localItems) {
    if (!combined.some((c) => c.id === loc.id)) {
      let status = loc.status;
      if (status === 'CONFIRMED' && !loc.checkedInAt) {
        const startMs = new Date(loc.startsAt).getTime();
        if (now - startMs > 15 * 60 * 1000) {
          status = 'NO_SHOW';
        }
      }

      combined.push({
        id: loc.id,
        branchId: loc.branchId,
        tableId: loc.tableId,
        startsAt: loc.startsAt,
        endsAt: loc.endsAt,
        checkedInAt: loc.checkedInAt,
        completedAt: loc.completedAt,
        partySize: loc.partySize,
        status,
        customer: {
          displayName: loc.customerName,
          phone: loc.customerPhone,
        } as any,
        notes: loc.notes,
        clientType: loc.clientType,
        source: (loc.clientType === 'WALK_IN' ? 'WALK_IN' : 'ONLINE') as any,
        hourlyRate: loc.hourlyRate,
      } as any);
    }
  }

  return combined;
});

// Category splits
const activeFloorReservations = computed(() => {
  return allReservations.value.filter((r) => r.status === 'CHECKED_IN');
});

const upcomingReservations = computed(() => {
  return allReservations.value.filter(
    (r) => r.status === 'CONFIRMED' || r.status === 'PENDING',
  );
});

// Tables Floor Plan Roster
interface FloorTable {
  id: string;
  number: number;
  seats: number;
  rate: number;
}

const branchFloorTables: FloorTable[] = [
  { id: '1', number: 1, seats: 2, rate: 150 },
  { id: '2', number: 2, seats: 4, rate: 200 },
  { id: '3', number: 3, seats: 4, rate: 200 },
  { id: '4', number: 4, seats: 4, rate: 200 },
  { id: '5', number: 5, seats: 6, rate: 300 },
  { id: '6', number: 6, seats: 6, rate: 300 },
];

function getTableStatus(table: FloorTable) {
  const occupied = activeFloorReservations.value.find((r) => {
    const tId = String(r.tableId || '');
    return (
      tId === table.id ||
      tId.endsWith(`-${table.id}`) ||
      tId.includes(`Table ${table.number}`)
    );
  });
  if (occupied) {
    return {
      state: 'OCCUPIED' as const,
      reservation: occupied,
    };
  }

  const reserved = upcomingReservations.value.find((r) => {
    const tId = String(r.tableId || '');
    return (
      tId === table.id ||
      tId.endsWith(`-${table.id}`) ||
      tId.includes(`Table ${table.number}`)
    );
  });
  if (reserved) {
    return {
      state: 'RESERVED' as const,
      reservation: reserved,
    };
  }

  return {
    state: 'FREE' as const,
    reservation: null,
  };
}

const occupiedTablesCount = computed(() => {
  return branchFloorTables.filter((t) => getTableStatus(t).state === 'OCCUPIED')
    .length;
});

const freeTablesCount = computed(() => {
  return branchFloorTables.length - occupiedTablesCount.value;
});

// Breadcrumb context label for the active view (FortiSOAR-style location trail)
const activeViewLabel = computed(() => {
  const labels: Record<NavTab, string> = {
    dashboard: 'Dashboard',
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

// Floor occupancy breakdown (FortiSIEM-style counter rail)
const floorOccupancy = computed(() => {
  const reserved = branchFloorTables.filter(
    (t) => getTableStatus(t).state === 'RESERVED',
  ).length;
  const occupied = occupiedTablesCount.value;
  const free = freeTablesCount.value;
  const total = branchFloorTables.length || 1;
  return [
    { state: 'In play', count: occupied, cls: 'occupied', pct: Math.round((occupied / total) * 100) },
    { state: 'Reserved', count: reserved, cls: 'reserved', pct: Math.round((reserved / total) * 100) },
    { state: 'Free', count: free, cls: 'free', pct: Math.round((free / total) * 100) },
  ];
});

// "Needs You Now" alerts (Most urgent first)
const needsYouItems = computed(() => {
  const alerts: any[] = [];
  for (const r of activeFloorReservations.value) {
    const elapsed = getElapsedMinutes(r);
    alerts.push({
      id: r.id,
      type: 'IN_PLAY',
      sev: 'med',
      title: `Table ${r.tableId} in play for ${elapsed} min`,
      subtitle: `${r.partySize} guests · ${getCustomerName(r.customer)} · Table charge: ฿${calculateCurrentPlayFee(r)}`,
      reservation: r,
    });
  }

  for (const r of upcomingReservations.value) {
    const startMs = new Date(r.startsAt).getTime();
    const diffMins = Math.round((Date.now() - startMs) / 60000);
    if (diffMins > 0) {
      alerts.push({
        id: r.id,
        type: 'LATE',
        sev: 'high',
        title: `Customer late ${diffMins}m: ${getCustomerName(r.customer)} (Table ${r.tableId})`,
        subtitle: `Scheduled ${formatTime(r.startsAt)} · Auto-released if 15m late`,
        reservation: r,
      });
    }
  }

  return alerts;
});

// Helper functions
function formatTime(iso: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function getElapsedMinutes(res: any): number {
  const start = new Date(res.checkedInAt || res.startsAt).getTime();
  const end = res.completedAt
    ? new Date(res.completedAt).getTime()
    : Date.now();
  return Math.max(1, Math.round((end - start) / 60000));
}

function calculateCurrentPlayFee(res: any): number {
  const mins = getElapsedMinutes(res);
  const rate = res.hourlyRate || 200;
  return Math.round((mins / 60) * rate);
}

function getCustomerName(c: any): string {
  if (!c) return 'Guest';
  return c.displayName || c.name || c.subject || 'Guest';
}

function getCustomerPhone(c: any): string {
  return c?.phone || '';
}

// Check in
const actionInProgress = ref<string | null>(null);
const actionError = ref<string>('');

const checkInMutation = useMutation({
  mutationFn: async (resId: string) => {
    return checkInReservation(selectedBranchId.value, resId);
  },
  onSuccess: () => {
    void queryClient.invalidateQueries({
      queryKey: ['listBranchReservations'],
    });
  },
  onError: (err: any) => {
    actionError.value =
      err?.response?.data?.message || err?.message || 'Check-in failed.';
  },
});

async function handleCheckIn(res: Reservation) {
  actionError.value = '';
  actionInProgress.value = res.id;
  try {
    reservationsStore.checkInReservation(res.id);
    await checkInMutation.mutateAsync(res.id);
  } finally {
    actionInProgress.value = null;
  }
}

// Cancel
const showCancelModal = ref(false);
const cancelTargetRes = ref<any>(null);
const cancelReason = ref('Customer called to cancel');

function openCancel(res: any) {
  cancelTargetRes.value = res;
  cancelReason.value = 'Customer called to cancel';
  showCancelModal.value = true;
}

const cancelMutation = useMutation({
  mutationFn: async ({ resId, reason }: { resId: string; reason: string }) => {
    return cancelReservation(selectedBranchId.value, resId, { reason });
  },
  onSuccess: () => {
    showCancelModal.value = false;
    cancelTargetRes.value = null;
    void queryClient.invalidateQueries({
      queryKey: ['listBranchReservations'],
    });
  },
  onError: (err: any) => {
    actionError.value =
      err?.response?.data?.message || err?.message || 'Cancel failed.';
  },
});

async function submitCancel() {
  if (!cancelTargetRes.value) return;
  actionError.value = '';
  reservationsStore.cancelReservation(
    cancelTargetRes.value.id,
    cancelReason.value,
  );
  await cancelMutation.mutateAsync({
    resId: cancelTargetRes.value.id,
    reason: cancelReason.value,
  });
}

// Walk-in Modal (Instant seating - NO pre-order snacks!)
const showWalkInModal = ref(false);
const walkInTableId = ref('1');
const walkInPartySize = ref(2);
const walkInDuration = ref(2);
const walkInGuestName = ref('');
const walkInPhone = ref('');
const walkInError = ref('');

function openWalkInForTable(table?: FloorTable) {
  if (table) {
    walkInTableId.value = table.id;
    walkInPartySize.value = table.seats;
  }
  walkInGuestName.value = '';
  walkInPhone.value = '';
  walkInError.value = '';
  showWalkInModal.value = true;
}

const staffCreateMutation = useMutation({
  mutationFn: async (payload: any) => {
    return createStaffReservation(selectedBranchId.value, payload);
  },
  onSuccess: () => {
    showWalkInModal.value = false;
    void queryClient.invalidateQueries({
      queryKey: ['listBranchReservations'],
    });
  },
  onError: (err: any) => {
    walkInError.value =
      err?.response?.data?.message || err?.message || 'Staff booking failed.';
  },
});

async function handleStaffWalkInSubmit() {
  walkInError.value = '';
  const matchedTable =
    branchFloorTables.find((t) => t.id === walkInTableId.value) ||
    branchFloorTables[0];
  const startsAtIso = new Date().toISOString();
  const endsAtIso = new Date(
    Date.now() + walkInDuration.value * 3600000,
  ).toISOString();

  reservationsStore.startWalkInSession({
    branchId: selectedBranchId.value,
    branchName: selectedBranch.value?.name ?? 'Siam',
    tableId: matchedTable.id,
    tableLabel: `Table ${matchedTable.number}`,
    partySize: walkInPartySize.value,
    customerName: walkInGuestName.value || 'Walk-in Guests',
    customerPhone: walkInPhone.value || undefined,
    hourlyRate: matchedTable.rate,
    durationHours: walkInDuration.value,
  });

  try {
    const payload = {
      source: 'WALK_IN',
      tableId: matchedTable.id,
      startsAt: startsAtIso,
      endsAt: endsAtIso,
      partySize: walkInPartySize.value,
      customer: {
        type: 'GUEST',
        name: walkInGuestName.value || 'Walk-in Guests',
        phone: walkInPhone.value || undefined,
      },
      games: [],
      notes: 'Counter Walk-in Session',
    };
    await staffCreateMutation.mutateAsync(payload);
  } catch {
    // local store maintains the session
  }

  showWalkInModal.value = false;
}

// ==========================================
// FEATURE 1: ASSIGNED GAMES PER TABLE & STRICT RULES
// Rule 1: Only start session can be assigned game
// Rule 2: Cannot assign game to assigned table
// ==========================================
const gameSearchQuery = ref('');
const gameStatusFilter = ref<'ALL' | 'AVAILABLE' | 'IN_USE'>('ALL');
const selectedGameToAssign = ref<CatalogGame | null>(null);
const assignGameTargetTableId = ref('1');
const showAssignGameModal = ref(false);
const assignModalSearch = ref('');

// Table assigned games tracking
const tableAssignedGames = ref<Record<string, string[]>>({
  '1': ['Azul'],
  '2': ['Wingspan'],
});

function getTableAssignedGames(tableId: string | number): string[] {
  return tableAssignedGames.value[String(tableId)] || [];
}

function getGameLocation(gameName: string): string | null {
  for (const [tId, gList] of Object.entries(tableAssignedGames.value)) {
    if (gList.some((g) => g.toLowerCase() === gameName.toLowerCase())) {
      return `Table ${tId}`;
    }
  }
  return null;
}

// Tables that have an active started session
const activeStartedTables = computed(() => {
  return branchFloorTables.filter(
    (t) => getTableStatus(t).state === 'OCCUPIED',
  );
});

// Tables eligible to receive a game:
// 1. Must have a started session (state === 'OCCUPIED')
// 2. Must not already have a game assigned (not an assigned table)
const eligibleTablesForGame = computed(() => {
  return activeStartedTables.value.filter(
    (t) => getTableAssignedGames(t.id).length === 0,
  );
});

function openAssignGame(game: CatalogGame) {
  selectedGameToAssign.value = game;
  assignModalSearch.value = '';
  // Default to first eligible table if available
  const firstEligible = eligibleTablesForGame.value[0];
  if (firstEligible) {
    assignGameTargetTableId.value = firstEligible.id;
  }
  showAssignGameModal.value = true;
}

function openAssignGameForTable(tableId: string | number) {
  const idStr = String(tableId);
  const tbl = branchFloorTables.find((t) => t.id === idStr);

  // Rule 1 check: Only started sessions can be assigned game
  if (!tbl || getTableStatus(tbl).state !== 'OCCUPIED') {
    return;
  }

  // Rule 2 check: Cannot assign game to assigned table
  if (getTableAssignedGames(idStr).length > 0) {
    return;
  }

  assignGameTargetTableId.value = idStr;
  const firstAvail = games.find((g) => !getGameLocation(g.name)) || games[0];
  selectedGameToAssign.value = firstAvail || null;
  assignModalSearch.value = '';
  showAssignGameModal.value = true;
}

function confirmAssignGame() {
  if (!selectedGameToAssign.value) return;
  const tId = assignGameTargetTableId.value;
  const tbl = branchFloorTables.find((t) => t.id === tId);

  // Strict enforcement:
  // 1. Only start session can be assigned game
  if (!tbl || getTableStatus(tbl).state !== 'OCCUPIED') {
    return;
  }
  // 2. Cannot assign game to assigned table
  if (getTableAssignedGames(tId).length > 0) {
    return;
  }
  // 3. Game must be available
  if (getGameLocation(selectedGameToAssign.value.name)) {
    return;
  }

  if (!tableAssignedGames.value[tId]) {
    tableAssignedGames.value[tId] = [];
  }
  tableAssignedGames.value[tId].push(selectedGameToAssign.value.name);
  showAssignGameModal.value = false;
}

function unassignGameFromTable(tableId: string | number, gameName: string) {
  const idStr = String(tableId);
  if (tableAssignedGames.value[idStr]) {
    tableAssignedGames.value[idStr] = tableAssignedGames.value[idStr].filter(
      (g) => g.toLowerCase() !== gameName.toLowerCase(),
    );
  }
}

function returnGameFromAnyTable(gameName: string) {
  for (const tId of Object.keys(tableAssignedGames.value)) {
    tableAssignedGames.value[tId] = tableAssignedGames.value[tId].filter(
      (g) => g.toLowerCase() !== gameName.toLowerCase(),
    );
  }
}

const filteredGames = computed(() => {
  const q = gameSearchQuery.value.trim().toLowerCase();
  return games.filter((g) => {
    const isAssigned = !!getGameLocation(g.name);
    if (gameStatusFilter.value === 'AVAILABLE' && isAssigned) return false;
    if (gameStatusFilter.value === 'IN_USE' && !isAssigned) return false;

    if (!q) return true;
    return (
      g.name.toLowerCase().includes(q) ||
      g.designer.toLowerCase().includes(q) ||
      g.categories.some((c) => c.toLowerCase().includes(q))
    );
  });
});

const assignableGamesList = computed(() => {
  const q = assignModalSearch.value.trim().toLowerCase();
  return games.filter((g) => {
    if (!q) return true;
    return (
      g.name.toLowerCase().includes(q) ||
      g.designer.toLowerCase().includes(q) ||
      g.categories.some((c) => c.toLowerCase().includes(q))
    );
  });
});

// Check if currently selected target table in modal is valid
const targetTableEligible = computed(() => {
  const tbl = branchFloorTables.find(
    (t) => t.id === assignGameTargetTableId.value,
  );
  if (!tbl) return false;
  const isStarted = getTableStatus(tbl).state === 'OCCUPIED';
  const hasGame = getTableAssignedGames(tbl.id).length > 0;
  return isStarted && !hasGame;
});

const canConfirmAssign = computed(() => {
  return (
    !!selectedGameToAssign.value &&
    !getGameLocation(selectedGameToAssign.value.name) &&
    targetTableEligible.value
  );
});

// ==========================================
// FEATURE 2: FOOD & DRINK STOCK MANAGEMENT
// Rule 3: Staff has + / - stock management;
// Higher roles (Manager & Admin) can create new food/drink types.
// ==========================================
export interface FoodDrinkStockItem {
  id: string;
  name: string;
  nameTh: string;
  category: 'beverages' | 'snacks' | 'tea_coffee' | 'bakery_desserts';
  price: number;
  costPrice: number;
  unit: string;
  currentStock: number;
  lowStockThreshold: number;
}

const branchStockItems = ref<FoodDrinkStockItem[]>([
  {
    id: 'bev-coca-cola',
    name: 'Coca-Cola Original (Can 325ml)',
    nameTh: 'โคคา-โคล่า รสชาติต้นตำรับ (กระป๋อง)',
    category: 'beverages',
    price: 35,
    costPrice: 18,
    unit: 'cans',
    currentStock: 48,
    lowStockThreshold: 12,
  },
  {
    id: 'bev-coke-zero',
    name: 'Coca-Cola Zero Sugar (Can 325ml)',
    nameTh: 'โค้กไม่มีน้ำตาล ซีโร่ (กระป๋อง)',
    category: 'beverages',
    price: 35,
    costPrice: 18,
    unit: 'cans',
    currentStock: 36,
    lowStockThreshold: 12,
  },
  {
    id: 'bev-sprite',
    name: 'Sprite Lemon-Lime (Can 325ml)',
    nameTh: 'สไปรท์ เลมอน-ไลม์ (กระป๋อง)',
    category: 'beverages',
    price: 35,
    costPrice: 18,
    unit: 'cans',
    currentStock: 24,
    lowStockThreshold: 10,
  },
  {
    id: 'bev-singha-soda',
    name: 'Singha Soda Water (Bottle 325ml)',
    nameTh: 'สิงห์โซดา (ขวดแก้ว)',
    category: 'beverages',
    price: 25,
    costPrice: 12,
    unit: 'bottles',
    currentStock: 30,
    lowStockThreshold: 10,
  },
  {
    id: 'bev-oishi-lemon',
    name: 'Oishi Honey Lemon Green Tea (Bottle 350ml)',
    nameTh: 'ชาเขียวโออิชิ น้ำผึ้งมะนาว',
    category: 'beverages',
    price: 30,
    costPrice: 16,
    unit: 'bottles',
    currentStock: 18,
    lowStockThreshold: 8,
  },
  {
    id: 'bev-lipton-tea',
    name: 'Lipton Iced Lemon Tea (Can 325ml)',
    nameTh: 'ชามะนาวลิปตัน (กระป๋อง)',
    category: 'beverages',
    price: 35,
    costPrice: 18,
    unit: 'cans',
    currentStock: 5,
    lowStockThreshold: 10,
  },
  {
    id: 'snack-truffle-fries',
    name: 'Truffle & Parmesan Hand-Cut Fries',
    nameTh: 'เฟรนช์ฟรายส์ทรัฟเฟิลพาร์เมซาน',
    category: 'snacks',
    price: 120,
    costPrice: 45,
    unit: 'portions',
    currentStock: 14,
    lowStockThreshold: 8,
  },
  {
    id: 'snack-churros',
    name: 'Spanish Cinnamon Sugar Churros',
    nameTh: 'ชูร์โรส ซินนามอน ดิปช็อกโกแลต',
    category: 'snacks',
    price: 95,
    costPrice: 35,
    unit: 'portions',
    currentStock: 3,
    lowStockThreshold: 8,
  },
  {
    id: 'snack-popcorn-butter',
    name: 'Movie-Style Warm Butter Popcorn',
    nameTh: 'ป๊อปคอร์นเนยอุ่น สไตล์โรงหนัง',
    category: 'snacks',
    price: 65,
    costPrice: 20,
    unit: 'bags',
    currentStock: 26,
    lowStockThreshold: 10,
  },
  {
    id: 'snack-crispy-calamari',
    name: 'Salt & Pepper Crispy Baby Squid',
    nameTh: 'หมึกกรอบพริกเกลือ',
    category: 'snacks',
    price: 140,
    costPrice: 55,
    unit: 'portions',
    currentStock: 0,
    lowStockThreshold: 6,
  },
  {
    id: 'tc-thai-tea',
    name: 'Hand-Brewed Traditional Thai Iced Tea',
    nameTh: 'ชาไทยเย็นสูตรโบราณ',
    category: 'tea_coffee',
    price: 75,
    costPrice: 25,
    unit: 'cups',
    currentStock: 32,
    lowStockThreshold: 10,
  },
  {
    id: 'tc-matcha-latte',
    name: 'Ceremonial Uji Matcha Latte',
    nameTh: 'อุจิมัทฉะลาเต้แท้เข้มข้น',
    category: 'tea_coffee',
    price: 95,
    costPrice: 35,
    unit: 'cups',
    currentStock: 2,
    lowStockThreshold: 10,
  },
  {
    id: 'tc-iced-americano',
    name: 'Cold Brew Single-Origin Americano',
    nameTh: 'อเมริกาโน่โคลด์บรูว์',
    category: 'tea_coffee',
    price: 70,
    costPrice: 20,
    unit: 'cups',
    currentStock: 40,
    lowStockThreshold: 10,
  },
  {
    id: 'bakery-butter-croissant',
    name: 'French Pure Butter Croissant',
    nameTh: 'ครัวซองต์เนยสดฝรั่งเศส',
    category: 'bakery_desserts',
    price: 80,
    costPrice: 30,
    unit: 'pieces',
    currentStock: 8,
    lowStockThreshold: 10,
  },
  {
    id: 'bakery-choco-chip-cookie',
    name: 'Sea Salt Dark Chocolate Cookie',
    nameTh: 'คุกกี้ดาร์กช็อกโกแลต ซีซอลต์',
    category: 'bakery_desserts',
    price: 60,
    costPrice: 22,
    unit: 'pieces',
    currentStock: 15,
    lowStockThreshold: 8,
  },
]);

const stockCategoryFilter = ref<string>('ALL');
const stockStatusFilter = ref<string>('ALL');

const filteredStockItems = computed(() => {
  return branchStockItems.value.filter((item) => {
    const matchesCat =
      stockCategoryFilter.value === 'ALL' ||
      item.category === stockCategoryFilter.value;
    let matchesStatus = true;
    if (stockStatusFilter.value === 'NEED_REORDER') {
      matchesStatus = item.currentStock <= item.lowStockThreshold;
    } else if (stockStatusFilter.value === 'OUT') {
      matchesStatus = item.currentStock === 0;
    } else if (stockStatusFilter.value === 'HEALTHY') {
      matchesStatus = item.currentStock > item.lowStockThreshold;
    }
    return matchesCat && matchesStatus;
  });
});

const totalStockUnits = computed(() =>
  branchStockItems.value.reduce((s, it) => s + it.currentStock, 0),
);
const healthyStockCount = computed(
  () =>
    branchStockItems.value.filter(
      (it) => it.currentStock > it.lowStockThreshold,
    ).length,
);
const lowStockCount = computed(
  () =>
    branchStockItems.value.filter(
      (it) => it.currentStock <= it.lowStockThreshold && it.currentStock > 0,
    ).length,
);
const outOfStockCount = computed(
  () => branchStockItems.value.filter((it) => it.currentStock === 0).length,
);

// Staff Quick +/- Adjustment
function adjustStockQty(item: FoodDrinkStockItem, delta: number) {
  item.currentStock = Math.max(0, item.currentStock + delta);
}

// Restock Modal
const showRestockModal = ref(false);
const restockTargetItem = ref<FoodDrinkStockItem | null>(null);
const restockBatchQty = ref(12);

function openRestockModal(item: FoodDrinkStockItem) {
  restockTargetItem.value = item;
  restockBatchQty.value = 12;
  showRestockModal.value = true;
}

function submitRestock() {
  if (restockTargetItem.value && restockBatchQty.value > 0) {
    restockTargetItem.value.currentStock += restockBatchQty.value;
  }
  showRestockModal.value = false;
  restockTargetItem.value = null;
}

// Higher Role Exclusive: Add New Food/Drink Type
const showAddFoodModal = ref(false);
const newFoodForm = ref({
  name: '',
  nameTh: '',
  category: 'beverages' as
    | 'beverages'
    | 'snacks'
    | 'tea_coffee'
    | 'bakery_desserts',
  price: 50,
  costPrice: 25,
  unit: 'cans',
  initialStock: 24,
  lowStockThreshold: 10,
});

function openAddFoodModal() {
  newFoodForm.value = {
    name: '',
    nameTh: '',
    category: 'beverages',
    price: 50,
    costPrice: 25,
    unit: 'cans',
    initialStock: 24,
    lowStockThreshold: 10,
  };
  showAddFoodModal.value = true;
}

function submitAddNewFood() {
  if (!newFoodForm.value.name.trim()) return;
  const newId = `custom-${Date.now()}`;
  branchStockItems.value.unshift({
    id: newId,
    name: newFoodForm.value.name.trim(),
    nameTh: newFoodForm.value.nameTh.trim() || newFoodForm.value.name.trim(),
    category: newFoodForm.value.category,
    price: Number(newFoodForm.value.price) || 0,
    costPrice: Number(newFoodForm.value.costPrice) || 0,
    unit: newFoodForm.value.unit || 'units',
    currentStock: Number(newFoodForm.value.initialStock) || 0,
    lowStockThreshold: Number(newFoodForm.value.lowStockThreshold) || 10,
  });
  showAddFoodModal.value = false;
}

function removeStockItem(itemId: string) {
  branchStockItems.value = branchStockItems.value.filter(
    (i) => i.id !== itemId,
  );
}

// Staff Snack Tab Ordering
const staffSnackReservation = ref<any>(null);
const staffSnackQuantities = ref<Record<string, number>>({});

function openAddSnacks(res: any) {
  staffSnackReservation.value = res;
  staffSnackQuantities.value = {};
}

function getStaffSnackQty(itemId: string): number {
  return staffSnackQuantities.value[itemId] || 0;
}

function adjustStaffSnackQty(item: FoodDrinkStockItem, delta: number) {
  const current = getStaffSnackQty(item.id);
  const next = Math.max(0, current + delta);
  if (next > item.currentStock) return; // Prevent ordering more than current stock
  if (next === 0) {
    delete staffSnackQuantities.value[item.id];
  } else {
    staffSnackQuantities.value[item.id] = next;
  }
}

const staffSnackSelectedItems = computed(() => {
  return Object.entries(staffSnackQuantities.value)
    .filter(([_, qty]) => qty > 0)
    .map(([id, qty]) => {
      const item = branchStockItems.value.find((f) => f.id === id);
      return {
        item: {
          id,
          name: item?.name || id,
          nameTh: item?.nameTh,
          price: item?.price || 0,
        },
        quantity: qty,
      };
    });
});

const staffSnackSubtotal = computed(() => {
  return staffSnackSelectedItems.value.reduce(
    (sum, it) => sum + it.item.price * it.quantity,
    0,
  );
});

function submitStaffSnackOrder() {
  if (
    !staffSnackReservation.value ||
    staffSnackSelectedItems.value.length === 0
  )
    return;

  // Deduct inventory stock in real time
  for (const it of staffSnackSelectedItems.value) {
    const stockItem = branchStockItems.value.find((s) => s.id === it.item.id);
    if (stockItem) {
      stockItem.currentStock = Math.max(
        0,
        stockItem.currentStock - it.quantity,
      );
    }
  }

  reservationsStore.addStaffSnackOrder(
    staffSnackReservation.value.id,
    staffSnackSelectedItems.value,
    'Added by staff at counter',
  );
  staffSnackReservation.value = null;
}

// Staff Table Bill Modal
const staffBillReservation = ref<any>(null);

function openCheckBill(res: any) {
  const found = reservationsStore.reservations.value.find(
    (r) => r.id === res.id,
  );
  if (found) {
    staffBillReservation.value = found;
  } else {
    staffBillReservation.value = {
      id: res.id,
      branchId: selectedBranchId.value,
      branchName: selectedBranch.value?.name ?? 'Siam',
      tableId: res.tableId,
      tableLabel: `Table ${res.tableId}`,
      startsAt: res.startsAt,
      endsAt: res.endsAt,
      checkedInAt: res.checkedInAt || res.startsAt,
      partySize: res.partySize,
      status: res.status,
      customerName: getCustomerName(res.customer),
      customerPhone: getCustomerPhone(res.customer) || undefined,
      hourlyRate: res.hourlyRate || 200,
      inPlayOrders: [],
    };
  }
}

function refreshAll() {
  currentTimeStr.value = new Date().toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  });
  void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
}

// ==========================================
// FEATURE 3: STAFF & SHIFTS (Manager+)
// ==========================================
interface StaffMember {
  id: string;
  name: string;
  role: 'Floor Lead' | 'Game Master' | 'Counter & POS' | 'Branch Manager';
  shift: string;
  status: 'ON_DUTY' | 'ON_BREAK' | 'OFF_DUTY';
  assignedZone: string;
}

const branchStaffRoster = ref<StaffMember[]>([
  {
    id: 's1',
    name: 'Krit Somchai',
    role: 'Floor Lead',
    shift: 'Evening (16:00 - 23:00)',
    status: 'ON_DUTY',
    assignedZone: 'Tables 1 - 3',
  },
  {
    id: 's2',
    name: 'Aom Pornpawee',
    role: 'Game Master',
    shift: 'Evening (16:00 - 23:00)',
    status: 'ON_DUTY',
    assignedZone: 'Tables 4 - 6 & Recommendations',
  },
  {
    id: 's3',
    name: 'Natt Worapat',
    role: 'Counter & POS',
    shift: 'Evening (16:00 - 23:00)',
    status: 'ON_DUTY',
    assignedZone: 'Counter, Bar & Bill Settlement',
  },
  {
    id: 's4',
    name: 'Thana Meechai',
    role: 'Branch Manager',
    shift: 'Full Day (10:00 - 20:00)',
    status: 'ON_DUTY',
    assignedZone: 'Branch Operations & Supervision',
  },
]);

// ==========================================
// FEATURE 4: REVENUE & ANALYTICS (Manager+)
// ==========================================
const totalTableFeesToday = computed(() => {
  return activeFloorReservations.value.reduce(
    (sum, r) => sum + calculateCurrentPlayFee(r),
    0,
  );
});
const todayFoodDrinkRevenue = ref(2450);
const todayTotalGross = computed(
  () => totalTableFeesToday.value + todayFoodDrinkRevenue.value,
);

// ==========================================
// FEATURE 5: VENUE SETTINGS & POLICIES (Admin)
// ==========================================
const venueSettings = ref({
  branchName: 'The Parlor · Siam Paragon',
  address: '991 Rama I Rd, Pathum Wan, Bangkok 10330',
  phone: '+66 2 123 4567',
  openTime: '10:00',
  closeTime: '23:00',
  totalTables: 20,
  peakHourSurcharge: false,
});

const venuePolicies = ref({
  noShowReleaseMinutes: 15,
  autoReleaseEnabled: true,
  playedTimeRecomputation: true,
  baseHourlyRate2Seats: 150,
  baseHourlyRate4Seats: 200,
  baseHourlyRate6Seats: 300,
  depositRequired: true,
  depositAmount: 100,
});
</script>

<template>
  <div class="gl-admin-layout">
    <!-- Left Navigation Sidebar -->
    <aside class="gl-sidebar" :class="{ collapsed: isSidebarCollapsed }">
      <!-- Brand & Role Indicator -->
      <div class="gl-sidebar-brand">
        <div class="brand-dice-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <rect
              x="2"
              y="2"
              width="20"
              height="20"
              rx="5"
              fill="#142b20"
              stroke="#c6ad7a"
              stroke-width="1.5"
            />
            <circle cx="7" cy="7" r="1.8" fill="#ffd98a" />
            <circle cx="17" cy="7" r="1.8" fill="#ffd98a" />
            <circle cx="12" cy="12" r="1.8" fill="#ffd98a" />
            <circle cx="7" cy="17" r="1.8" fill="#ffd98a" />
            <circle cx="17" cy="17" r="1.8" fill="#ffd98a" />
          </svg>
        </div>
        <div class="brand-text-col">
          <span class="brand-sub">{{ roleBadgeLabel }}</span>
          <span class="brand-name">The Parlor</span>
        </div>
      </div>

      <button
        type="button"
        class="sidebar-collapse-btn"
        :aria-label="isSidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        :title="isSidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        @click="toggleSidebar"
      >
        <span class="collapse-icon-box">
          <svg
            class="collapse-arrow-icon"
            :class="{ 'is-collapsed': isSidebarCollapsed }"
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <polyline points="11 17 6 12 11 7" />
            <polyline points="18 17 13 12 18 7" />
          </svg>
        </span>
        <span class="collapse-text">Collapse</span>
      </button>

      <!-- Nav Menu Items Divided by Operational Scope & Roles -->
      <nav class="sidebar-nav">
        <!-- 1. Operations (Staff, Manager, Admin) -->
        <div class="nav-section-title">
          <span>OPERATIONS</span>
        </div>

        <a
          href="#dashboard"
          class="nav-item"
          :class="{ active: activeTab === 'dashboard' }"
          :title="isSidebarCollapsed ? 'Dashboard' : undefined"
          @click.prevent="activeTab = 'dashboard'"
        >
          <span class="nav-icon-box">
            <svg
              class="nav-icon"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <rect x="3" y="3" width="7" height="7" />
              <rect x="14" y="3" width="7" height="7" />
              <rect x="14" y="14" width="7" height="7" />
              <rect x="3" y="14" width="7" height="7" />
            </svg>
          </span>
          <span class="nav-label">Dashboard</span>
        </a>

        <a
          href="#floor"
          class="nav-item"
          :class="{ active: activeTab === 'floor' }"
          :title="isSidebarCollapsed ? 'Tables & Floor' : undefined"
          @click.prevent="activeTab = 'floor'"
        >
          <span class="nav-icon-box">
            <svg
              class="nav-icon"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M3 3h18v18H3z" />
              <path d="M3 9h18M9 21V9" />
            </svg>
          </span>
          <span class="nav-label">Tables & Floor</span>
        </a>

        <a
          href="#sessions"
          class="nav-item"
          :class="{ active: activeTab === 'sessions' }"
          :title="isSidebarCollapsed ? 'Live Sessions' : undefined"
          @click.prevent="activeTab = 'sessions'"
        >
          <span class="nav-icon-box">
            <svg
              class="nav-icon"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
          </span>
          <span class="nav-label">Live Sessions</span>
          <span
            v-if="activeFloorReservations.length > 0"
            class="nav-badge-gold"
          >
            {{ activeFloorReservations.length }}
          </span>
        </a>

        <a
          href="#bookings"
          class="nav-item"
          :class="{ active: activeTab === 'bookings' }"
          :title="isSidebarCollapsed ? 'Bookings & Timeline' : undefined"
          @click.prevent="activeTab = 'bookings'"
        >
          <span class="nav-icon-box">
            <svg
              class="nav-icon"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
              <line x1="16" y1="2" x2="16" y2="6" />
              <line x1="8" y1="2" x2="8" y2="6" />
              <line x1="3" y1="10" x2="21" y2="10" />
            </svg>
          </span>
          <span class="nav-label">Bookings & Timeline</span>
        </a>

        <a
          href="#library"
          class="nav-item"
          :class="{ active: activeTab === 'library' }"
          :title="isSidebarCollapsed ? 'Game Library' : undefined"
          @click.prevent="activeTab = 'library'"
        >
          <span class="nav-icon-box">
            <svg
              class="nav-icon"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
              <path
                d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"
              />
            </svg>
          </span>
          <span class="nav-label">Game Library</span>
        </a>

        <!-- Refreshments & Stock Management (Available to staff, manager & admin) -->
        <a
          href="#inventory"
          class="nav-item"
          :class="{ active: activeTab === 'inventory' }"
          :title="isSidebarCollapsed ? 'Inventory & Fulfillment' : undefined"
          @click.prevent="activeTab = 'inventory'"
        >
          <span class="nav-icon-box">
            <svg
              class="nav-icon"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path
                d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
              />
              <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
              <line x1="12" y1="22.08" x2="12" y2="12" />
            </svg>
          </span>
          <span class="nav-label">Inventory & Fulfillment</span>
        </a>

        <!-- 2. Management (Manager & Admin only) -->
        <template v-if="isManager">
          <div class="nav-section-title mgmt-title">
            <span>MANAGEMENT</span>
            <span class="role-pill-tiny">Manager+</span>
          </div>

          <a
            href="#staff-roster"
            class="nav-item"
            :class="{ active: activeTab === 'staff-roster' }"
            :title="isSidebarCollapsed ? 'Staff & Shifts' : undefined"
            @click.prevent="activeTab = 'staff-roster'"
          >
            <span class="nav-icon-box">
              <svg
                class="nav-icon"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </span>
            <span class="nav-label">Staff & Shifts</span>
          </a>

          <a
            href="#analytics"
            class="nav-item"
            :class="{ active: activeTab === 'analytics' }"
            :title="isSidebarCollapsed ? 'Revenue & Analytics' : undefined"
            @click.prevent="activeTab = 'analytics'"
          >
            <span class="nav-icon-box">
              <svg
                class="nav-icon"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <line x1="18" y1="20" x2="18" y2="10" />
                <line x1="12" y1="20" x2="12" y2="4" />
                <line x1="6" y1="20" x2="6" y2="14" />
              </svg>
            </span>
            <span class="nav-label">Revenue & Analytics</span>
          </a>
        </template>

        <!-- 3. Administration (Admin only) -->
        <template v-if="isAdmin">
          <div class="nav-section-title admin-title">
            <span>ADMINISTRATION</span>
            <span class="role-pill-tiny admin">Admin</span>
          </div>

          <a
            href="#settings"
            class="nav-item"
            :class="{ active: activeTab === 'settings' }"
            :title="isSidebarCollapsed ? 'Venue Settings' : undefined"
            @click.prevent="activeTab = 'settings'"
          >
            <span class="nav-icon-box">
              <svg
                class="nav-icon"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <circle cx="12" cy="12" r="3" />
                <path
                  d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"
                />
              </svg>
            </span>
            <span class="nav-label">Venue Settings</span>
          </a>

          <a
            href="#policies"
            class="nav-item"
            :class="{ active: activeTab === 'policies' }"
            :title="isSidebarCollapsed ? 'Venue Policies' : undefined"
            @click.prevent="activeTab = 'policies'"
          >
            <span class="nav-icon-box">
              <svg
                class="nav-icon"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
              </svg>
            </span>
            <span class="nav-label">Venue Policies</span>
          </a>
        </template>

        <div class="nav-divider"></div>

        <!-- Global Utilities -->
        <RouterLink
          :to="{ name: 'HomeIndex' }"
          class="nav-item"
          :title="isSidebarCollapsed ? 'Public Store' : undefined"
        >
          <span class="nav-icon-box">
            <svg
              class="nav-icon"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
              <polyline points="9 22 9 12 15 12 15 22" />
            </svg>
          </span>
          <span class="nav-label">Public Store</span>
        </RouterLink>
      </nav>

      <!-- Sidebar User Footer -->
      <div class="sidebar-footer">
        <div class="user-profile-row">
          <div
            class="user-avatar-gold"
            :title="auth.username || 'Staff Member'"
          >
            {{ (auth.username || 'Staff').slice(0, 2).toUpperCase() }}
          </div>
          <div class="user-info-text">
            <strong class="user-name">{{
              auth.username || 'Staff Member'
            }}</strong>
            <span class="user-role-label">{{ userRoleTitle }}</span>
          </div>
        </div>
        <button type="button" class="btn-signout" @click="auth.logout()">
          Sign Out
        </button>
      </div>
    </aside>

    <!-- Main Content Area -->
    <div class="gl-main-wrapper">
      <!-- Top Header Navigation Bar -->
      <header class="gl-topbar">
        <div class="topbar-left">
          <div class="branch-selector-capsule">
            <select
              v-model="selectedBranchId"
              class="branch-select"
              aria-label="Branch"
            >
              <option v-for="b in branches" :key="b.id" :value="b.id">
                The Parlor · {{ b.name }} Branch
              </option>
            </select>
          </div>

          <div class="console-role-pill">
            <span>{{ userRoleTitle }}</span>
          </div>

          <nav class="crumb-trail" aria-label="Location">
            <span>{{ selectedBranch?.name || 'Branch' }}</span>
            <i class="fa-solid fa-angle-right" aria-hidden="true"></i>
            <span class="crumb-current">{{ activeViewLabel }}</span>
          </nav>
        </div>


        <div class="topbar-actions">
          <button
            type="button"
            class="icon-circle-btn"
            title="Refresh Live Data"
            @click="refreshAll"
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M23 4v6h-6" />
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
            </svg>
          </button>

          <RouterLink
            :to="{ name: 'HomeIndex' }"
            class="icon-circle-btn"
            title="View Customer Storefront"
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
              <line x1="8" y1="21" x2="16" y2="21" />
              <line x1="12" y1="17" x2="12" y2="21" />
            </svg>
          </RouterLink>

          <div class="topbar-avatar" :title="userRoleTitle">
            {{ (auth.username || 'Staff').slice(0, 2).toUpperCase() }}
          </div>
        </div>
      </header>

      <!-- Main Operational Console Body -->
      <div class="gl-content-scroll" tabindex="0" aria-label="Management workspace">
      <main class="gl-content">
        <!-- ========================================== -->
        <!-- VIEW 1: DASHBOARD TAB                      -->
        <!-- ========================================== -->
        <template v-if="activeTab === 'dashboard'">
          <!-- Block 1: Service Greeting Hero -->
          <section class="console-block hero-block">
            <div class="eyebrow-row">
              <span class="hero-eyebrow">
                {{ selectedBranch?.name || 'Siam' }} branch · Overview
              </span>
            </div>

            <div class="title-row">
              <h1 class="hero-title">{{ greeting }}</h1>
            </div>

            <p class="hero-subtitle">
              {{ allReservations.length }} bookings tonight — first arrival
              {{ formatTime(upcomingReservations[0]?.startsAt) || '7:30 PM' }},
              largest party of
              {{ Math.max(...branchFloorTables.map((t) => t.seats)) }},
              {{ needsYouItems.length }} item{{
                needsYouItems.length === 1 ? '' : 's'
              }}
              need attention.
            </p>

            <!-- Hero Action Buttons Bar -->
            <div class="hero-actions-bar">
              <button
                type="button"
                class="btn-primary-parlor"
                @click="openWalkInForTable()"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.5"
                >
                  <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                  <circle cx="8.5" cy="7" r="4" />
                  <line x1="20" y1="8" x2="20" y2="14" />
                  <line x1="23" y1="11" x2="17" y2="11" />
                </svg>
                <span>Seat walk-in</span>
              </button>

              <button
                type="button"
                class="btn-secondary-parlor"
                @click="activeTab = 'library'"
              >
                <svg
                  width="15"
                  height="15"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                  <path
                    d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"
                  />
                </svg>
                <span>Assign game</span>
              </button>

              <div class="actions-right">
                <span class="updated-time"
                  >Updated at {{ currentTimeStr }}</span
                >
                <button
                  type="button"
                  class="btn-refresh-parlor"
                  @click="refreshAll"
                >
                  <svg
                    width="13"
                    height="13"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <path d="M23 4v6h-6" />
                    <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
                  </svg>
                  <span>Refresh</span>
                </button>
              </div>
            </div>
          </section>

          <!-- Block 2: 4-Column Metric Ribbon Block -->
          <section class="console-block numbers-card-ribbon">
            <div class="num-col">
              <span class="col-label">Tonight</span>
              <div class="col-value">{{ allReservations.length }}</div>
              <div class="col-sub">
                party ·
                {{
                  allReservations.reduce((s, r) => s + (r.partySize || 2), 0)
                }}
                guests
              </div>
            </div>

            <div class="num-col">
              <span class="col-label">Tables in use</span>
              <div class="col-value">
                {{ occupiedTablesCount }} / {{ branchFloorTables.length }}
              </div>
              <div class="col-sub">{{ freeTablesCount }} free now</div>
            </div>

            <div class="num-col">
              <span class="col-label">Arriving</span>
              <div class="col-value">{{ upcomingReservations.length }}</div>
              <div class="col-sub">in the next hour</div>
            </div>

            <div class="num-col">
              <span class="col-label">Active tabs</span>
              <div class="col-value">{{ activeFloorReservations.length }}</div>
              <div class="col-sub">running refreshments on tab</div>
            </div>
          </section>

          <section class="dashboard-stock-watch" aria-labelledby="stock-watch-title">
            <div>
              <h2 id="stock-watch-title">Stock & fulfillment</h2>
            </div>
            <div class="stock-watch-summary">
              <span>Pantry: <strong>{{ lowStockCount }} low</strong> · <strong>{{ outOfStockCount }} out</strong></span>
              <button type="button" class="btn-secondary-parlor" @click="activeTab = 'inventory'">Review inventory <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></button>
            </div>
          </section>

          <!-- Block 4: Two-Column Lower Section: Floor Plan & Coming Up -->
          <div class="dash-grid">
            <div class="dash-main">
              <section class="console-block">
                <div class="section-card-header">
                  <div class="header-left">
                    <h2 class="section-title">Floor</h2>
                  </div>
                  <span class="usage-text"
                    >{{ occupiedTablesCount }} of
                    {{ branchFloorTables.length }} in use</span
                  >
                </div>

                <div class="floor-tables-grid">
                  <article
                    v-for="table in branchFloorTables"
                    :key="table.id"
                    class="floor-table-tile"
                    :class="getTableStatus(table).state.toLowerCase()"
                  >
                    <div class="tile-top-row">
                      <span class="table-num-lbl">{{ table.number }}</span>
                      <span class="table-seats-lbl"
                        >{{ table.seats }} seats · ฿{{ table.rate }}/hr</span
                      >
                    </div>

                    <!-- FREE TABLE: NO game assignment allowed -->
                    <template v-if="getTableStatus(table).state === 'FREE'">
                      <div class="tile-status-row">
                        <span class="state-badge badge-free">FREE</span>
                      </div>
                      <p class="tile-helper">Available rest of shift</p>
                      <button
                        type="button"
                        class="tile-action-link"
                        @click="openWalkInForTable(table)"
                      >
                        + Seat walk-in here <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
                      </button>
                    </template>

                    <!-- OCCUPIED TABLE: Session started -->
                    <template
                      v-else-if="getTableStatus(table).state === 'OCCUPIED'"
                    >
                      <div class="tile-status-row">
                        <span class="state-badge badge-occupied">PLAYING</span>
                        <span class="tile-time-pill">
                          {{
                            getElapsedMinutes(
                              getTableStatus(table).reservation,
                            )
                          }}m
                        </span>
                      </div>
                      <p class="tile-guest-name">
                        {{
                          getCustomerName(
                            getTableStatus(table).reservation?.customer,
                          )
                        }}
                      </p>
                      <div class="tile-sub-info">
                        Tab: ฿{{
                          calculateCurrentPlayFee(
                            getTableStatus(table).reservation,
                          )
                        }}
                      </div>

                      <!-- Assigned Game Chip (If assigned, cannot assign another game) -->
                      <div
                        v-if="getTableAssignedGames(table.id).length > 0"
                        class="tile-assigned-games-box"
                      >
                        <div class="assigned-chips-wrap">
                          <span class="game-chip-capsule">
                            <i class="fa-solid fa-dice" aria-hidden="true"></i>
                            {{ getTableAssignedGames(table.id)[0] }}
                            <button
                              type="button"
                              class="btn-chip-unassign"
                              title="Return game to shelf"
                              @click.stop="
                                unassignGameFromTable(
                                  table.id,
                                  getTableAssignedGames(table.id)[0],
                                )
                              "
                            >
                              <i class="fa-solid fa-xmark" aria-hidden="true"></i>
                            </button>
                          </span>
                        </div>
                      </div>

                      <div class="tile-actions-row">
                        <button
                          type="button"
                          class="tile-btn-small"
                          @click="
                            openAddSnacks(getTableStatus(table).reservation)
                          "
                        >
                          + Food/Drinks
                        </button>

                        <!-- Only show + Assign Game if table has no game yet! -->
                        <button
                          v-if="getTableAssignedGames(table.id).length === 0"
                          type="button"
                          class="tile-btn-assign"
                          @click.stop="openAssignGameForTable(table.id)"
                        >
                          + Assign Game
                        </button>

                        <button
                          type="button"
                          class="tile-btn-gold"
                          @click="
                            openCheckBill(getTableStatus(table).reservation)
                          "
                        >
                          Bill
                        </button>
                      </div>
                    </template>

                    <!-- RESERVED TABLE: NO game assignment allowed until started -->
                    <template
                      v-else-if="getTableStatus(table).state === 'RESERVED'"
                    >
                      <div class="tile-status-row">
                        <span class="state-badge badge-reserved">RESERVED</span>
                      </div>
                      <p class="tile-guest-name">
                        {{
                          getCustomerName(
                            getTableStatus(table).reservation?.customer,
                          )
                        }}
                      </p>
                      <p class="tile-due-time">
                        Due at
                        {{
                          formatTime(
                            getTableStatus(table).reservation?.startsAt,
                          )
                        }}
                      </p>
                      <button
                        type="button"
                        class="tile-action-link"
                        @click="
                          handleCheckIn(getTableStatus(table).reservation)
                        "
                      >
                        Check-in & Seat <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
                      </button>
                    </template>
                  </article>
                </div>
              </section>
            </div>

            <aside class="dash-side">
          <!-- FortiSIEM-style incident rail: severity-tagged attention boxes -->
          <section
            v-if="needsYouItems.length > 0"
            class="console-block needs-you-card"
          >
            <div class="panel-head">
              <div class="panel-title-row">
                <h2 class="panel-title">Needs you now</h2>
                <span class="count-pill warn">{{ needsYouItems.length }}</span>
              </div>
              <span class="panel-hint">Most urgent first</span>
            </div>

            <div class="incident-stack">
              <article
                v-for="alert in needsYouItems"
                :key="alert.id"
                class="incident-box"
                :class="alert.sev === 'high' ? 'sev-high' : 'sev-med'"
              >
                <div class="incident-body">
                  <strong class="incident-title">{{ alert.title }}</strong>
                  <p class="incident-sub">{{ alert.subtitle }}</p>
                  <div class="incident-actions">
                    <button
                      v-if="alert.type === 'IN_PLAY'"
                      type="button"
                      class="btn-secondary-parlor small"
                      @click="openAddSnacks(alert.reservation)"
                    >
                      + Add refreshments
                    </button>
                    <button
                      v-if="alert.type === 'IN_PLAY'"
                      type="button"
                      class="btn-primary-parlor small"
                      @click="openCheckBill(alert.reservation)"
                    >
                      Check bill <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
                    </button>
                    <button
                      v-if="alert.type === 'LATE'"
                      type="button"
                      class="btn-primary-parlor small"
                      @click="handleCheckIn(alert.reservation)"
                    >
                      Check in guest
                    </button>
                  </div>
                </div>
              </article>
            </div>
          </section>


              <!-- Coming Up Timeline -->
              <section class="console-block">
                <div class="section-card-header">
                  <div class="header-left">
                    <h2 class="section-title">Coming up</h2>
                    <span class="timeline-span">Next 2 hours</span>
                  </div>
                  <span class="count-bubble">{{
                    upcomingReservations.length
                  }}</span>
                </div>

                <div class="policy-rule-banner">
                  <span
                    >15-Minute Auto No-Show: Tables auto-release if customer is
                    >15m late.</span
                  >
                </div>

                <div
                  v-if="upcomingReservations.length === 0"
                  class="empty-timeline"
                >
                  <p>No arrivals scheduled in next 2 hours.</p>
                </div>

                <div v-else class="timeline-cards-list">
                  <article
                    v-for="res in upcomingReservations"
                    :key="res.id"
                    class="timeline-card"
                  >
                    <div class="timeline-time-col">
                      <span class="time-bold">{{
                        formatTime(res.startsAt)
                      }}</span>
                    </div>

                    <div class="timeline-card-content">
                      <div class="timeline-card-top">
                        <strong class="timeline-guest-name">
                          {{ getCustomerName(res.customer) }}
                        </strong>
                        <span class="party-tag"
                          >· {{ res.partySize }} guests</span
                        >
                        <span class="table-tag">Table {{ res.tableId }}</span>
                      </div>

                      <div class="timeline-actions">
                        <button
                          type="button"
                          class="btn-parlor-pill"
                          :disabled="actionInProgress === res.id"
                          @click="handleCheckIn(res)"
                        >
                          Arrived
                        </button>
                        <button
                          type="button"
                          class="btn-primary-parlor small"
                          :disabled="actionInProgress === res.id"
                          @click="handleCheckIn(res)"
                        >
                          Seat Table <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
                        </button>
                        <button
                          type="button"
                          class="btn-cancel-link"
                          @click="openCancel(res)"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  </article>
                </div>
              </section>
            </aside>
          </div>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 2: FULL FLOOR & TABLES TAB            -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'floor'">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">Venue</div>
                <h1 class="view-title">Tables & Floor Management</h1>
                <p class="view-subtitle">
                  Live table occupancy, assigned games, guest seating, and floor
                  status.
                </p>
              </div>
              <button
                type="button"
                class="btn-primary-parlor"
                @click="openWalkInForTable()"
              >
                + Seat Walk-in Customer
              </button>
            </div>

            <div class="floor-layout">
              <div class="floor-main">
            <div class="floor-tables-grid expanded">
              <article
                v-for="table in branchFloorTables"
                :key="table.id"
                class="floor-table-tile big"
                :class="getTableStatus(table).state.toLowerCase()"
              >
                <div class="tile-top-row">
                  <span class="table-num-lbl">Table {{ table.number }}</span>
                  <span class="table-seats-lbl"
                    >{{ table.seats }} Seats · ฿{{ table.rate }}/hr</span
                  >
                </div>

                <!-- FREE TABLE: NO game assignment -->
                <template v-if="getTableStatus(table).state === 'FREE'">
                  <div class="tile-status-row">
                    <span class="state-badge badge-free">FREE</span>
                  </div>
                  <p class="tile-helper">
                    Ready for walk-ins or scheduled bookings. Games can be
                    assigned after seating.
                  </p>
                  <button
                    type="button"
                    class="btn-primary-parlor small full"
                    @click="openWalkInForTable(table)"
                  >
                    + Seat Walk-in at Table {{ table.number }}
                  </button>
                </template>

                <!-- OCCUPIED TABLE: Session started -->
                <template
                  v-else-if="getTableStatus(table).state === 'OCCUPIED'"
                >
                  <div class="tile-status-row">
                    <span class="state-badge badge-occupied">IN PLAY</span>
                    <span class="tile-time-pill"
                      >{{
                        getElapsedMinutes(getTableStatus(table).reservation)
                      }}
                      mins played</span
                    >
                  </div>
                  <h3 class="guest-name-large">
                    {{
                      getCustomerName(
                        getTableStatus(table).reservation?.customer,
                      )
                    }}
                  </h3>
                  <p class="tile-sub-info">
                    Party of
                    {{ getTableStatus(table).reservation?.partySize }} · Current
                    Tab: ฿{{
                      calculateCurrentPlayFee(getTableStatus(table).reservation)
                    }}
                  </p>

                  <!-- Assigned Game Box on Occupied Table -->
                  <div class="tile-assigned-games-box" style="margin: 8px 0">
                    <div class="assigned-header-row">
                      <span class="assigned-label">Assigned game:</span>
                      <!-- Only show + Assign Game if table has no game yet -->
                      <button
                        v-if="getTableAssignedGames(table.id).length === 0"
                        type="button"
                        class="btn-tile-assign-game small"
                        @click.stop="openAssignGameForTable(table.id)"
                      >
                        + Assign Game
                      </button>
                    </div>
                    <div
                      v-if="getTableAssignedGames(table.id).length > 0"
                      class="assigned-chips-wrap"
                    >
                      <span class="game-chip-capsule">
                        <i class="fa-solid fa-dice" aria-hidden="true"></i>
                        {{ getTableAssignedGames(table.id)[0] }}
                        <button
                          type="button"
                          class="btn-chip-unassign"
                          title="Return game to shelf"
                          @click.stop="
                            unassignGameFromTable(
                              table.id,
                              getTableAssignedGames(table.id)[0],
                            )
                          "
                        >
                         <i class="fa-solid fa-xmark" aria-hidden="true"></i> Return
                        </button>
                      </span>
                    </div>
                    <div v-else class="no-game-text">
                      No game assigned yet · Click "+ Assign Game"
                    </div>
                  </div>

                  <div class="tile-actions-row">
                    <button
                      type="button"
                      class="tile-btn-small"
                      @click="openAddSnacks(getTableStatus(table).reservation)"
                    >
                      + Food/Drinks
                    </button>
                    <button
                      type="button"
                      class="tile-btn-gold"
                      @click="openCheckBill(getTableStatus(table).reservation)"
                    >
                      Check Bill & Settle
                    </button>
                  </div>
                </template>

                <!-- RESERVED TABLE: NO game assignment -->
                <template
                  v-else-if="getTableStatus(table).state === 'RESERVED'"
                >
                  <div class="tile-status-row">
                    <span class="state-badge badge-reserved">RESERVED</span>
                  </div>
                  <h3 class="guest-name-large">
                    {{
                      getCustomerName(
                        getTableStatus(table).reservation?.customer,
                      )
                    }}
                  </h3>
                  <p class="tile-due-time">
                    Due at
                    {{
                      formatTime(getTableStatus(table).reservation?.startsAt)
                    }}
                  </p>
                  <button
                    type="button"
                    class="btn-primary-parlor small full"
                    @click="handleCheckIn(getTableStatus(table).reservation)"
                  >
                    Check-in & Seat Table <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
                  </button>
                </template>
              </article>
            </div>
              </div>

              <aside class="floor-side">
                <section class="console-block occ-panel">
                  <div class="panel-head">
                    <h2 class="panel-title">Occupancy</h2>
                    <span class="panel-hint">{{ branchFloorTables.length }} tables</span>
                  </div>
                  <div class="occ-body">
                    <div
                      v-for="row in floorOccupancy"
                      :key="row.state"
                      class="occ-row"
                      :class="row.cls"
                    >
                      <span class="occ-dot"></span>
                      <span class="occ-label">{{ row.state }}</span>
                      <span class="occ-count">{{ row.count }}</span>
                      <span class="occ-bar"
                        ><span class="occ-fill" :style="{ width: row.pct + '%' }"></span
                      ></span>
                    </div>
                  </div>
                  <div class="occ-foot">Live state · refreshes with floor data</div>
                </section>
              </aside>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 3: LIVE SESSIONS & BILLING TAB        -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'sessions'">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">Table monitoring</div>
                <h1 class="view-title">Active Play Sessions & Tabs</h1>
                <p class="view-subtitle">
                  Monitor played time, assigned games, refreshments, and settle
                  bills with automatic late arrival recomputation.
                </p>
              </div>
              <button
                type="button"
                class="btn-primary-parlor"
                @click="openWalkInForTable()"
              >
                + Start Walk-in Session
              </button>
            </div>

            <div
              v-if="activeFloorReservations.length === 0"
              class="empty-state-box"
            >
              <div class="empty-icon" aria-hidden="true"><i class="fa-regular fa-clock"></i></div>
              <h3>No Active Play Sessions</h3>
              <p>
                There are no tables currently playing. Seat a walk-in guest or
                check in a reserved booking.
              </p>
            </div>

            <div
              v-else
              class="sessions-table-wrap"
              tabindex="0"
              aria-label="Live sessions"
            >
              <table class="parlor-table">
                <thead>
                  <tr>
                    <th>Table</th>
                    <th>Customer</th>
                    <th>Assigned Game</th>
                    <th>Checked in</th>
                    <th>Time Played</th>
                    <th>Hourly Rate</th>
                    <th>Table Fee</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="res in activeFloorReservations" :key="res.id">
                    <td>
                      <span class="table-badge">Table {{ res.tableId }}</span>
                    </td>
                    <td>
                      <strong>{{ getCustomerName(res.customer) }}</strong>
                      <span class="party-muted">({{ res.partySize }}p)</span>
                    </td>
                    <td>
                      <div class="session-games-cell">
                        <!-- If table already has game: show chip with return button -->
                        <div
                          v-if="getTableAssignedGames(res.tableId).length > 0"
                          class="session-chips-wrap"
                        >
                          <span class="game-chip-capsule">
                            {{ getTableAssignedGames(res.tableId)[0] }}
                            <button
                              type="button"
                              class="btn-chip-unassign"
                              title="Return to shelf"
                              @click.stop="
                                unassignGameFromTable(
                                  res.tableId,
                                  getTableAssignedGames(res.tableId)[0],
                                )
                              "
                            >
                              <i class="fa-solid fa-xmark" aria-hidden="true"></i>
                            </button>
                          </span>
                        </div>
                        <!-- If table has NO game: show + Assign Game button -->
                        <button
                          v-else
                          type="button"
                          class="btn-assign-mini"
                          @click="openAssignGameForTable(res.tableId)"
                        >
                          + Assign Game
                        </button>
                      </div>
                    </td>
                    <td>{{ formatTime(res.checkedInAt || res.startsAt) }}</td>
                    <td>
                      <span class="time-elapsed-badge">
                        {{ getElapsedMinutes(res) }} mins
                      </span>
                    </td>
                    <td>฿{{ res.hourlyRate || 200 }}/hr</td>
                    <td>
                      <strong class="fee-text"
                        >฿{{ calculateCurrentPlayFee(res) }}</strong
                      >
                    </td>
                    <td>
                      <div class="actions-cell">
                        <button
                          type="button"
                          class="btn-secondary-parlor small"
                          @click="openAddSnacks(res)"
                        >
                          + Tab
                        </button>
                        <button
                          type="button"
                          class="btn-primary-parlor small"
                          @click="openCheckBill(res)"
                        >
                          Check Bill
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 4: BOOKINGS & TIMELINE TAB            -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'bookings'">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">Shift schedule</div>
                <h1 class="view-title">Bookings & Reservations Timeline</h1>
                <p class="view-subtitle">
                  Automatic 15-minute no-show policy releases tables to walk-ins
                  if customers do not arrive.
                </p>
              </div>
            </div>

            <div class="bookings-list-wrap" tabindex="0" aria-label="Bookings">
              <table class="parlor-table">
                <thead>
                  <tr>
                    <th>Time</th>
                    <th>Table</th>
                    <th>Guest</th>
                    <th>Party</th>
                    <th>Status</th>
                    <th>Contact</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="res in allReservations" :key="res.id">
                    <td>
                      <strong>{{ formatTime(res.startsAt) }}</strong>
                    </td>
                    <td>Table {{ res.tableId }}</td>
                    <td>{{ getCustomerName(res.customer) }}</td>
                    <td>{{ res.partySize }} guests</td>
                    <td>
                      <span
                        class="status-tag"
                        :class="res.status.toLowerCase()"
                      >
                        {{ res.status === "CHECKED_IN" ? "Checked in" : res.status === "CONFIRMED" ? "Confirmed" : res.status === "CANCELLED" ? "Cancelled" : res.status === "COMPLETED" ? "Completed" : res.status === "NO_SHOW" ? "No show" : res.status === "PENDING" ? "Pending" : res.status }}
                      </span>
                    </td>
                    <td>{{ getCustomerPhone(res.customer) || '—' }}</td>
                    <td>
                      <div class="actions-cell">
                        <button
                          v-if="res.status === 'CONFIRMED'"
                          type="button"
                          class="btn-primary-parlor small"
                          @click="handleCheckIn(res)"
                        >
                          Check In
                        </button>
                        <button
                          v-if="res.status === 'CHECKED_IN'"
                          type="button"
                          class="btn-secondary-parlor small"
                          @click="openCheckBill(res)"
                        >
                          Check Bill
                        </button>
                        <button
                          v-if="res.status === 'CONFIRMED'"
                          type="button"
                          class="btn-cancel-link"
                          @click="openCancel(res)"
                        >
                          Cancel
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 5: GAME LIBRARY & AVAILABILITY        -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'library'">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">Collection & shelf inventory</div>
                <h1 class="view-title">Board Game Library</h1>
                <p class="view-subtitle">
                  Check game availability in real time, see which table is
                  currently playing, and assign to tables.
                </p>
              </div>
              <div class="search-games-input-wrap">
                <input
                  v-model="gameSearchQuery"
                  type="search"
                  placeholder="Filter games by name, designer, genre..."
                  class="parlor-input"
                />
              </div>
            </div>

            <!-- Game Availability Filter Pills -->
            <div class="library-filter-ribbon">
              <button
                type="button"
                class="filter-pill"
                :class="{ active: gameStatusFilter === 'ALL' }"
                @click="gameStatusFilter = 'ALL'"
              >
                All Games ({{ games.length }})
              </button>
              <button
                type="button"
                class="filter-pill avail"
                :class="{ active: gameStatusFilter === 'AVAILABLE' }"
                @click="gameStatusFilter = 'AVAILABLE'"
              >
                On shelf ({{
                  games.filter((g) => !getGameLocation(g.name)).length
                }})
              </button>
              <button
                type="button"
                class="filter-pill in-use"
                :class="{ active: gameStatusFilter === 'IN_USE' }"
                @click="gameStatusFilter = 'IN_USE'"
              >
                In use ({{
                  games.filter((g) => !!getGameLocation(g.name)).length
                }})
              </button>
            </div>

            <div class="games-library-grid">
              <article
                v-for="game in filteredGames"
                :key="game.id"
                class="game-catalog-card"
              >
                <!-- Game Status Banner (Available vs Unavailable) -->
                <div
                  class="game-status-strip"
                  :class="getGameLocation(game.name) ? 'unavail' : 'avail'"
                >
                  <span
                    v-if="getGameLocation(game.name)"
                    class="status-strip-text"
                  >
                    In use at {{ getGameLocation(game.name) }}
                  </span>
                  <span v-else class="status-strip-text"> On shelf </span>
                </div>

                <div class="game-card-header">
                  <h3 class="game-name">{{ game.name }}</h3>
                  <span class="game-difficulty-pill">{{
                    game.difficulty
                  }}</span>
                </div>
                <p class="game-designer">Designed by {{ game.designer }}</p>
                <div class="game-meta-row">
                  <span
                    ><i class="fa-solid fa-user-group" aria-hidden="true"></i> {{ game.players[0] }}–{{
                      game.players[1]
                    }}
                    Players</span
                  >
                  <span
                    ><i class="fa-regular fa-clock" aria-hidden="true"></i> {{ game.playMinutes[0] }}–{{
                      game.playMinutes[1]
                    }}
                    min</span
                  >
                </div>
                <p class="game-desc">{{ game.description }}</p>

                <div class="game-card-footer">
                  <button
                    v-if="getGameLocation(game.name)"
                    type="button"
                    class="btn-return-shelf full"
                    @click="returnGameFromAnyTable(game.name)"
                  >
                    <i class="fa-solid fa-rotate-left" aria-hidden="true"></i>
                    Return to Shelf (From {{ getGameLocation(game.name) }})
                  </button>
                  <button
                    v-else
                    type="button"
                    class="btn-primary-parlor small full"
                    @click="openAssignGame(game)"
                  >
                    Assign to table
                  </button>
                </div>
              </article>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 6: FOOD & DRINK STOCK MANAGEMENT      -->
        <!-- Rule 3: Staff has +/- stock management;    -->
        <!-- Higher roles (Manager/Admin) can add types -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'inventory'">
          <ManagementInventory :key="selectedBranchId" :branch-name="selectedBranch?.name || 'Select a branch'">
            <template #slot>
              <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">Kitchen & beverages</div>
                <h1 class="view-title">Food & Drink Stock Management</h1>
              </div>

              <!-- Higher Role Action: Add New Food/Drink Type -->
              <div class="header-mgmt-actions">
                <button
                  v-if="isManager"
                  type="button"
                  class="btn-primary-parlor"
                  @click="openAddFoodModal"
                >
                  Add food / drink item
                </button>
              </div>
            </div>

            <!-- Stock Metrics Ribbon -->
            <div class="stock-stats-ribbon">
              <div class="stock-stat-box">
                <span class="stat-lbl">Total units in stock</span>
                <div class="stat-num">{{ totalStockUnits }}</div>
                <span class="stat-sub"
                  >{{ branchStockItems.length }} tracked menu items</span
                >
              </div>
              <div class="stock-stat-box">
                <span class="stat-lbl">Healthy stock</span>
                <div class="stat-num text-green">{{ healthyStockCount }}</div>
                <span class="stat-sub">Sufficient pantry levels</span>
              </div>
              <div class="stock-stat-box">
                <span class="stat-lbl">Low stock alert</span>
                <div class="stat-num text-amber">{{ lowStockCount }}</div>
                <span class="stat-sub">Below threshold, reorder soon</span>
              </div>
              <div class="stock-stat-box">
                <span class="stat-lbl">Out of stock</span>
                <div class="stat-num text-red">{{ outOfStockCount }}</div>
                <span class="stat-sub">Requires supplier delivery</span>
              </div>
            </div>

            <!-- Category & Status Filters -->
            <div class="stock-filter-bar">
              <div class="filter-group">
                <span class="filter-lbl">Category:</span>
                <button
                  type="button"
                  class="pill-tab"
                  :class="{ active: stockCategoryFilter === 'ALL' }"
                  @click="stockCategoryFilter = 'ALL'"
                >
                  All ({{ branchStockItems.length }})
                </button>
                <button
                  type="button"
                  class="pill-tab"
                  :class="{ active: stockCategoryFilter === 'beverages' }"
                  @click="stockCategoryFilter = 'beverages'"
                >
                  <i class="fa-solid fa-whiskey-glass" aria-hidden="true"></i> Beverages
                </button>
                <button
                  type="button"
                  class="pill-tab"
                  :class="{ active: stockCategoryFilter === 'snacks' }"
                  @click="stockCategoryFilter = 'snacks'"
                >
                  <i class="fa-solid fa-cookie-bite" aria-hidden="true"></i> Snacks & Fries
                </button>
                <button
                  type="button"
                  class="pill-tab"
                  :class="{ active: stockCategoryFilter === 'tea_coffee' }"
                  @click="stockCategoryFilter = 'tea_coffee'"
                >
                  <i class="fa-solid fa-mug-hot" aria-hidden="true"></i> Tea & Coffee
                </button>
                <button
                  type="button"
                  class="pill-tab"
                  :class="{ active: stockCategoryFilter === 'bakery_desserts' }"
                  @click="stockCategoryFilter = 'bakery_desserts'"
                >
                  <i class="fa-solid fa-cake-candles" aria-hidden="true"></i> Bakery
                </button>
              </div>

              <div class="filter-group">
                <span class="filter-lbl">Alerts:</span>
                <button
                  type="button"
                  class="pill-tab alert"
                  :class="{ active: stockStatusFilter === 'NEED_REORDER' }"
                  @click="
                    stockStatusFilter =
                      stockStatusFilter === 'NEED_REORDER'
                        ? 'ALL'
                        : 'NEED_REORDER'
                  "
                >
                  <i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i> Needs reorder ({{ lowStockCount + outOfStockCount }})
                </button>
              </div>
            </div>

            <!-- Stock Table -->
            <div
              class="inventory-table-wrap"
              tabindex="0"
              aria-label="Inventory"
            >
              <table class="parlor-table stock-table">
                <thead>
                  <tr>
                    <th>Item & SKU</th>
                    <th>Category</th>
                    <th>Current Quantity</th>
                    <th>Stock Status</th>
                    <th>Retail Price</th>
                    <th v-if="isManager">Cost & Margin</th>
                    <th>Stock Stepper (+ / -)</th>
                    <th>Restock Delivery</th>
                    <th v-if="isManager">Catalog Action</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in filteredStockItems" :key="item.id">
                    <td>
                      <div class="stock-item-info">
                        <strong>{{ item.name }}</strong>
                        <div class="item-th-sub">{{ item.nameTh }}</div>
                        <!-- TODO: replace with real SKU codes once editions expose SKU identifiers -->
                        <code class="pantry-sku">{{ item.id.toUpperCase() }}</code>
                      </div>
                    </td>
                    <td>
                      <span class="category-tag">{{
                        item.category.replace('_', ' ')
                      }}</span>
                    </td>
                    <td>
                      <div
                        class="stock-qty-badge"
                        :class="{
                          empty: item.currentStock === 0,
                          low:
                            item.currentStock <= item.lowStockThreshold &&
                            item.currentStock > 0,
                        }"
                      >
                        <strong class="qty-big">{{ item.currentStock }}</strong>
                        <span class="qty-unit">{{ item.unit }}</span>
                      </div>
                    </td>
                    <td>
                      <span
                        v-if="item.currentStock === 0"
                        class="stock-status-tag out"
                      >
                        Out of stock
                      </span>
                      <span
                        v-else-if="item.currentStock <= item.lowStockThreshold"
                        class="stock-status-tag low"
                      >
                        Low stock (&le;{{ item.lowStockThreshold }})
                      </span>
                      <span v-else class="stock-status-tag healthy">
                        In stock
                      </span>
                    </td>
                    <td>
                      <strong class="price-val">฿{{ item.price }}</strong>
                    </td>
                    <td v-if="isManager">
                      <div class="margin-cell">
                        <span>Cost: ฿{{ item.costPrice }}</span>
                        <strong class="margin-pill">
                          {{
                            Math.round(
                              ((item.price - item.costPrice) / item.price) *
                                100,
                            )
                          }}% margin
                        </strong>
                      </div>
                    </td>
                    <td>
                      <div class="stepper-wrap compact">
                        <button
                          type="button"
                          class="step-btn small"
                          title="Decrease stock (-1)"
                          :disabled="item.currentStock <= 0"
                          @click="adjustStockQty(item, -1)"
                        >
                          -
                        </button>
                        <span class="step-val small">{{
                          item.currentStock
                        }}</span>
                        <button
                          type="button"
                          class="step-btn small"
                          title="Increase stock (+1)"
                          @click="adjustStockQty(item, 1)"
                        >
                          +
                        </button>
                      </div>
                    </td>
                    <td>
                      <div class="restock-actions-cell">
                        <button
                          type="button"
                          class="btn-batch-restock"
                          @click="adjustStockQty(item, 12)"
                        >
                          +12 Pack
                        </button>
                        <button
                          type="button"
                          class="btn-custom-restock"
                          @click="openRestockModal(item)"
                        >
                          Custom…
                        </button>
                      </div>
                    </td>
                    <td v-if="isManager">
                      <button
                        type="button"
                        class="btn-delete-sku"
                        title="Remove SKU from branch catalog"
                        @click="removeStockItem(item.id)"
                      >
                        <i class="fa-regular fa-trash-can" aria-hidden="true"></i>
                        Remove
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
            </template>
          </ManagementInventory>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 7: STAFF & SHIFTS (Manager & Admin)   -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'staff-roster' && isManager">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">Branch team</div>
                <h1 class="view-title">Staff & Shift Attendance</h1>
                <p class="view-subtitle">
                  Active shift supervision, table zone allocations, and roster
                  management.
                </p>
              </div>
              <span class="role-pill-gold">Branch Manager Access</span>
            </div>

            <div class="staff-roster-grid">
              <article
                v-for="staff in branchStaffRoster"
                :key="staff.id"
                class="staff-member-card"
              >
                <div class="staff-card-header">
                  <div class="staff-avatar-circle">
                    {{ staff.name.slice(0, 2).toUpperCase() }}
                  </div>
                  <div>
                    <h3 class="staff-name">{{ staff.name }}</h3>
                    <span class="staff-role-badge">{{ staff.role }}</span>
                  </div>
                </div>
                <div class="staff-card-body">
                  <p><strong>Shift:</strong> {{ staff.shift }}</p>
                  <p>
                    <strong>Assigned Zone:</strong> {{ staff.assignedZone }}
                  </p>
                  <div class="duty-status-row">
                    <span class="duty-lbl">On Duty · Clocked in</span>
                  </div>
                </div>
              </article>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 8: REVENUE & ANALYTICS (Manager+)     -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'analytics' && isManager">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">Revenue & occupancy</div>
                <h1 class="view-title">Revenue & Branch Analytics</h1>
                <p class="view-subtitle">
                  Today's real-time financial gross, hourly table time, and
                  refreshments sales.
                </p>
              </div>
              <span class="role-pill-gold">Managerial Insights</span>
            </div>

            <div class="analytics-ribbon-grid">
              <div class="analytics-card">
                <span class="analytics-label">Today's gross revenue</span>
                <div class="analytics-value">฿{{ todayTotalGross }}</div>
                <span class="analytics-sub"
                  >Table time + Counter refreshments</span
                >
              </div>
              <div class="analytics-card">
                <span class="analytics-label">Table playtime fees</span>
                <div class="analytics-value">฿{{ totalTableFeesToday }}</div>
                <span class="analytics-sub"
                  >Based on actual elapsed playtime</span
                >
              </div>
              <div class="analytics-card">
                <span class="analytics-label">F&B refreshments</span>
                <div class="analytics-value">฿{{ todayFoodDrinkRevenue }}</div>
                <span class="analytics-sub">Snacks & drinks tabs</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">Peak utilization</span>
                <div class="analytics-value">
                  {{
                    Math.round(
                      (occupiedTablesCount / branchFloorTables.length) * 100,
                    )
                  }}%
                </div>
                <span class="analytics-sub"
                  >{{ occupiedTablesCount }} of
                  {{ branchFloorTables.length }} tables playing</span
                >
              </div>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 9: VENUE SETTINGS (Admin only)        -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'settings' && isAdmin">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">Configuration</div>
                <h1 class="view-title">Venue Settings</h1>
                <p class="view-subtitle">
                  Manage branch identity, operating hours, and capacity limits.
                </p>
              </div>
              <span class="role-pill-gold admin">Super Admin Only</span>
            </div>

            <div class="settings-form-grid">
              <div class="form-group">
                <label class="parlor-label">Branch Name:</label>
                <input
                  v-model="venueSettings.branchName"
                  type="text"
                  class="parlor-input"
                />
              </div>
              <div class="form-group">
                <label class="parlor-label">Physical Address:</label>
                <input
                  v-model="venueSettings.address"
                  type="text"
                  class="parlor-input"
                />
              </div>
              <div class="form-row-2">
                <div class="form-group">
                  <label class="parlor-label">Opening Time:</label>
                  <input
                    v-model="venueSettings.openTime"
                    type="time"
                    class="parlor-input"
                  />
                </div>
                <div class="form-group">
                  <label class="parlor-label">Closing Time:</label>
                  <input
                    v-model="venueSettings.closeTime"
                    type="time"
                    class="parlor-input"
                  />
                </div>
              </div>
            </div>

            <!-- TODO: wire to a real venue settings update endpoint; local-only state right now -->
            <div class="settings-actions">
              <button type="button" class="btn-primary-parlor">Save changes</button>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 10: VENUE POLICIES (Admin only)       -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'policies' && isAdmin">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">Branch rules</div>
                <h1 class="view-title">Venue Operational Policies</h1>
                <p class="view-subtitle">
                  Configure 15-minute automatic no-show table releases,
                  played-time recalculation, and hourly rates.
                </p>
              </div>
              <span class="role-pill-gold admin">Super Admin Only</span>
            </div>

            <div class="policies-cards-list">
              <article class="policy-card">
                <div class="policy-card-top">
                  <div>
                    <h3 class="policy-card-title">
                      Automatic {{ venuePolicies.noShowReleaseMinutes }}-Minute
                      No-Show Release
                    </h3>
                    <p class="policy-card-desc">
                      When a customer reservation starts and they do not arrive
                      within {{ venuePolicies.noShowReleaseMinutes }} minutes,
                      the system automatically transitions their status to
                      NO_SHOW and releases their table for walk-ins.
                    </p>
                  </div>
                  <span class="policy-active-tag">Active & Enforced</span>
                </div>
              </article>

              <article class="policy-card">
                <div class="policy-card-top">
                  <div>
                    <h3 class="policy-card-title">
                      Dynamic Played-Time Recomputation
                    </h3>
                    <p class="policy-card-desc">
                      If customers arrive late, table play fee is dynamically
                      computed based strictly on actual elapsed time from
                      check-in to settlement at the counter (e.g. booked 9-10 at
                      ฿{{ venuePolicies.baseHourlyRate6Seats }}/hr, arrived 9:15
                      -> played 45 min -> billed ฿225).
                    </p>
                  </div>
                  <span class="policy-active-tag">Active & Enforced</span>
                </div>
              </article>

              <article class="policy-card">
                <div class="policy-card-top">
                  <div>
                    <h3 class="policy-card-title">
                      Standard Table Hourly Rates
                    </h3>
                    <p class="policy-card-desc">
                      2-Seater Table: ฿{{
                        venuePolicies.baseHourlyRate2Seats
                      }}/hr · 4-Seater Table: ฿{{
                        venuePolicies.baseHourlyRate4Seats
                      }}/hr · 6-Seater Table: ฿{{
                        venuePolicies.baseHourlyRate6Seats
                      }}/hr.
                    </p>
                  </div>
                  <span class="policy-active-tag">Default Matrix</span>
                </div>
              </article>
            </div>
          </section>
        </template>
      </main>
      </div>

      <!-- Assign Game to Table Modal (Enforces: 1. Only started session can receive game; 2. Cannot assign to already assigned table) -->
      <div
        v-if="showAssignGameModal"
        class="modal-backdrop"
        @click.self="showAssignGameModal = false"
      >
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">Table service</div>
              <h3 class="modal-title">Assign Game to Table</h3>
              <p class="modal-sub">
                Rule: Only tables with an active started session can receive a
                game, and each table can only hold one game at a time.
              </p>
            </div>
            <button
              type="button"
              class="close-x"
              @click="showAssignGameModal = false"
            >
              <i class="fa-solid fa-xmark" aria-hidden="true"></i>
            </button>
          </div>

          <div class="modal-form-body">
            <!-- Alert if no tables are currently eligible -->
            <div
              v-if="eligibleTablesForGame.length === 0"
              class="rule-warning-box"
            >
              <span class="gold-bullet"><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i></span>
              <span>
                No tables eligible right now. Games can only be assigned to
                tables with an <strong>active started session</strong> that do
                not already have an assigned game.
              </span>
            </div>

            <!-- 1. Choose Target Table -->
            <div class="form-section">
              <label class="modal-label">Select Active Playing Table:</label>
              <div class="tables-choice-grid">
                <button
                  v-for="t in branchFloorTables"
                  :key="t.id"
                  type="button"
                  class="tbl-choice-pill"
                  :class="{
                    active: assignGameTargetTableId === t.id,
                    disabled:
                      getTableStatus(t).state !== 'OCCUPIED' ||
                      getTableAssignedGames(t.id).length > 0,
                  }"
                  :disabled="
                    getTableStatus(t).state !== 'OCCUPIED' ||
                    getTableAssignedGames(t.id).length > 0
                  "
                  @click="assignGameTargetTableId = t.id"
                >
                  <strong>Table {{ t.number }}</strong>
                  <span
                    v-if="getTableStatus(t).state !== 'OCCUPIED'"
                    class="pill-reason"
                  >
                    (Not Playing)
                  </span>
                  <span
                    v-else-if="getTableAssignedGames(t.id).length > 0"
                    class="pill-reason has-game"
                  >
                    (Has {{ getTableAssignedGames(t.id)[0] }})
                  </span>
                  <span v-else class="pill-reason ready">
                    (Ready for game)
                  </span>
                </button>
              </div>
            </div>

            <!-- 2. Choose Game from Library -->
            <div class="form-section" style="margin-top: 14px">
              <label class="modal-label">Choose Game to Deliver:</label>
              <div class="search-games-input-wrap" style="margin-bottom: 8px">
                <input
                  v-model="assignModalSearch"
                  type="search"
                  placeholder="Filter available games..."
                  class="parlor-input"
                  style="width: 100%; font-size: 12px; padding: 6px 10px"
                />
              </div>

              <div class="modal-games-list">
                <div
                  v-for="g in assignableGamesList"
                  :key="g.id"
                  class="modal-game-choice-row"
                  :class="{
                    selected: selectedGameToAssign?.id === g.id,
                    'in-use': !!getGameLocation(g.name),
                  }"
                  @click="
                    !getGameLocation(g.name) && (selectedGameToAssign = g)
                  "
                >
                  <div class="game-choice-info">
                    <strong class="game-choice-name">{{ g.name }}</strong>
                    <span class="game-choice-sub">
                      <i class="fa-solid fa-user-group" aria-hidden="true"></i> {{ g.players[0] }}-{{ g.players[1] }} players ·
                      <i class="fa-regular fa-clock" aria-hidden="true"></i> {{ g.playMinutes[0] }}-{{ g.playMinutes[1] }}m
                    </span>
                  </div>
                  <div class="game-choice-status">
                    <span
                      class="mini-status-tag"
                      :class="getGameLocation(g.name) ? 'unavail' : 'avail'"
                    >
                      {{
                        getGameLocation(g.name)
                          ? `In use (${getGameLocation(g.name)})`
                          : 'Available on shelf'
                      }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button
              type="button"
              class="btn-secondary-parlor"
              @click="showAssignGameModal = false"
            >
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="!canConfirmAssign"
              @click="confirmAssignGame"
            >
              Assign {{ selectedGameToAssign?.name || 'Game' }} to Table
              {{ assignGameTargetTableId }} <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
            </button>
          </div>
        </div>
      </div>

      <!-- Higher Role Modal: Add New Food / Drink Type (Manager & Admin only) -->
      <div
        v-if="showAddFoodModal && isManager"
        class="modal-backdrop"
        @click.self="showAddFoodModal = false"
      >
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">Catalog management</div>
              <h3 class="modal-title">Add New Food / Drink Item</h3>
              <p class="modal-sub">
                Define a new refreshment item, retail price, cost margins, and
                initial stock.
              </p>
            </div>
            <button
              type="button"
              class="close-x"
              @click="showAddFoodModal = false"
            >
              <i class="fa-solid fa-xmark" aria-hidden="true"></i>
            </button>
          </div>

          <div class="modal-form-body">
            <div class="form-group">
              <label class="modal-label">Item Name (English):</label>
              <input
                v-model="newFoodForm.name"
                type="text"
                placeholder="e.g. Yuzu Sparkling Cold Brew"
                class="modal-input"
                required
              />
            </div>

            <div class="form-group">
              <label class="modal-label">Item Name (Thai):</label>
              <input
                v-model="newFoodForm.nameTh"
                type="text"
                placeholder="e.g. โคลด์บรูว์ยูสุโซดา"
                class="modal-input"
              />
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="modal-label">Category:</label>
                <select v-model="newFoodForm.category" class="modal-select">
                  <option value="beverages">Beverages</option>
                  <option value="snacks">Snacks & Fries</option>
                  <option value="tea_coffee">Tea & Coffee</option>
                  <option value="bakery_desserts">Bakery & Desserts</option>
                </select>
              </div>

              <div class="form-group">
                <label class="modal-label">Serving Unit:</label>
                <select v-model="newFoodForm.unit" class="modal-select">
                  <option value="cans">cans (กระป๋อง)</option>
                  <option value="bottles">bottles (ขวด)</option>
                  <option value="cups">cups (แก้ว)</option>
                  <option value="portions">portions (ที่/จาน)</option>
                  <option value="pieces">pieces (ชิ้น)</option>
                  <option value="bags">bags (ซอง)</option>
                </select>
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="modal-label">Selling Price (฿):</label>
                <input
                  v-model.number="newFoodForm.price"
                  type="number"
                  min="0"
                  class="modal-input"
                />
              </div>

              <div class="form-group">
                <label class="modal-label">Cost Price (฿):</label>
                <input
                  v-model.number="newFoodForm.costPrice"
                  type="number"
                  min="0"
                  class="modal-input"
                />
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="modal-label">Initial Stock Quantity:</label>
                <input
                  v-model.number="newFoodForm.initialStock"
                  type="number"
                  min="0"
                  class="modal-input"
                />
              </div>

              <div class="form-group">
                <label class="modal-label">Low Stock Threshold:</label>
                <input
                  v-model.number="newFoodForm.lowStockThreshold"
                  type="number"
                  min="1"
                  class="modal-input"
                />
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button
              type="button"
              class="btn-secondary-parlor"
              @click="showAddFoodModal = false"
            >
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="!newFoodForm.name.trim()"
              @click="submitAddNewFood"
            >
              Add Item to Menu & Stock <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
            </button>
          </div>
        </div>
      </div>

      <!-- Batch Restock Modal -->
      <div
        v-if="showRestockModal && restockTargetItem"
        class="modal-backdrop"
        @click.self="showRestockModal = false"
      >
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">Pantry intake</div>
              <h3 class="modal-title">Restock Inventory Batch</h3>
              <p class="modal-sub">
                Add incoming shipment to
                <strong>{{ restockTargetItem.name }}</strong
                >.
              </p>
            </div>
            <button
              type="button"
              class="close-x"
              @click="showRestockModal = false"
            >
              <i class="fa-solid fa-xmark" aria-hidden="true"></i>
            </button>
          </div>

          <div class="modal-form-body">
            <div class="stock-current-preview">
              <span>Current Stock on Hand:</span>
              <strong
                >{{ restockTargetItem.currentStock }}
                {{ restockTargetItem.unit }}</strong
              >
            </div>

            <div class="form-group" style="margin-top: 14px">
              <label class="modal-label"
                >Quantity Received (+{{ restockTargetItem.unit }}):</label
              >
              <div class="stepper-wrap">
                <button
                  type="button"
                  class="step-btn"
                  :disabled="restockBatchQty <= 1"
                  @click="restockBatchQty = Math.max(1, restockBatchQty - 6)"
                >
                  -6
                </button>
                <input
                  v-model.number="restockBatchQty"
                  type="number"
                  min="1"
                  class="modal-input"
                  style="width: 100px; text-align: center; font-weight: 600"
                />
                <button
                  type="button"
                  class="step-btn"
                  @click="restockBatchQty += 6"
                >
                  +6
                </button>
                <button
                  type="button"
                  class="btn-secondary-parlor small"
                  @click="restockBatchQty += 12"
                >
                  +12 Case
                </button>
              </div>
            </div>

            <div class="new-stock-preview">
              <span>New Stock after Restock:</span>
              <strong class="text-green"
                >{{ restockTargetItem.currentStock + restockBatchQty }}
                {{ restockTargetItem.unit }}</strong
              >
            </div>
          </div>

          <div class="modal-footer">
            <button
              type="button"
              class="btn-secondary-parlor"
              @click="showRestockModal = false"
            >
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              @click="submitRestock"
            >
              Confirm Intake (+{{ restockBatchQty }}
              {{ restockTargetItem.unit }}) <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
            </button>
          </div>
        </div>
      </div>

      <!-- Staff Table Bill & Settlement Modal -->
      <TableBillModal
        v-if="staffBillReservation"
        :reservation="staffBillReservation"
        :is-staff="true"
        @close="staffBillReservation = null"
        @settled="
          staffBillReservation = null;
          queryClient.invalidateQueries({
            queryKey: ['listBranchReservations'],
          });
        "
      />

      <!-- Cancel Confirmation Modal -->
      <div
        v-if="showCancelModal"
        class="modal-backdrop"
        @click.self="showCancelModal = false"
      >
        <div class="modal-card">
          <h3 class="modal-title">Cancel Reservation</h3>
          <p class="modal-sub">
            Cancel reservation for
            <strong>{{ getCustomerName(cancelTargetRes?.customer) }}</strong> at
            Table {{ cancelTargetRes?.tableId }}. Table will be made available
            immediately.
          </p>

          <div class="form-group">
            <label class="modal-label">Reason:</label>
            <input
              v-model="cancelReason"
              type="text"
              class="modal-input"
              required
            />
          </div>

          <div class="modal-footer">
            <button
              type="button"
              class="btn-secondary-parlor"
              @click="showCancelModal = false"
            >
              Keep Booking
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="cancelMutation.isPending.value"
              @click="submitCancel"
            >
              Confirm Cancellation
            </button>
          </div>
        </div>
      </div>

      <!-- Quick Fast Walk-in Modal (Instant Seating - NO pre-order snacks!) -->
      <div
        v-if="showWalkInModal"
        class="modal-backdrop"
        @click.self="showWalkInModal = false"
      >
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">Counter seating</div>
              <h3 class="modal-title">Seat Walk-in Guests</h3>
              <p class="modal-sub">
                Select table and seat immediately. Refreshments can be added on
                table tab anytime.
              </p>
            </div>
            <button
              type="button"
              class="close-x"
              @click="showWalkInModal = false"
            >
              <i class="fa-solid fa-xmark" aria-hidden="true"></i>
            </button>
          </div>

          <div class="modal-form-body">
            <!-- Table Selection -->
            <div class="form-section">
              <label class="modal-label">Select Table:</label>
              <div class="tables-choice-grid">
                <button
                  v-for="tbl in branchFloorTables"
                  :key="tbl.id"
                  type="button"
                  class="tbl-choice-pill"
                  :class="{
                    active: walkInTableId === tbl.id,
                    occupied: getTableStatus(tbl).state === 'OCCUPIED',
                  }"
                  @click="
                    walkInTableId = tbl.id;
                    walkInPartySize = tbl.seats;
                  "
                >
                  <strong>Table {{ tbl.number }}</strong>
                  <span>({{ tbl.seats }}p · ฿{{ tbl.rate }}/hr)</span>
                </button>
              </div>
            </div>

            <!-- Party Size & Duration -->
            <div class="form-row-2">
              <div class="form-group">
                <label class="modal-label">Guests in Party:</label>
                <div class="stepper-wrap">
                  <button
                    type="button"
                    class="step-btn"
                    :disabled="walkInPartySize <= 1"
                    @click="walkInPartySize = Math.max(1, walkInPartySize - 1)"
                  >
                    -
                  </button>
                  <span class="step-val">{{ walkInPartySize }}</span>
                  <button
                    type="button"
                    class="step-btn"
                    @click="walkInPartySize++"
                  >
                    +
                  </button>
                </div>
              </div>

              <div class="form-group">
                <label class="modal-label">Duration:</label>
                <select v-model.number="walkInDuration" class="modal-select">
                  <option :value="1">1 Hour</option>
                  <option :value="2">2 Hours</option>
                  <option :value="3">3 Hours</option>
                  <option :value="4">4 Hours</option>
                </select>
              </div>
            </div>

            <!-- Guest Name -->
            <div class="form-group">
              <label class="modal-label">Guest Name (Optional):</label>
              <input
                v-model="walkInGuestName"
                type="text"
                placeholder="Walk-in Guests"
                class="modal-input"
              />
            </div>

            <div v-if="walkInError" class="alert-banner">{{ walkInError }}</div>
          </div>

          <div class="modal-footer">
            <button
              type="button"
              class="btn-secondary-parlor"
              @click="showWalkInModal = false"
            >
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="staffCreateMutation.isPending.value"
              @click="handleStaffWalkInSubmit"
            >
              <span v-if="staffCreateMutation.isPending.value"
                >Seating Table…</span
              >
              <span v-else>Seat & Start Playing Now <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></span>
            </button>
          </div>
        </div>
      </div>

      <!-- Quick Add Snacks Modal -->
      <div
        v-if="staffSnackReservation"
        class="modal-backdrop"
        @click.self="staffSnackReservation = null"
      >
        <div class="modal-card snack-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">Table tab</div>
              <h3 class="modal-title">Add Refreshments</h3>
              <p class="modal-sub">
                Table {{ staffSnackReservation.tableId }} ·
                {{ getCustomerName(staffSnackReservation.customer) }}
              </p>
            </div>
            <button
              type="button"
              class="close-x"
              @click="staffSnackReservation = null"
            >
              <i class="fa-solid fa-xmark" aria-hidden="true"></i>
            </button>
          </div>

          <div class="snack-list-body">
            <div
              v-for="item in branchStockItems"
              :key="item.id"
              class="snack-row"
              :class="{ 'out-of-stock-row': item.currentStock <= 0 }"
            >
              <div class="snack-info">
                <span class="snack-name">{{ item.name }}</span>
                <span class="snack-price">
                  ฿{{ item.price }} ·
                  <span
                    :class="item.currentStock > 0 ? 'stock-ok' : 'stock-out'"
                  >
                    {{
                      item.currentStock > 0
                        ? `${item.currentStock} in stock`
                        : 'Out of stock'
                    }}
                  </span>
                </span>
              </div>
              <div class="stepper-wrap compact">
                <button
                  type="button"
                  class="step-btn small"
                  :disabled="getStaffSnackQty(item.id) <= 0"
                  @click="adjustStaffSnackQty(item, -1)"
                >
                  -
                </button>
                <span class="step-val small">{{
                  getStaffSnackQty(item.id)
                }}</span>
                <button
                  type="button"
                  class="step-btn small"
                  :disabled="getStaffSnackQty(item.id) >= item.currentStock"
                  @click="adjustStaffSnackQty(item, 1)"
                >
                  +
                </button>
              </div>
            </div>
          </div>

          <div class="snack-modal-footer">
            <div class="snack-total">
              <span>Selected Total:</span>
              <strong class="total-val">฿{{ staffSnackSubtotal }}</strong>
            </div>
            <div class="modal-buttons">
              <button
                type="button"
                class="btn-secondary-parlor"
                @click="staffSnackReservation = null"
              >
                Cancel
              </button>
              <button
                type="button"
                class="btn-primary-parlor"
                :disabled="staffSnackSelectedItems.length === 0"
                @click="submitStaffSnackOrder"
              >
                Add to Table Tab (฿{{ staffSnackSubtotal }})
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Fortinet-derived dense ops system. Tokens first, then chrome, panels, tables. */

.gl-admin-layout {
  --canvas: #f0f3f0;
  --surface: #ffffff;
  --line: #d3d9d4;
  --line-soft: #e6ebe6;
  --ink: #1c2a22;
  --body: #33413a;
  --muted: #5a675f;
  --accent: #244c38;
  --accent-hover: #1d4030;
  --accent-soft: #e3ebe6;
  --accent-ink: #1a3a29;
  --chrome: #1c2e24;
  --chrome-2: #243a2d;
  --chrome-hover: #2a4232;
  --chrome-line: #31493a;
  --gold: #c6ad7a;
  --gold-soft: #f4ecd9;
  --gold-ink: #7a5a24;
  --ok: #1e5631;
  --ok-bg: #e2efe6;
  --warn: #8a5a1c;
  --warn-bg: #f6ecd9;
  --err: #9c3526;
  --err-bg: #f7e5e1;
  --r: 3px;
  --shadow: 0 1px 2px rgba(20, 43, 32, 0.06);

  display: flex;
  height: 100dvh;
  overflow: hidden;
  background: var(--canvas);
  color: var(--body);
  font-size: 13px;
  line-height: 1.45;
}

/* ------------------------------------------------------------------ */
/* Sidebar chrome                                                      */
/* ------------------------------------------------------------------ */

.gl-sidebar {
  width: 236px;
  flex: 0 0 auto;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--chrome);
  color: #cfe0d5;
}

.gl-sidebar.collapsed {
  width: 64px;
}

.gl-sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 12px;
  border-bottom: 1px solid var(--chrome-line);
}

.brand-text-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand-name {
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.01em;
  white-space: nowrap;
}

.brand-sub {
  color: #8fa697;
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.gl-sidebar.collapsed .brand-text-col,
.gl-sidebar.collapsed .collapse-text,
.gl-sidebar.collapsed .sidebar-footer,
.gl-sidebar.collapsed .nav-section-title {
  opacity: 0;
  max-width: 0;
  max-height: 0;
  margin: 0;
  padding: 0;
  pointer-events: none;
  overflow: hidden;
}

.sidebar-collapse-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 12px;
  border: 0;
  border-bottom: 1px solid var(--chrome-line);
  background: transparent;
  color: #8fa697;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  text-align: left;
}

.sidebar-collapse-btn:hover {
  background: var(--chrome-2);
  color: #ffffff;
}

.gl-sidebar.collapsed .sidebar-collapse-btn {
  justify-content: center;
}

.collapse-arrow-icon {
  transition: transform 0.15s ease;
}

.collapse-arrow-icon.is-collapsed {
  transform: rotate(180deg);
}

.sidebar-nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 0;
}

.nav-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 12px 5px;
  color: #6f8a7a;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.role-pill-tiny {
  padding: 1px 6px;
  border: 1px solid var(--chrome-line);
  border-radius: var(--r);
  color: #8fa697;
  font-size: 9px;
  font-weight: 500;
  letter-spacing: 0.05em;
}

.role-pill-tiny.admin {
  color: var(--gold);
  border-color: #54685a;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  color: #c2d4c8;
  font-size: 12.5px;
  font-weight: 400;
  text-decoration: none;
  cursor: pointer;
  user-select: none;
}

.nav-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 2px;
  background: transparent;
}

.nav-item:hover {
  background: var(--chrome-hover);
  color: #ffffff;
}

.nav-item.active {
  background: var(--chrome-2);
  color: #ffffff;
  font-weight: 500;
}

.nav-item.active::before {
  background: var(--gold);
}

.nav-icon-box {
  display: inline-flex;
  flex: 0 0 18px;
  align-items: center;
  justify-content: center;
}

.nav-icon {
  display: block;
}

.nav-label {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gl-sidebar.collapsed .nav-label {
  display: none;
}

.nav-badge-gold {
  padding: 0 6px;
  border-radius: 8px;
  background: var(--gold);
  color: #142b20;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
}

.gl-sidebar.collapsed .nav-badge-gold {
  display: none;
}

.nav-divider {
  height: 1px;
  margin: 10px 12px;
  background: var(--chrome-line);
}

.sidebar-footer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border-top: 1px solid var(--chrome-line);
}

.user-profile-row {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.user-avatar-gold {
  flex: 0 0 30px;
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--gold);
  color: #142b20;
  font-size: 11px;
  font-weight: 600;
}

.user-info-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-name {
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role-label {
  color: #8fa697;
  font-size: 10.5px;
}

.btn-signout {
  padding: 6px 10px;
  border: 1px solid var(--chrome-line);
  border-radius: var(--r);
  background: transparent;
  color: #c2d4c8;
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
}

.btn-signout:hover {
  background: var(--chrome-hover);
  border-color: #4a6350;
  color: #ffffff;
}

/* ------------------------------------------------------------------ */
/* Main shell & topbar                                                 */
/* ------------------------------------------------------------------ */

.gl-main-wrapper {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.gl-topbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px;
  height: 46px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.branch-select {
  height: 28px;
  padding: 0 26px 0 9px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: var(--surface)
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6' viewBox='0 0 10 6'%3E%3Cpath d='M1 1l4 4 4-4' stroke='%235a675f' fill='none' stroke-width='1.5'/%3E%3C/svg%3E")
    no-repeat right 9px center;
  color: var(--ink);
  font-size: 12px;
  font-weight: 500;
  appearance: none;
  cursor: pointer;
}

.branch-select:focus-visible {
  outline: none;
  border-color: var(--accent);
}

.console-role-pill {
  padding: 3px 9px;
  border-radius: var(--r);
  background: var(--accent-soft);
  color: var(--accent-ink);
  font-size: 11px;
  font-weight: 500;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.icon-circle-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: var(--surface);
  color: var(--muted);
  cursor: pointer;
  text-decoration: none;
}

.icon-circle-btn:hover {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--accent-ink);
}

.topbar-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-left: 4px;
  border-radius: 50%;
  background: var(--accent);
  color: #ffffff;
  font-size: 11px;
  font-weight: 600;
}

.gl-content-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.gl-content-scroll:focus-visible {
  outline: none;
}

.gl-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 18px 20px 56px;
}

/* ------------------------------------------------------------------ */
/* Panel system (Fortinet widget pattern)                              */
/* ------------------------------------------------------------------ */

.console-block {
  margin-bottom: 16px;
  padding: 0;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r);
  box-shadow: var(--shadow);
}

.view-header-row,
.section-card-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line-soft);
  background: #f7f9f7;
  border-radius: var(--r) var(--r) 0 0;
}

.view-eyebrow {
  color: var(--muted);
  font-size: 10.5px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.view-title {
  margin: 1px 0 2px;
  color: var(--ink);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.view-subtitle {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

.section-title {
  margin: 0;
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.header-mgmt-actions,
.timeline-span,
.usage-text {
  color: var(--muted);
  font-size: 11.5px;
}

.count-bubble {
  padding: 1px 8px;
  border-radius: 9px;
  background: var(--accent-soft);
  color: var(--accent-ink);
  font-size: 11px;
  font-weight: 600;
}

.role-pill-gold {
  padding: 3px 9px;
  border: 1px solid #e0d3b3;
  border-radius: var(--r);
  background: var(--gold-soft);
  color: var(--gold-ink);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

/* Body content inside panels gets horizontal padding */
.view-header-row + .stock-stats-ribbon,
.view-header-row + .library-filter-ribbon,
.view-header-row + .settings-form-grid,
.view-header-row + .policies-cards-list,
.view-header-row + .staff-roster-grid,
.view-header-row + .analytics-ribbon-grid {
  padding: 14px 16px;
}

/* ------------------------------------------------------------------ */
/* Buttons — Fortinet primary/secondary/link semantics                 */
/* ------------------------------------------------------------------ */

.btn-primary-parlor,
.btn-secondary-parlor,
.btn-refresh-parlor,
.btn-batch-restock,
.btn-custom-restock,
.btn-delete-sku,
.btn-return-shelf,
.tile-btn-small,
.tile-btn-assign,
.tile-btn-gold,
.btn-tile-assign-game,
.btn-assign-mini,
.btn-parlor-pill,
.btn-gold-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 12px;
  height: 30px;
  border: 1px solid transparent;
  border-radius: var(--r);
  font-family: inherit;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
}

.btn-primary-parlor {
  background: var(--accent);
  border-color: var(--accent);
  color: #ffffff;
}

.btn-primary-parlor:hover {
  background: var(--accent-hover);
  border-color: var(--accent-hover);
}

.btn-secondary-parlor,
.btn-refresh-parlor,
.btn-parlor-pill {
  background: var(--surface);
  border-color: #a9b5aa;
  color: var(--ink);
}

.btn-secondary-parlor:hover,
.btn-refresh-parlor:hover,
.btn-parlor-pill:hover {
  border-color: var(--accent);
  color: var(--accent-ink);
  background: var(--accent-soft);
}

.btn-primary-parlor.small,
.btn-secondary-parlor.small,
.btn-primary-parlor.small,
.btn-batch-restock,
.btn-custom-restock,
.btn-assign-mini,
.btn-tile-assign-game {
  height: 26px;
  padding: 0 10px;
  font-size: 11.5px;
}

.btn-primary-parlor:disabled,
.btn-secondary-parlor:disabled,
.btn-parlor-pill:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn-cancel-link {
  border: 0;
  background: none;
  padding: 0 6px;
  color: var(--err);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}

.btn-cancel-link:hover {
  text-decoration: underline;
}

.btn-gold-pill {
  background: var(--gold);
  border-color: var(--gold);
  color: #142b20;
}

.btn-gold-pill:hover {
  background: #b89b66;
}

.btn-delete-sku {
  height: 26px;
  padding: 0 10px;
  background: var(--surface);
  border-color: #d8b7af;
  color: var(--err);
  font-size: 11.5px;
}

.btn-delete-sku:hover {
  background: var(--err-bg);
  border-color: var(--err);
}

.btn-refresh-parlor {
  height: 26px;
  padding: 0 10px;
  font-size: 11.5px;
}

.btn-primary-parlor i,
.btn-secondary-parlor i,
.btn-batch-restock i,
.btn-custom-restock i,
.btn-delete-sku i,
.btn-return-shelf i,
.pill-tab i,
.filter-pill i {
  font-size: 10px;
}

.tile-action-link,
.btn-return-shelf {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 26px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--r);
  background: var(--accent-soft);
  color: var(--accent-ink);
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
}

.tile-action-link:hover,
.btn-return-shelf:hover {
  background: #d6e2da;
}

.btn-assign-mini {
  height: 24px;
  padding: 0 10px;
  background: var(--accent-soft);
  border-color: transparent;
  color: var(--accent-ink);
  font-size: 11.5px;
}

.btn-assign-mini:hover {
  background: #d6e2da;
}

.tile-btn-small {
  background: var(--accent-soft);
  border-color: transparent;
  color: var(--accent-ink);
}

.tile-btn-small:hover {
  background: #d6e2da;
}

.tile-btn-gold {
  background: var(--accent);
  border-color: var(--accent);
  color: #ffffff;
}

.tile-btn-gold:hover {
  background: var(--accent-hover);
}

.tile-btn-assign {
  background: var(--surface);
  border-color: #a9b5aa;
  color: var(--ink);
}

.tile-btn-assign:hover {
  border-color: var(--accent);
  color: var(--accent-ink);
}

.btn-tile-assign-game {
  background: var(--surface);
  border-color: #a9b5aa;
  color: var(--ink);
}

.btn-tile-assign-game:hover {
  border-color: var(--accent);
  color: var(--accent-ink);
}

.search-games-input-wrap .parlor-input {
  width: 100%;
}

/* Alert / empty states ------------------------------------------------ */

.alert-banner {
  padding: 8px 12px;
  border: 1px solid #ecc8c1;
  border-radius: var(--r);
  background: var(--err-bg);
  color: var(--err);
  font-size: 12px;
}

.empty-state-box {
  margin: 0;
  padding: 44px 20px;
  text-align: center;
}

.empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-bottom: 10px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: #f4f6f4;
  color: var(--muted);
  font-size: 14px;
}

.empty-state-box h3 {
  margin: 0 0 4px;
  color: var(--ink);
  font-size: 13.5px;
  font-weight: 600;
}

.empty-state-box p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

.empty-timeline {
  padding: 26px 16px;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}

/* ------------------------------------------------------------------ */
/* Tables — nu-table pattern                                           */
/* ------------------------------------------------------------------ */

.sessions-table-wrap,
.bookings-list-wrap,
.inventory-table-wrap {
  overflow-x: auto;
}

.parlor-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}

.parlor-table th {
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  background: #f4f6f4;
  color: var(--muted);
  font-size: 11px;
  font-weight: 600;
  text-align: left;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.parlor-table td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--line-soft);
  color: var(--body);
  vertical-align: middle;
}

.parlor-table tbody tr:last-child td {
  border-bottom: 0;
}

.parlor-table tbody tr:hover td {
  background: #f7f9f7;
}

.table-badge {
  display: inline-block;
  padding: 2px 8px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: #f4f6f4;
  color: var(--ink);
  font-size: 11px;
  font-weight: 600;
}

.time-elapsed-badge {
  padding: 2px 8px;
  border-radius: var(--r);
  background: var(--accent-soft);
  color: var(--accent-ink);
  font-size: 11px;
  font-weight: 600;
}

.party-muted {
  margin-left: 4px;
  color: var(--muted);
  font-size: 11px;
}

.fee-text {
  color: var(--ink);
  font-size: 13px;
}

.actions-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* Status tags --------------------------------------------------------- */

.status-tag,
.stock-status-tag,
.mini-status-tag {
  display: inline-block;
  padding: 2px 8px;
  border: 1px solid transparent;
  border-radius: var(--r);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.status-tag.checked_in,
.status-tag.confirmed,
.stock-status-tag.healthy,
.mini-status-tag.avail {
  background: var(--ok-bg);
  border-color: #c4d9ca;
  color: var(--ok);
}

.status-tag.no_show,
.status-tag.cancelled,
.stock-status-tag.out,
.mini-status-tag.unavail {
  background: var(--err-bg);
  border-color: #e3c3bb;
  color: var(--err);
}

.status-tag.pending,
.status-tag.completed {
  background: #eef1ee;
  border-color: var(--line);
  color: var(--muted);
}

.stock-status-tag.low {
  background: var(--warn-bg);
  border-color: #e3d3ac;
  color: var(--warn);
}

/* ------------------------------------------------------------------ */
/* Metric ribbons                                                      */
/* ------------------------------------------------------------------ */

.numbers-card-ribbon,
.stock-stats-ribbon,
.analytics-ribbon-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  background: var(--line-soft);
}

.numbers-card-ribbon {
  border: 1px solid var(--line);
  border-radius: var(--r);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.stock-stats-ribbon,
.analytics-ribbon-grid {
  border: 0;
  background: transparent;
  gap: 12px;
}

.num-col,
.stock-stat-box,
.analytics-card {
  padding: 12px 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r);
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.col-label,
.stat-lbl,
.analytics-label {
  color: var(--muted);
  font-size: 10.5px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.07em;
}

.col-value,
.stat-num,
.analytics-value {
  color: var(--ink);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
  line-height: 1.2;
}

.col-sub,
.stat-sub,
.analytics-sub {
  color: var(--muted);
  font-size: 11px;
}

.text-green {
  color: var(--ok);
}

.text-amber {
  color: var(--warn);
}

.text-red {
  color: var(--err);
}

/* ------------------------------------------------------------------ */
/* Dashboard                                                           */
/* ------------------------------------------------------------------ */

.hero-block {
  margin-bottom: 16px;
  padding: 16px 20px 14px;
}

.eyebrow-row {
  margin-bottom: 2px;
}

.hero-eyebrow,
.view-eyebrow {
  color: var(--muted);
  font-size: 10.5px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.hero-title {
  margin: 0 0 2px;
  color: var(--ink);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.hero-subtitle {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 12.5px;
}

.hero-actions-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hero-actions-bar .actions-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.updated-time {
  color: var(--muted);
  font-size: 11px;
}

.dashboard-stock-watch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding: 10px 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r);
  box-shadow: var(--shadow);
}

.dashboard-stock-watch h2 {
  margin: 0;
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}

.stock-watch-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--muted);
  font-size: 12px;
}

.stock-watch-summary strong {
  color: var(--ink);
}

/* Panel head anatomy ------------------------------------------------- */

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--line-soft);
}

.panel-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-title {
  margin: 0;
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 600;
}

.panel-hint {
  color: var(--muted);
  font-size: 11px;
}

.count-pill {
  padding: 0 7px;
  border-radius: 9px;
  font-size: 10.5px;
  font-weight: 600;
  line-height: 16px;
}

.count-pill.warn {
  background: var(--warn-bg);
  color: var(--warn);
}

.count-pill.accent {
  background: var(--accent-soft);
  color: var(--accent-ink);
}

/* Breadcrumb trail (FortiSOAR) ---------------------------------------- */

.crumb-trail {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: var(--muted);
  font-size: 12px;
}

.crumb-trail i {
  font-size: 9px;
  color: #a9b5aa;
}

.crumb-root {
  color: var(--muted);
}

.crumb-current {
  color: var(--ink);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Incident boxes (FortiSIEM severity cards) --------------------------- */

.incident-stack {
  display: flex;
  flex-direction: column;
}

.incident-box {
  padding: 10px 14px 10px 16px;
  border-bottom: 1px solid var(--line-soft);
  border-left: 2px solid transparent;
}

.incident-box:last-child {
  border-bottom: 0;
}

.incident-box.sev-high {
  border-left-color: var(--err);
  background: #fdf7f5;
}

.incident-box.sev-med {
  border-left-color: var(--gold);
}

.incident-title {
  display: block;
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 600;
}

.incident-sub {
  margin: 1px 0 8px;
  color: var(--muted);
  font-size: 11.5px;
}

.incident-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* Occupancy rail (FortiSIEM counters) --------------------------------- */

.occ-panel {
  margin-bottom: 0;
}

.occ-body {
  display: flex;
  flex-direction: column;
}

.occ-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 12px;
}

.occ-row:last-child {
  border-bottom: 0;
}

.occ-dot {
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #a9b5aa;
}

.occ-row.occupied .occ-dot {
  background: var(--accent);
}

.occ-row.reserved .occ-dot {
  background: var(--gold);
}

.occ-label {
  flex: 1;
  color: var(--body);
}

.occ-count {
  color: var(--ink);
  font-weight: 600;
}

.occ-bar {
  flex: 0 0 54px;
  height: 4px;
  border-radius: 2px;
  background: #e6ebe6;
  overflow: hidden;
}

.occ-fill {
  display: block;
  height: 100%;
  border-radius: 2px;
  background: var(--accent);
}

.occ-row.reserved .occ-fill {
  background: var(--gold);
}

.occ-row.free .occ-fill {
  background: #a9b5aa;
}

.occ-foot {
  padding: 8px 16px;
  border-top: 1px solid var(--line-soft);
  background: #f7f9f7;
  color: var(--muted);
  font-size: 10.5px;
  border-radius: 0 0 var(--r) var(--r);
}

/* Row-hover action emphasis (FortiSOAR) ------------------------------- */

.parlor-table .actions-cell {
  opacity: 0.55;
}

.parlor-table tbody tr:hover .actions-cell {
  opacity: 1;
}

/* Needs-you legacy hooks kept harmless */

.needs-you-card {
  border-color: #e3d3ac;
}

.needs-you-card .panel-head {
  background: var(--gold-soft);
  border-bottom-color: #e9dfc4;
  border-radius: var(--r) var(--r) 0 0;
}

.needs-you-card .panel-title {
  color: var(--gold-ink);
}

.needs-you-card .panel-hint {
  color: var(--gold-ink);
}

/* Two-column lower area ---------------------------------------------- */

.dash-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.dash-main,
.dash-side {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.floor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 16px;
  align-items: start;
}

.floor-main,
.floor-side {
  min-width: 0;
}

.policy-rule-banner {
  margin: 12px 16px 0;
  padding: 7px 10px;
  border: 1px solid var(--line-soft);
  border-left: 2px solid var(--accent);
  border-radius: var(--r);
  background: #f4f7f4;
  color: var(--body);
  font-size: 11.5px;
}

/* Floor tiles --------------------------------------------------------- */

.floor-tables-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 10px;
  padding: 14px 16px;
}

.floor-tables-grid.expanded {
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
}

.floor-table-tile {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r);
}

.floor-table-tile.free {
  border-left: 2px solid #a9b5aa;
}

.floor-table-tile.occupied {
  border-left: 2px solid var(--accent);
  background: #f6faf7;
}

.floor-table-tile.reserved {
  border-left: 2px solid var(--gold);
  background: #fdfbf5;
}

.tile-top-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.table-num-lbl {
  color: var(--ink);
  font-size: 13.5px;
  font-weight: 600;
}

.table-seats-lbl {
  color: var(--muted);
  font-size: 10.5px;
  white-space: nowrap;
}

.tile-status-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.state-badge {
  padding: 2px 7px;
  border-radius: var(--r);
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.badge-free {
  background: #eef1ee;
  border: 1px solid var(--line);
  color: var(--muted);
}

.badge-occupied {
  background: var(--accent-soft);
  border: 1px solid #c4d4c9;
  color: var(--accent-ink);
}

.badge-reserved {
  background: var(--gold-soft);
  border: 1px solid #e0d3b3;
  color: var(--gold-ink);
}

.tile-time-pill {
  padding: 2px 7px;
  border-radius: var(--r);
  background: var(--accent);
  color: #ffffff;
  font-size: 10.5px;
  font-weight: 600;
}

.tile-helper,
.no-game-text {
  margin: 0;
  color: var(--muted);
  font-size: 11px;
}

.tile-guest-name,
.guest-name-large {
  margin: 0;
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 600;
}

.guest-name-large {
  font-size: 15px;
}

.tile-sub-info,
.tile-due-time {
  margin: 0;
  color: var(--muted);
  font-size: 11.5px;
}

.tile-assigned-games-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.assigned-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.assigned-label {
  color: var(--muted);
  font-size: 11px;
  font-weight: 500;
}

.assigned-chips-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.game-chip-capsule {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 3px 8px;
  border: 1px solid #c4d4c9;
  border-radius: var(--r);
  background: var(--accent-soft);
  color: var(--accent-ink);
  font-size: 11.5px;
  font-weight: 500;
}

.game-chip-capsule i {
  font-size: 10px;
}

.btn-chip-unassign {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: none;
  padding: 0;
  color: var(--accent-ink);
  font-size: 10px;
  cursor: pointer;
}

.btn-chip-unassign:hover {
  color: var(--err);
}

.tile-actions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: auto;
}

/* Coming-up timeline -------------------------------------------------- */

.timeline-cards-list {
  display: flex;
  flex-direction: column;
}

.timeline-card {
  display: flex;
  gap: 14px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--line-soft);
}

.timeline-card:last-child {
  border-bottom: 0;
}

.timeline-time-col {
  flex: 0 0 58px;
  padding-top: 1px;
}

.time-bold {
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
}

.timeline-card-content {
  flex: 1;
  min-width: 0;
}

.timeline-card-top {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.timeline-guest-name {
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 600;
}

.party-tag,
.table-tag {
  color: var(--muted);
  font-size: 11.5px;
}

.table-tag {
  padding: 1px 7px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: #f4f6f4;
  font-size: 10.5px;
  font-weight: 500;
}

.timeline-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ------------------------------------------------------------------ */
/* Game library                                                        */
/* ------------------------------------------------------------------ */

.search-games-input-wrap {
  flex: 0 0 260px;
}

.library-filter-ribbon {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line-soft);
}

.filter-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: var(--surface);
  color: var(--body);
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
}

.filter-pill:hover {
  border-color: var(--accent);
  color: var(--accent-ink);
}

.filter-pill.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #ffffff;
}

.games-library-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
  padding: 14px 16px;
}

.game-catalog-card {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r);
  overflow: hidden;
}

.game-status-strip {
  padding: 3px 10px;
  font-size: 10.5px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.game-status-strip.avail {
  background: var(--ok-bg);
  color: var(--ok);
}

.game-status-strip.unavail {
  background: var(--warn-bg);
  color: var(--warn);
}

.game-card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px 0;
}

.game-name {
  margin: 0;
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
}

.game-difficulty-pill {
  flex: 0 0 auto;
  padding: 2px 7px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: #f4f6f4;
  color: var(--muted);
  font-size: 10px;
  font-weight: 500;
}

.game-designer {
  margin: 0;
  padding: 0 12px;
  color: var(--muted);
  font-size: 11px;
}

.game-meta-row {
  display: flex;
  gap: 12px;
  padding: 6px 12px 0;
  color: var(--body);
  font-size: 11px;
}

.game-meta-row i {
  color: var(--muted);
  font-size: 10px;
}

.game-desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 6px 0 0;
  padding: 0 12px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.4;
}

.game-card-footer {
  margin-top: auto;
  padding: 10px 12px 12px;
}

.game-card-footer .btn-primary-parlor,
.game-card-footer .btn-return-shelf {
  width: 100%;
}

/* ------------------------------------------------------------------ */
/* Inventory                                                           */
/* ------------------------------------------------------------------ */

.stock-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 16px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--line-soft);
}

.filter-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-lbl {
  color: var(--muted);
  font-size: 11px;
  font-weight: 500;
}

.pill-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: var(--surface);
  color: var(--body);
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
}

.pill-tab:hover {
  border-color: var(--accent);
  color: var(--accent-ink);
}

.pill-tab.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #ffffff;
}

.stock-item-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.stock-item-info strong {
  color: var(--ink);
  font-size: 12.5px;
}

.item-th-sub {
  color: var(--muted);
  font-size: 11px;
}

.pantry-sku {
  color: var(--muted);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.category-tag {
  padding: 2px 8px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: #f4f6f4;
  color: var(--body);
  font-size: 11px;
  font-weight: 500;
  text-transform: capitalize;
}

.stock-qty-badge {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
}

.qty-big {
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
}

.stock-qty-badge.empty .qty-big {
  color: var(--err);
}

.stock-qty-badge.low .qty-big {
  color: var(--warn);
}

.qty-unit {
  color: var(--muted);
  font-size: 11px;
}

.price-val {
  color: var(--ink);
  font-size: 12.5px;
}

.margin-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
  color: var(--muted);
}

.margin-pill {
  color: var(--ok);
  font-size: 11px;
}

.stepper-wrap {
  display: inline-flex;
  align-items: center;
  gap: 0;
  border: 1px solid #a9b5aa;
  border-radius: var(--r);
  overflow: hidden;
  background: var(--surface);
}

.step-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 0;
  background: var(--surface);
  color: var(--body);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.step-btn:hover:not(:disabled) {
  background: var(--accent-soft);
  color: var(--accent-ink);
}

.step-btn:disabled {
  color: #b3bcb4;
  cursor: not-allowed;
}

.step-val {
  min-width: 30px;
  text-align: center;
  border-left: 1px solid var(--line-soft);
  border-right: 1px solid var(--line-soft);
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 600;
  line-height: 26px;
}

.stepper-wrap.small .step-btn,
.stepper-wrap.compact .step-btn {
  width: 22px;
  height: 22px;
  font-size: 12px;
}

.stepper-wrap.small .step-val,
.stepper-wrap.compact .step-val {
  min-width: 26px;
  line-height: 22px;
  font-size: 11.5px;
}

.restock-actions-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ------------------------------------------------------------------ */
/* Staff, settings, policies                                           */
/* ------------------------------------------------------------------ */

.staff-roster-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
}

.staff-member-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--r);
}

.staff-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.staff-avatar-circle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--accent);
  color: #ffffff;
  font-size: 11.5px;
  font-weight: 600;
}

.staff-name {
  margin: 0;
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}

.staff-role-badge {
  color: var(--muted);
  font-size: 11px;
}

.staff-card-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-top: 10px;
  border-top: 1px solid var(--line-soft);
  color: var(--body);
  font-size: 12px;
}

.duty-status-row {
  margin-top: 4px;
}

.duty-lbl {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--r);
  background: var(--ok-bg);
  color: var(--ok);
  font-size: 10.5px;
  font-weight: 500;
}

.settings-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 18px;
}

.form-row-2 {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 18px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.parlor-label,
.modal-label {
  color: var(--body);
  font-size: 11.5px;
  font-weight: 500;
}

.parlor-input,
.modal-input,
.modal-select {
  height: 30px;
  padding: 0 9px;
  border: 1px solid #a9b5aa;
  border-radius: var(--r);
  background: var(--surface);
  color: var(--ink);
  font-family: inherit;
  font-size: 12.5px;
}

.parlor-input::placeholder,
.modal-input::placeholder {
  color: #97a49a;
}

.parlor-input:focus,
.modal-input:focus,
.modal-select:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 2px rgba(36, 76, 56, 0.12);
}

select.parlor-input,
.modal-select {
  appearance: auto;
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
}

.policies-cards-list {
  display: flex;
  flex-direction: column;
}

.policy-card {
  padding: 12px 16px;
  border-bottom: 1px solid var(--line-soft);
}

.policy-card:last-child {
  border-bottom: 0;
}

.policy-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.policy-card-title {
  margin: 0 0 3px;
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}

.policy-card-desc {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  max-width: 72ch;
}

.policy-active-tag {
  flex: 0 0 auto;
  padding: 2px 8px;
  border: 1px solid #c4d9ca;
  border-radius: var(--r);
  background: var(--ok-bg);
  color: var(--ok);
  font-size: 10.5px;
  font-weight: 500;
  white-space: nowrap;
}

/* ------------------------------------------------------------------ */
/* Modals                                                              */
/* ------------------------------------------------------------------ */

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(16, 26, 20, 0.55);
}

.modal-card {
  width: 100%;
  max-width: 560px;
  max-height: calc(100dvh - 64px);
  overflow-y: auto;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 4px;
  box-shadow: 0 8px 28px rgba(16, 26, 20, 0.28);
}

.modal-card.snack-card {
  max-width: 480px;
}

.walkin-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 16px;
  border-bottom: 1px solid var(--line);
  background: #f7f9f7;
}

.eyebrow-sub {
  color: var(--muted);
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.modal-title {
  margin: 2px 0 2px;
  color: var(--ink);
  font-size: 15px;
  font-weight: 600;
}

.modal-sub {
  margin: 0;
  color: var(--muted);
  font-size: 11.5px;
}

.close-x {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 26px;
  width: 26px;
  height: 26px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: var(--surface);
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}

.close-x:hover {
  border-color: var(--err);
  color: var(--err);
  background: var(--err-bg);
}

.modal-form-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px;
}

.modal-input,
.modal-select {
  width: 100%;
}

.rule-warning-box {
  display: flex;
  gap: 9px;
  padding: 9px 11px;
  border: 1px solid #e3d3ac;
  border-left: 2px solid var(--gold);
  border-radius: var(--r);
  background: var(--gold-soft);
  color: var(--gold-ink);
  font-size: 11.5px;
  line-height: 1.45;
}

.gold-bullet {
  flex: 0 0 auto;
  color: var(--gold-ink);
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.tables-choice-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(108px, 1fr));
  gap: 6px;
}

.tbl-choice-pill {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  padding: 7px 9px;
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: var(--surface);
  color: var(--ink);
  font-size: 11.5px;
  cursor: pointer;
  text-align: left;
}

.tbl-choice-pill strong {
  font-weight: 600;
}

.tbl-choice-pill:hover:not(:disabled) {
  border-color: var(--accent);
}

.tbl-choice-pill.active {
  border-color: var(--accent);
  background: var(--accent-soft);
  box-shadow: inset 0 0 0 1px var(--accent);
}

.tbl-choice-pill:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.pill-reason {
  color: var(--muted);
  font-size: 10.5px;
}

.pill-reason.ready {
  color: var(--ok);
}

.pill-reason.has-game {
  color: var(--warn);
}

.modal-games-list {
  max-height: 230px;
  overflow-y: auto;
  border: 1px solid var(--line-soft);
  border-radius: var(--r);
}

.modal-game-choice-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 11px;
  border-bottom: 1px solid var(--line-soft);
  cursor: pointer;
}

.modal-game-choice-row:last-child {
  border-bottom: 0;
}

.modal-game-choice-row:hover {
  background: #f7f9f7;
}

.modal-game-choice-row.selected {
  background: var(--accent-soft);
  box-shadow: inset 2px 0 0 var(--accent);
}

.modal-game-choice-row.in-use {
  opacity: 0.55;
  cursor: not-allowed;
}

.game-choice-info {
  min-width: 0;
}

.game-choice-name {
  display: block;
  color: var(--ink);
  font-size: 12.5px;
}

.game-choice-sub {
  color: var(--muted);
  font-size: 11px;
}

.game-choice-sub i {
  font-size: 9.5px;
}

.mini-status-tag {
  flex: 0 0 auto;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--line);
  background: #f7f9f7;
}

.stock-current-preview,
.new-stock-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 11px;
  border: 1px solid var(--line-soft);
  border-radius: var(--r);
  background: #f4f6f4;
  color: var(--muted);
  font-size: 12px;
}

.stock-current-preview strong,
.new-stock-preview strong {
  color: var(--ink);
  font-size: 13px;
}

/* Snack modal --------------------------------------------------------- */

.snack-list-body {
  max-height: 340px;
  overflow-y: auto;
}

.snack-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 7px 16px;
  border-bottom: 1px solid var(--line-soft);
}

.snack-row:last-child {
  border-bottom: 0;
}

.snack-row.out-of-stock-row {
  opacity: 0.55;
}

.snack-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.snack-name {
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 500;
}

.snack-price {
  color: var(--muted);
  font-size: 11px;
}

.stock-ok {
  color: var(--ok);
}

.stock-out {
  color: var(--err);
}

.snack-modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid var(--line);
  background: #f7f9f7;
}

.snack-total {
  display: flex;
  align-items: baseline;
  gap: 7px;
  color: var(--muted);
  font-size: 12px;
}

.total-val {
  color: var(--ink);
  font-size: 15px;
}

.modal-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ------------------------------------------------------------------ */
/* Responsive & motion                                                 */
/* ------------------------------------------------------------------ */

@media (max-width: 1080px) {
  .dash-grid,
  .floor-layout {
    grid-template-columns: 1fr;
  }

  .numbers-card-ribbon,
  .stock-stats-ribbon,
  .analytics-ribbon-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 720px) {
  .gl-sidebar {
    width: 64px;
  }

  .brand-text-col,
  .collapse-text,
  .sidebar-footer,
  .nav-section-title,
  .nav-label,
  .nav-badge-gold {
    display: none;
  }

  .gl-content {
    padding: 14px 12px 48px;
  }

  .numbers-card-ribbon,
  .stock-stats-ribbon,
  .analytics-ribbon-grid,
  .settings-form-grid,
  .form-row-2 {
    grid-template-columns: 1fr;
  }

  .view-header-row,
  .section-card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .incident-box {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .gl-sidebar,
  .nav-item,
  .collapse-arrow-icon {
    transition: none !important;
  }
}

.nav-item,
.filter-pill,
.pill-tab,
.btn-primary-parlor,
.btn-secondary-parlor,
.icon-circle-btn {
  transition: background 0.12s ease, border-color 0.12s ease, color 0.12s ease;
}
</style>

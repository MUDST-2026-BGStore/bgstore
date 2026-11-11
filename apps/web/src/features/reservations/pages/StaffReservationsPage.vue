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
import { useCustomerReservationsStore } from '../data/reservations-store';
import { games, type CatalogGame } from '../../home/data/catalog';
import { useAuthStore } from '../../../shared/stores/auth';

const queryClient = useQueryClient();
const auth = useAuthStore();
const reservationsStore = useCustomerReservationsStore();
const route = useRoute();

// Sidebar state & Role resolution
const SIDEBAR_STORAGE_KEY = 'theparlor.staff_sidebar_collapsed';

function isManagementPath(path?: string, name?: string | symbol | null): boolean {
  if (path && (path === '/workspace/management' || path.startsWith('/workspace/management'))) {
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
  }
);

type NavTab =
  | 'dashboard'
  | 'floor'
  | 'sessions'
  | 'bookings'
  | 'library'
  | 'inventory'
  | 'branches'
  | 'manage-tables'
  | 'staff-roster'
  | 'analytics'
  | 'settings'
  | 'policies';

const activeTab = ref<NavTab>('dashboard');

function selectNavTab(tab: NavTab) {
  activeTab.value = tab;
  if (typeof window !== 'undefined' && window.innerWidth <= 768) {
    isSidebarCollapsed.value = true;
  }
}

// Role hierarchy (Management & Administration unified into Administration)
const userRoles = computed(() => auth.roles || []);
const isAdmin = computed(
  () =>
    userRoles.value.includes('admin') ||
    userRoles.value.includes('manager') ||
    userRoles.value.includes('developer')
);
const isManager = isAdmin; // Combined management and administration into unified Administration role

const roleBadgeLabel = computed(() => {
  if (isAdmin.value) return 'ADMIN CONSOLE';
  return 'STAFF CONSOLE';
});

const userRoleTitle = computed(() => {
  if (isAdmin.value) return 'Administrator';
  return 'Floor Staff';
});

// Guard administration tabs (inventory/stock is accessible to all staff)
watch(activeTab, (tab) => {
  if (
    ['branches', 'manage-tables', 'staff-roster', 'analytics', 'settings', 'policies'].includes(tab) &&
    !isAdmin.value
  ) {
    activeTab.value = 'dashboard';
  }
});

// Branch & Search
const selectedBranchId = ref<string>('');
const searchQuery = ref<string>('');
const currentTimeStr = ref(
  new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
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
const branches = computed<Branch[]>(() => branchesQuery.data.value?.items ?? []);

watch(branches, (list) => {
  if (list.length > 0 && !selectedBranchId.value) {
    selectedBranchId.value = list[0].id;
  }
});

const selectedBranch = computed(() =>
  branches.value.find((b) => b.id === selectedBranchId.value)
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
    void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
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
      r.branchName.toLowerCase() === selectedBranch.value?.name.toLowerCase()
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
    (r) => r.status === 'CONFIRMED' || r.status === 'PENDING'
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

// "Needs You Now" alerts (Most urgent first)
const needsYouItems = computed(() => {
  const alerts: any[] = [];
  for (const r of activeFloorReservations.value) {
    const elapsed = getElapsedMinutes(r);
    alerts.push({
      id: r.id,
      type: 'IN_PLAY',
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
  const end = res.completedAt ? new Date(res.completedAt).getTime() : Date.now();
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
    void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
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
    void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
  },
  onError: (err: any) => {
    actionError.value =
      err?.response?.data?.message || err?.message || 'Cancel failed.';
  },
});

async function submitCancel() {
  if (!cancelTargetRes.value) return;
  actionError.value = '';
  reservationsStore.cancelReservation(cancelTargetRes.value.id, cancelReason.value);
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
    void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
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
    Date.now() + walkInDuration.value * 3600000
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
  return branchFloorTables.filter((t) => getTableStatus(t).state === 'OCCUPIED');
});

// Tables eligible to receive a game:
// 1. Must have a started session (state === 'OCCUPIED')
// 2. Must not already have a game assigned (not an assigned table)
const eligibleTablesForGame = computed(() => {
  return activeStartedTables.value.filter(
    (t) => getTableAssignedGames(t.id).length === 0
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
      (g) => g.toLowerCase() !== gameName.toLowerCase()
    );
  }
}

function returnGameFromAnyTable(gameName: string) {
  for (const tId of Object.keys(tableAssignedGames.value)) {
    tableAssignedGames.value[tId] = tableAssignedGames.value[tId].filter(
      (g) => g.toLowerCase() !== gameName.toLowerCase()
    );
  }
}

const catalogGames = ref<CatalogGame[]>([...games]);
const activeInUseGamesCount = computed(() => {
  return Object.values(tableAssignedGames.value).flat().length;
});

const filteredGames = computed(() => {
  const q = gameSearchQuery.value.trim().toLowerCase();
  return catalogGames.value.filter((g) => {
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
  return catalogGames.value.filter((g) => {
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
  const tbl = branchFloorTables.find((t) => t.id === assignGameTargetTableId.value);
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
      stockCategoryFilter.value === 'ALL' || item.category === stockCategoryFilter.value;
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
  branchStockItems.value.reduce((s, it) => s + it.currentStock, 0)
);
const healthyStockCount = computed(
  () => branchStockItems.value.filter((it) => it.currentStock > it.lowStockThreshold).length
);
const lowStockCount = computed(
  () =>
    branchStockItems.value.filter(
      (it) => it.currentStock <= it.lowStockThreshold && it.currentStock > 0
    ).length
);
const outOfStockCount = computed(
  () => branchStockItems.value.filter((it) => it.currentStock === 0).length
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
  category: 'beverages' as 'beverages' | 'snacks' | 'tea_coffee' | 'bakery_desserts',
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
  branchStockItems.value = branchStockItems.value.filter((i) => i.id !== itemId);
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
    0
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
      stockItem.currentStock = Math.max(0, stockItem.currentStock - it.quantity);
    }
  }

  reservationsStore.addStaffSnackOrder(
    staffSnackReservation.value.id,
    staffSnackSelectedItems.value,
    'Added by staff at counter'
  );
  staffSnackReservation.value = null;
}

// Staff Table Bill Modal
const staffBillReservation = ref<any>(null);

function openCheckBill(res: any) {
  const found = reservationsStore.reservations.value.find(
    (r) => r.id === res.id
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
// FEATURE: STAFF & SHIFTS
// ==========================================
interface StaffMember {
  id: string;
  name: string;
  role: 'Floor Lead' | 'Game Master' | 'Counter & POS' | 'Branch Manager';
  shift: string;
  status: 'ON_DUTY' | 'ON_BREAK' | 'OFF_DUTY';
  assignedZone: string;
}

const shiftOptions = [
  'Morning (10:00 - 16:00)',
  'Evening (16:00 - 23:00)',
  'Night (18:00 - 01:00)',
  'Full Day (10:00 - 20:00)',
];

const staffRoles: StaffMember['role'][] = [
  'Floor Lead',
  'Game Master',
  'Counter & POS',
  'Branch Manager',
];

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

const showAddStaffModal = ref(false);
const editingStaffId = ref<string | null>(null);
const staffForm = ref({
  name: '',
  role: 'Floor Lead' as StaffMember['role'],
  shift: 'Evening (16:00 - 23:00)',
  status: 'ON_DUTY' as StaffMember['status'],
  assignedZone: 'Tables 1 - 3',
});

function openAddStaffModal() {
  editingStaffId.value = null;
  staffForm.value = {
    name: '',
    role: 'Floor Lead',
    shift: 'Evening (16:00 - 23:00)',
    status: 'ON_DUTY',
    assignedZone: 'Tables 1 - 3',
  };
  showAddStaffModal.value = true;
}

function openEditStaffModal(staff: StaffMember) {
  editingStaffId.value = staff.id;
  staffForm.value = {
    name: staff.name,
    role: staff.role,
    shift: staff.shift,
    status: staff.status,
    assignedZone: staff.assignedZone,
  };
  showAddStaffModal.value = true;
}

function saveStaff() {
  if (!staffForm.value.name.trim()) return;
  if (editingStaffId.value) {
    const idx = branchStaffRoster.value.findIndex((s) => s.id === editingStaffId.value);
    if (idx !== -1) {
      branchStaffRoster.value[idx] = {
        ...branchStaffRoster.value[idx],
        name: staffForm.value.name.trim(),
        role: staffForm.value.role,
        shift: staffForm.value.shift,
        status: staffForm.value.status,
        assignedZone: staffForm.value.assignedZone.trim() || 'Floor Operations',
      };
    }
  } else {
    branchStaffRoster.value.push({
      id: `s${Date.now()}`,
      name: staffForm.value.name.trim(),
      role: staffForm.value.role,
      shift: staffForm.value.shift,
      status: staffForm.value.status,
      assignedZone: staffForm.value.assignedZone.trim() || 'Floor Operations',
    });
  }
  showAddStaffModal.value = false;
}

function deleteStaff(id: string) {
  branchStaffRoster.value = branchStaffRoster.value.filter((s) => s.id !== id);
}

// ==========================================
// FEATURE: REVENUE & ANALYTICS
// ==========================================
const totalTableFeesToday = computed(() => {
  return activeFloorReservations.value.reduce(
    (sum, r) => sum + calculateCurrentPlayFee(r),
    0
  );
});
const todayFoodDrinkRevenue = ref(2450);
const todayTotalGross = computed(
  () => totalTableFeesToday.value + todayFoodDrinkRevenue.value
);

// ==========================================
// FEATURE: BUSINESS OVERVIEW & MANAGE BRANCHES
// ==========================================
export interface BranchRecord {
  id: string;
  name: string;
  code: string;
  status: 'Active' | 'Inactive';
  address: string;
  city: string;
  phone: string;
  email: string;
  totalTables: number;
  availableTables: number;
  reservedTables: number;
  occupiedTables: number;
  checkInsToday: number;
  openingHours: {
    monFri: string;
    sat: string;
    sun: string;
  };
}

const branchesList = ref<BranchRecord[]>([
  {
    id: 'b1',
    name: 'The Parlor · Siam Square',
    code: 'BKK-SQ1',
    status: 'Active',
    address: '388 Rama I Rd, Pathum Wan',
    city: 'Bangkok',
    phone: '+66 2 251 8899',
    email: 'siamsquare@theparlor.com',
    totalTables: 20,
    availableTables: 16,
    reservedTables: 3,
    occupiedTables: 1,
    checkInsToday: 14,
    openingHours: {
      monFri: '11:00 - 22:00',
      sat: '10:00 - 23:00',
      sun: '10:00 - 22:00',
    },
  },
  {
    id: 'b2',
    name: 'The Parlor · Asok Station',
    code: 'BKK-AS2',
    status: 'Active',
    address: '211 Sukhumvit Soi 21, Watthana',
    city: 'Bangkok',
    phone: '+66 2 664 3322',
    email: 'asok@theparlor.com',
    totalTables: 15,
    availableTables: 12,
    reservedTables: 2,
    occupiedTables: 1,
    checkInsToday: 9,
    openingHours: {
      monFri: '11:00 - 22:00',
      sat: '10:00 - 23:00',
      sun: '10:00 - 22:00',
    },
  },
  {
    id: 'b3',
    name: 'The Parlor · Silom Edge',
    code: 'BKK-SL3',
    status: 'Active',
    address: '2 Silom Rd, Bang Rak',
    city: 'Bangkok',
    phone: '+66 2 234 5678',
    email: 'silom@theparlor.com',
    totalTables: 18,
    availableTables: 14,
    reservedTables: 3,
    occupiedTables: 1,
    checkInsToday: 11,
    openingHours: {
      monFri: '10:00 - 23:00',
      sat: '10:00 - 23:30',
      sun: '10:00 - 22:00',
    },
  },
  {
    id: 'b4',
    name: 'The Parlor · Thonglor 13 (Opening Soon)',
    code: 'BKK-TL4',
    status: 'Inactive',
    address: '138 Soi Thonglor 13, Khlong Tan Nuea',
    city: 'Bangkok',
    phone: '+66 2 712 9900',
    email: 'thonglor@theparlor.com',
    totalTables: 24,
    availableTables: 24,
    reservedTables: 0,
    occupiedTables: 0,
    checkInsToday: 0,
    openingHours: {
      monFri: '12:00 - 22:00',
      sat: '11:00 - 23:00',
      sun: '11:00 - 22:00',
    },
  },
]);

const branchSearchQuery = ref('');
const branchStatusFilter = ref<'All' | 'Active' | 'Inactive'>('All');

const filteredBranches = computed(() => {
  return branchesList.value.filter((b) => {
    const matchesSearch =
      b.name.toLowerCase().includes(branchSearchQuery.value.toLowerCase()) ||
      b.code.toLowerCase().includes(branchSearchQuery.value.toLowerCase()) ||
      b.city.toLowerCase().includes(branchSearchQuery.value.toLowerCase());
    const matchesStatus =
      branchStatusFilter.value === 'All' || b.status === branchStatusFilter.value;
    return matchesSearch && matchesStatus;
  });
});

const overviewTotalBranches = computed(() => branchesList.value.length);
const overviewTotalTables = computed(() =>
  branchesList.value.reduce((acc, b) => acc + b.totalTables, 0)
);
const overviewTotalCheckIns = computed(() =>
  branchesList.value.reduce((acc, b) => acc + b.checkInsToday, 0)
);
const overviewTotalActiveReservations = computed(() =>
  branchesList.value.reduce((acc, b) => acc + b.reservedTables + b.occupiedTables, 0)
);

const showAddBranchModal = ref(false);
const editingBranchId = ref<string | null>(null);
const branchForm = ref({
  name: '',
  code: '',
  status: 'Active' as 'Active' | 'Inactive',
  address: '',
  city: 'Bangkok',
  phone: '',
  email: '',
  totalTables: 12,
  monFriOpen: '11:00',
  monFriClose: '22:00',
  satOpen: '10:00',
  satClose: '23:00',
  sunOpen: '10:00',
  sunClose: '22:00',
});

function openAddBranchModal() {
  editingBranchId.value = null;
  branchForm.value = {
    name: '',
    code: `BKK-BR${branchesList.value.length + 1}`,
    status: 'Active',
    address: '',
    city: 'Bangkok',
    phone: '',
    email: '',
    totalTables: 12,
    monFriOpen: '11:00',
    monFriClose: '22:00',
    satOpen: '10:00',
    satClose: '23:00',
    sunOpen: '10:00',
    sunClose: '22:00',
  };
  showAddBranchModal.value = true;
}

function parseTimeString(timeRange: string | undefined, fallbackOpen: string, fallbackClose: string): [string, string] {
  if (!timeRange) return [fallbackOpen, fallbackClose];
  const matches = timeRange.match(/\b\d{1,2}:\d{2}\b/g);
  if (matches && matches.length >= 2) {
    const open = matches[0].padStart(5, '0');
    const close = matches[1].padStart(5, '0');
    return [open, close];
  }
  return [fallbackOpen, fallbackClose];
}

function openEditBranchModal(b: BranchRecord) {
  editingBranchId.value = b.id;
  const [monFriOpen, monFriClose] = parseTimeString(b.openingHours?.monFri, '11:00', '22:00');
  const [satOpen, satClose] = parseTimeString(b.openingHours?.sat, '10:00', '23:00');
  const [sunOpen, sunClose] = parseTimeString(b.openingHours?.sun, '10:00', '22:00');
  branchForm.value = {
    name: b.name,
    code: b.code,
    status: b.status,
    address: b.address,
    city: b.city,
    phone: b.phone,
    email: b.email,
    totalTables: b.totalTables,
    monFriOpen,
    monFriClose,
    satOpen,
    satClose,
    sunOpen,
    sunClose,
  };
  showAddBranchModal.value = true;
}

function saveBranch() {
  if (!branchForm.value.name.trim() || !branchForm.value.code.trim()) return;
  const openingHours = {
    monFri: `${branchForm.value.monFriOpen || '11:00'} - ${branchForm.value.monFriClose || '22:00'}`,
    sat: `${branchForm.value.satOpen || '10:00'} - ${branchForm.value.satClose || '23:00'}`,
    sun: `${branchForm.value.sunOpen || '10:00'} - ${branchForm.value.sunClose || '22:00'}`,
  };

  if (editingBranchId.value) {
    const idx = branchesList.value.findIndex((b) => b.id === editingBranchId.value);
    if (idx !== -1) {
      branchesList.value[idx] = {
        ...branchesList.value[idx],
        name: branchForm.value.name,
        code: branchForm.value.code,
        status: branchForm.value.status,
        address: branchForm.value.address,
        city: branchForm.value.city,
        phone: branchForm.value.phone,
        email: branchForm.value.email,
        totalTables: Number(branchForm.value.totalTables),
        openingHours,
      };
    }
  } else {
    branchesList.value.push({
      id: `b${Date.now()}`,
      name: branchForm.value.name,
      code: branchForm.value.code,
      status: branchForm.value.status,
      address: branchForm.value.address,
      city: branchForm.value.city,
      phone: branchForm.value.phone,
      email: branchForm.value.email,
      totalTables: Number(branchForm.value.totalTables),
      availableTables: Number(branchForm.value.totalTables),
      reservedTables: 0,
      occupiedTables: 0,
      checkInsToday: 0,
      openingHours,
    });
  }
  showAddBranchModal.value = false;
}

function toggleBranchStatus(b: BranchRecord) {
  b.status = b.status === 'Active' ? 'Inactive' : 'Active';
}

function deleteBranch(bId: string) {
  if (confirm('Are you sure you want to delete this branch location?')) {
    branchesList.value = branchesList.value.filter((b) => b.id !== bId);
  }
}

// ==========================================
// FEATURE: PHYSICAL TABLE INVENTORY MANAGEMENT
// ==========================================
export type PhysicalTableShape = 'Round' | 'Square' | 'Rectangle' | 'Oval';
export type PhysicalTableStatus = 'Available' | 'Reserved' | 'Occupied' | 'Unavailable';

export interface PhysicalTableRecord {
  id: string;
  name: string;
  branchName: string;
  capacity: number;
  shape: PhysicalTableShape;
  status: PhysicalTableStatus;
  active: boolean;
  lastUpdated: string;
}

const physicalTablesList = ref<PhysicalTableRecord[]>([
  { id: 'T01', name: 'Table 1', branchName: 'The Parlor · Siam Square', capacity: 2, shape: 'Round', status: 'Available', active: true, lastUpdated: '10 mins ago' },
  { id: 'T02', name: 'Table 2', branchName: 'The Parlor · Siam Square', capacity: 4, shape: 'Square', status: 'Reserved', active: true, lastUpdated: '5 mins ago' },
  { id: 'T03', name: 'Table 3', branchName: 'The Parlor · Siam Square', capacity: 4, shape: 'Square', status: 'Available', active: true, lastUpdated: 'Just now' },
  { id: 'T04', name: 'Table 4', branchName: 'The Parlor · Siam Square', capacity: 4, shape: 'Round', status: 'Occupied', active: true, lastUpdated: '45 mins ago' },
  { id: 'T05', name: 'Table 5', branchName: 'The Parlor · Siam Square', capacity: 6, shape: 'Rectangle', status: 'Available', active: true, lastUpdated: '1 hour ago' },
  { id: 'T06', name: 'Table 6', branchName: 'The Parlor · Siam Square', capacity: 6, shape: 'Rectangle', status: 'Available', active: true, lastUpdated: '2 hours ago' },
  { id: 'T07', name: 'Table 7 (VIP)', branchName: 'The Parlor · Siam Square', capacity: 8, shape: 'Oval', status: 'Reserved', active: true, lastUpdated: '15 mins ago' },
  { id: 'T08', name: 'Table 8 (Private)', branchName: 'The Parlor · Siam Square', capacity: 6, shape: 'Rectangle', status: 'Available', active: true, lastUpdated: '3 hours ago' },
  { id: 'T09', name: 'Table 9 (Rooftop)', branchName: 'The Parlor · Siam Square', capacity: 4, shape: 'Round', status: 'Occupied', active: true, lastUpdated: '30 mins ago' },
  { id: 'T10', name: 'Table 10 (Maintenance)', branchName: 'The Parlor · Siam Square', capacity: 4, shape: 'Square', status: 'Unavailable', active: false, lastUpdated: 'Yesterday' },
]);

const tableSearchQuery = ref('');
const tableFilterBranch = ref('The Parlor · Siam Square');
const tableFilterStatus = ref<string>('All statuses');

const filteredPhysicalTables = computed(() => {
  return physicalTablesList.value.filter((t) => {
    const matchesSearch =
      t.name.toLowerCase().includes(tableSearchQuery.value.toLowerCase()) ||
      t.id.toLowerCase().includes(tableSearchQuery.value.toLowerCase());
    const matchesBranch =
      tableFilterBranch.value === 'All branches' || t.branchName === tableFilterBranch.value;
    const matchesStatus =
      tableFilterStatus.value === 'All statuses' || t.status === tableFilterStatus.value;
    return matchesSearch && matchesBranch && matchesStatus;
  });
});

const tableCountTotal = computed(() => physicalTablesList.value.length);
const tableCountAvailable = computed(
  () => physicalTablesList.value.filter((t) => t.status === 'Available').length
);
const tableCountReserved = computed(
  () => physicalTablesList.value.filter((t) => t.status === 'Reserved').length
);
const tableCountOccupied = computed(
  () => physicalTablesList.value.filter((t) => t.status === 'Occupied').length
);

const showAddTableModal = ref(false);
const editingTableId = ref<string | null>(null);
const tableForm = ref({
  name: '',
  branchName: 'The Parlor · Siam Square',
  capacity: 4,
  shape: 'Square' as PhysicalTableShape,
  status: 'Available' as PhysicalTableStatus,
  active: true,
});

function openAddTableModal() {
  editingTableId.value = null;
  tableForm.value = {
    name: `Table ${physicalTablesList.value.length + 1}`,
    branchName:
      tableFilterBranch.value === 'All branches'
        ? 'The Parlor · Siam Square'
        : tableFilterBranch.value,
    capacity: 4,
    shape: 'Square',
    status: 'Available',
    active: true,
  };
  showAddTableModal.value = true;
}

function openEditTableModal(t: PhysicalTableRecord) {
  editingTableId.value = t.id;
  tableForm.value = {
    name: t.name,
    branchName: t.branchName,
    capacity: t.capacity,
    shape: t.shape,
    status: t.status,
    active: t.active,
  };
  showAddTableModal.value = true;
}

function saveTable() {
  if (!tableForm.value.name.trim()) return;
  if (editingTableId.value) {
    const idx = physicalTablesList.value.findIndex((t) => t.id === editingTableId.value);
    if (idx !== -1) {
      physicalTablesList.value[idx] = {
        ...physicalTablesList.value[idx],
        name: tableForm.value.name,
        branchName: tableForm.value.branchName,
        capacity: Number(tableForm.value.capacity),
        shape: tableForm.value.shape,
        status: tableForm.value.status,
        active: tableForm.value.active,
        lastUpdated: 'Just now',
      };
    }
  } else {
    physicalTablesList.value.push({
      id: `T${String(physicalTablesList.value.length + 1).padStart(2, '0')}`,
      name: tableForm.value.name,
      branchName: tableForm.value.branchName,
      capacity: Number(tableForm.value.capacity),
      shape: tableForm.value.shape,
      status: tableForm.value.status,
      active: tableForm.value.active,
      lastUpdated: 'Just now',
    });
  }
  showAddTableModal.value = false;
}

function deleteTable(tId: string) {
  if (confirm('Are you sure you want to delete this table from floor inventory?')) {
    physicalTablesList.value = physicalTablesList.value.filter((t) => t.id !== tId);
  }
}

// ==========================================
// FEATURE: BOARD GAME CATALOG MANAGEMENT
// ==========================================
const showAddGameModal = ref(false);
const editingGameId = ref<string | null>(null);
const gameForm = ref({
  title: '',
  designer: '',
  categories: 'Strategy',
  complexity: 'Medium' as 'Light' | 'Medium' | 'Heavy',
  minPlayers: 2,
  maxPlayers: 4,
  durationMinutes: 45,
  copiesCount: 2,
  description: '',
  coverImage: '',
});

function openAddGameModal() {
  editingGameId.value = null;
  gameForm.value = {
    title: '',
    designer: '',
    categories: 'Strategy, Board Game',
    complexity: 'Medium',
    minPlayers: 2,
    maxPlayers: 4,
    durationMinutes: 45,
    copiesCount: 2,
    description: 'Engaging board game experience for parlor visitors.',
    coverImage:
      'https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?auto=format&fit=crop&w=400&q=80',
  };
  showAddGameModal.value = true;
}

function openEditGameModal(g: CatalogGame) {
  editingGameId.value = g.id;
  gameForm.value = {
    title: g.name,
    designer: g.designer || '',
    categories: g.categories.join(', '),
    complexity:
      g.difficulty === 'Easy'
        ? 'Light'
        : g.difficulty === 'Advanced'
          ? 'Heavy'
          : 'Medium',
    minPlayers: g.players[0] || 2,
    maxPlayers: g.players[1] || 4,
    durationMinutes: g.playMinutes[1] || 45,
    copiesCount: 2,
    description: g.description || '',
    coverImage: '',
  };
  showAddGameModal.value = true;
}

function saveGame() {
  if (!gameForm.value.title.trim()) return;
  const validCategories: (
    | 'Party & Casual'
    | 'Social Deduction'
    | 'Strategy'
    | 'Cooperative'
    | 'Mystery & Puzzle'
    | 'Quick & Light'
    | 'Adventure & Immersive'
  )[] = ['Strategy'];
  const catArray = gameForm.value.categories
    .split(',')
    .map((c) => c.trim())
    .filter(Boolean) as (
    | 'Party & Casual'
    | 'Social Deduction'
    | 'Strategy'
    | 'Cooperative'
    | 'Mystery & Puzzle'
    | 'Quick & Light'
    | 'Adventure & Immersive'
  )[];
  const finalCategories = catArray.length ? catArray : validCategories;
  const difficulty: 'Easy' | 'Medium' | 'Advanced' =
    gameForm.value.complexity === 'Light'
      ? 'Easy'
      : gameForm.value.complexity === 'Heavy'
        ? 'Advanced'
        : 'Medium';

  if (editingGameId.value) {
    const idx = catalogGames.value.findIndex((g) => g.id === editingGameId.value);
    if (idx !== -1) {
      catalogGames.value[idx] = {
        ...catalogGames.value[idx],
        name: gameForm.value.title,
        designer: gameForm.value.designer,
        categories: finalCategories,
        difficulty,
        players: [Number(gameForm.value.minPlayers), Number(gameForm.value.maxPlayers)],
        playMinutes: [
          Math.floor(Number(gameForm.value.durationMinutes) * 0.7),
          Number(gameForm.value.durationMinutes),
        ],
        description: gameForm.value.description || catalogGames.value[idx].description,
      };
    }
  } else {
    catalogGames.value.unshift({
      id: `g-${Date.now()}`,
      name: gameForm.value.title,
      designer: gameForm.value.designer || 'The Parlor Studio',
      categories: finalCategories,
      mechanics: ['Worker Placement', 'Tile Placement'],
      players: [Number(gameForm.value.minPlayers), Number(gameForm.value.maxPlayers)],
      playMinutes: [
        Math.floor(Number(gameForm.value.durationMinutes) * 0.7),
        Number(gameForm.value.durationMinutes),
      ],
      teachMinutes: 10,
      setupMinutes: 5,
      difficulty,
      language: 'English / Thai',
      description:
        gameForm.value.description ||
        'Engaging board game experience for parlor visitors.',
      retailPrice: null,
    });
  }
  showAddGameModal.value = false;
}

function deleteGame(gId: string) {
  if (confirm('Are you sure you want to remove this board game from library catalog?')) {
    catalogGames.value = catalogGames.value.filter((g) => g.id !== gId);
  }
}

// ==========================================
// FEATURE: VENUE SETTINGS & POLICIES
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

export interface VenuePolicyItem {
  id: string;
  title: string;
  category: 'Reservations' | 'Billing & Rates' | 'Floor & Operations' | 'Safety & Conduct';
  description: string;
  metric: string;
  active: boolean;
}

const venuePoliciesList = ref<VenuePolicyItem[]>([
  {
    id: 'pol-1',
    title: 'Automatic 15-Minute No-Show Release',
    category: 'Reservations',
    description: 'When a customer reservation starts and they do not arrive within 15 minutes, the system automatically transitions their status to NO_SHOW and releases their table for walk-ins.',
    metric: '15 Mins Grace',
    active: true,
  },
  {
    id: 'pol-2',
    title: 'Dynamic Elapsed-Time Play Calculation',
    category: 'Billing & Rates',
    description: 'If customers arrive late, table play fee is dynamically computed based strictly on actual elapsed time from check-in to settlement at the counter.',
    metric: 'Real-time billing',
    active: true,
  },
  {
    id: 'pol-3',
    title: 'Standard Table Base Hourly Rates',
    category: 'Billing & Rates',
    description: '2-Seater Table: ฿150/hr · 4-Seater Table: ฿200/hr · 6-Seater Table: ฿300/hr. VIP surcharge: +25%.',
    metric: '฿150 - ฿300/hr',
    active: true,
  },
  {
    id: 'pol-4',
    title: 'Reservation Security Deposit Policy',
    category: 'Reservations',
    description: 'Large parties of 6 or more require a ฿100 advance deposit, fully creditable towards food, beverage, or table play fee upon arrival.',
    metric: '฿100 Deposit',
    active: true,
  },
  {
    id: 'pol-5',
    title: 'Game Component Care & Replacement Policy',
    category: 'Floor & Operations',
    description: 'Missing or ruined board game pieces are logged by Game Masters upon return. Deliberate damage is billed at retail replacement cost.',
    metric: 'Fair Play',
    active: true,
  },
]);

const showPolicyModal = ref(false);
const editingPolicyId = ref<string | null>(null);
const policyCategories: VenuePolicyItem['category'][] = [
  'Reservations',
  'Billing & Rates',
  'Floor & Operations',
  'Safety & Conduct',
];

const policyForm = ref({
  title: '',
  category: 'Reservations' as VenuePolicyItem['category'],
  metric: '',
  description: '',
  active: true,
});

function openAddPolicyModal() {
  editingPolicyId.value = null;
  policyForm.value = {
    title: '',
    category: 'Reservations',
    metric: '',
    description: '',
    active: true,
  };
  showPolicyModal.value = true;
}

function openEditPolicyModal(p: VenuePolicyItem) {
  editingPolicyId.value = p.id;
  policyForm.value = {
    title: p.title,
    category: p.category,
    metric: p.metric || '',
    description: p.description,
    active: p.active,
  };
  showPolicyModal.value = true;
}

function savePolicy() {
  if (!policyForm.value.title.trim()) return;
  if (editingPolicyId.value) {
    const idx = venuePoliciesList.value.findIndex((p) => p.id === editingPolicyId.value);
    if (idx !== -1) {
      venuePoliciesList.value[idx] = {
        ...venuePoliciesList.value[idx],
        title: policyForm.value.title.trim(),
        category: policyForm.value.category,
        metric: policyForm.value.metric.trim(),
        description: policyForm.value.description.trim(),
        active: policyForm.value.active,
      };
    }
  } else {
    venuePoliciesList.value.push({
      id: `pol-${Date.now()}`,
      title: policyForm.value.title.trim(),
      category: policyForm.value.category,
      metric: policyForm.value.metric.trim(),
      description: policyForm.value.description.trim(),
      active: policyForm.value.active,
    });
  }
  showPolicyModal.value = false;
}

function deletePolicy(id: string) {
  if (confirm('Are you sure you want to delete this venue operational policy?')) {
    venuePoliciesList.value = venuePoliciesList.value.filter((p) => p.id !== id);
  }
}

function togglePolicyActive(p: VenuePolicyItem) {
  p.active = !p.active;
}
</script>

<template>
  <div class="gl-admin-layout">
    <!-- Left Navigation Sidebar -->
    <aside class="gl-sidebar" :class="{ collapsed: isSidebarCollapsed }">
      <!-- Brand & Role Indicator -->
      <div class="gl-sidebar-brand">
        <div class="brand-dice-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <rect x="2" y="2" width="20" height="20" rx="5" fill="#142b20" stroke="#c6ad7a" stroke-width="1.5"/>
            <circle cx="7" cy="7" r="1.8" fill="#ddc18c"/>
            <circle cx="17" cy="7" r="1.8" fill="#ddc18c"/>
            <circle cx="12" cy="12" r="1.8" fill="#ddc18c"/>
            <circle cx="7" cy="17" r="1.8" fill="#ddc18c"/>
            <circle cx="17" cy="17" r="1.8" fill="#ddc18c"/>
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
        @click="toggleSidebar"
        :aria-label="isSidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        :title="isSidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
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
            <polyline points="11 17 6 12 11 7"/>
            <polyline points="18 17 13 12 18 7"/>
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
          @click.prevent="selectNavTab('dashboard')"
        >
          <span class="nav-icon-box">
            <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="7" height="7"/>
              <rect x="14" y="3" width="7" height="7"/>
              <rect x="14" y="14" width="7" height="7"/>
              <rect x="3" y="14" width="7" height="7"/>
            </svg>
          </span>
          <span class="nav-label">Dashboard</span>
        </a>

        <a
          href="#floor"
          class="nav-item"
          :class="{ active: activeTab === 'floor' }"
          :title="isSidebarCollapsed ? 'Tables & Floor' : undefined"
          @click.prevent="selectNavTab('floor')"
        >
          <span class="nav-icon-box">
            <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 3h18v18H3z"/>
              <path d="M3 9h18M9 21V9"/>
            </svg>
          </span>
          <span class="nav-label">Tables & Floor</span>
        </a>

        <a
          href="#sessions"
          class="nav-item"
          :class="{ active: activeTab === 'sessions' }"
          :title="isSidebarCollapsed ? 'Live Sessions' : undefined"
          @click.prevent="selectNavTab('sessions')"
        >
          <span class="nav-icon-box">
            <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
          </span>
          <span class="nav-label">Live Sessions</span>
          <span v-if="activeFloorReservations.length > 0" class="nav-badge-gold">
            {{ activeFloorReservations.length }}
          </span>
        </a>

        <a
          href="#bookings"
          class="nav-item"
          :class="{ active: activeTab === 'bookings' }"
          :title="isSidebarCollapsed ? 'Bookings & Timeline' : undefined"
          @click.prevent="selectNavTab('bookings')"
        >
          <span class="nav-icon-box">
            <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
              <line x1="16" y1="2" x2="16" y2="6"/>
              <line x1="8" y1="2" x2="8" y2="6"/>
              <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
          </span>
          <span class="nav-label">Bookings & Timeline</span>
        </a>

        <a
          href="#library"
          class="nav-item"
          :class="{ active: activeTab === 'library' }"
          :title="isSidebarCollapsed ? 'Game Library' : undefined"
          @click.prevent="selectNavTab('library')"
        >
          <span class="nav-icon-box">
            <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
          </span>
          <span class="nav-label">Game Library</span>
        </a>

        <!-- Refreshments & Stock Management (Available to staff, manager & admin) -->
        <a
          href="#inventory"
          class="nav-item"
          :class="{ active: activeTab === 'inventory' }"
          :title="isSidebarCollapsed ? 'Refreshments & Stock' : undefined"
          @click.prevent="selectNavTab('inventory')"
        >
          <span class="nav-icon-box">
            <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
              <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
              <line x1="12" y1="22.08" x2="12" y2="12"/>
            </svg>
          </span>
          <span class="nav-label">Refreshments & Stock</span>
        </a>

        <!-- Administration (Unified Admin & Management) -->
        <template v-if="isAdmin">
          <div class="nav-section-title admin-title">
            <span>ADMINISTRATION</span>
            <span class="role-pill-tiny admin">Admin</span>
          </div>

          <a
            href="#branches"
            class="nav-item"
            :class="{ active: activeTab === 'branches' }"
            :title="isSidebarCollapsed ? 'Business & Branches' : undefined"
            @click.prevent="selectNavTab('branches')"
          >
            <span class="nav-icon-box">
              <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 21h18"/>
                <path d="M9 8h1"/>
                <path d="M9 12h1"/>
                <path d="M9 16h1"/>
                <path d="M14 8h1"/>
                <path d="M14 12h1"/>
                <path d="M14 16h1"/>
                <path d="M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16"/>
              </svg>
            </span>
            <span class="nav-label">Business & Branches</span>
          </a>

          <a
            href="#manage-tables"
            class="nav-item"
            :class="{ active: activeTab === 'manage-tables' }"
            :title="isSidebarCollapsed ? 'Manage Tables' : undefined"
            @click.prevent="selectNavTab('manage-tables')"
          >
            <span class="nav-icon-box">
              <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2"/>
                <path d="M3 9h18"/>
                <path d="M9 21V9"/>
              </svg>
            </span>
            <span class="nav-label">Manage Tables</span>
          </a>

          <a
            href="#staff-roster"
            class="nav-item"
            :class="{ active: activeTab === 'staff-roster' }"
            :title="isSidebarCollapsed ? 'Staff & Shifts' : undefined"
            @click.prevent="selectNavTab('staff-roster')"
          >
            <span class="nav-icon-box">
              <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </span>
            <span class="nav-label">Staff & Shifts</span>
          </a>

          <a
            href="#analytics"
            class="nav-item"
            :class="{ active: activeTab === 'analytics' }"
            :title="isSidebarCollapsed ? 'Revenue & Analytics' : undefined"
            @click.prevent="selectNavTab('analytics')"
          >
            <span class="nav-icon-box">
              <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="20" x2="18" y2="10"/>
                <line x1="12" y1="20" x2="12" y2="4"/>
                <line x1="6" y1="20" x2="6" y2="14"/>
              </svg>
            </span>
            <span class="nav-label">Revenue & Analytics</span>
          </a>

          <a
            href="#policies"
            class="nav-item"
            :class="{ active: activeTab === 'policies' }"
            :title="isSidebarCollapsed ? 'Venue Policies' : undefined"
            @click.prevent="selectNavTab('policies')"
          >
            <span class="nav-icon-box">
              <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
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
            <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
              <polyline points="9 22 9 12 15 12 15 22"/>
            </svg>
          </span>
          <span class="nav-label">Public Store</span>
        </RouterLink>
      </nav>

      <!-- Sidebar User Footer -->
      <div class="sidebar-footer">
        <div class="user-profile-row">
          <div class="user-avatar-gold" :title="auth.username || 'Staff Member'">
            {{ (auth.username || 'Staff').slice(0, 2).toUpperCase() }}
          </div>
          <div class="user-info-text">
            <strong class="user-name">{{ auth.username || 'Staff Member' }}</strong>
            <span class="user-role-label">{{ userRoleTitle }}</span>
          </div>
        </div>
        <button type="button" class="btn-signout" @click="auth.logout()">
          Sign Out
        </button>
      </div>
    </aside>

    <!-- Mobile Sidebar Backdrop Overlay -->
    <div
      v-if="!isSidebarCollapsed"
      class="sidebar-mobile-overlay"
      @click="isSidebarCollapsed = true"
    ></div>

    <!-- Main Content Area -->
    <div class="gl-main-wrapper">
      <!-- Top Header Navigation Bar -->
      <header class="gl-topbar">
        <div class="topbar-left">
          <button
            type="button"
            class="mobile-menu-toggle"
            @click="toggleSidebar"
            :aria-label="isSidebarCollapsed ? 'Open navigation menu' : 'Close navigation menu'"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>

          <div class="branch-selector-capsule">
            <span class="pin-icon">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                <circle cx="12" cy="10" r="3"/>
              </svg>
            </span>
            <select v-model="selectedBranchId" class="branch-select">
              <option v-for="b in branches" :key="b.id" :value="b.id">
                The Parlor · {{ b.name }} Branch
              </option>
            </select>
          </div>

          <div class="console-role-pill">
            <span class="pulse-indicator"></span>
            <span>Central {{ userRoleTitle }} Hub</span>
          </div>
        </div>

        <div class="topbar-search">
          <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            v-model="searchQuery"
            type="search"
            placeholder="Search games, guests, or tables..."
            class="topbar-search-input"
          />
        </div>

        <div class="topbar-actions">
          <button type="button" class="icon-circle-btn" title="Refresh Live Data" @click="refreshAll">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
          </button>

          <RouterLink :to="{ name: 'HomeIndex' }" class="icon-circle-btn" title="View Customer Storefront">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
              <line x1="8" y1="21" x2="16" y2="21"/>
              <line x1="12" y1="17" x2="12" y2="21"/>
            </svg>
          </RouterLink>

          <div class="topbar-avatar" :title="userRoleTitle">
            {{ (auth.username || 'Staff').slice(0, 2).toUpperCase() }}
          </div>
        </div>
      </header>

      <!-- Main Operational Console Body -->
      <main class="gl-content">
        <!-- ========================================== -->
        <!-- VIEW 1: DASHBOARD TAB                      -->
        <!-- ========================================== -->
        <template v-if="activeTab === 'dashboard'">
          <!-- Block 1: Service Greeting Hero -->
          <section class="console-block hero-block">
            <div class="eyebrow-row">
              <span class="hero-eyebrow">
                TONIGHT AT THE PARLOR · {{ selectedBranch?.name.toUpperCase() || 'SIAM' }} BRANCH
              </span>
            </div>

            <div class="title-row">
              <h1 class="hero-title">{{ greeting }}</h1>
              <span class="live-pill">
                <span class="live-pulse"></span>
                Bookings: Live
              </span>
            </div>

            <p class="hero-subtitle">
              {{ allReservations.length }} bookings tonight — first arrival {{ formatTime(upcomingReservations[0]?.startsAt) || '7:30 PM' }}, largest party of {{ Math.max(...branchFloorTables.map(t => t.seats)) }}, {{ needsYouItems.length }} item{{ needsYouItems.length === 1 ? '' : 's' }} need attention.
            </p>

            <!-- Hero Action Buttons Bar -->
            <div class="hero-actions-bar">
              <button
                type="button"
                class="btn-primary-parlor"
                @click="openWalkInForTable()"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                  <circle cx="8.5" cy="7" r="4"/>
                  <line x1="20" y1="8" x2="20" y2="14"/>
                  <line x1="23" y1="11" x2="17" y2="11"/>
                </svg>
                <span>Seat walk-in</span>
              </button>

              <button
                type="button"
                class="btn-secondary-parlor"
                @click="selectNavTab('library')"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                  <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                </svg>
                <span>Assign game</span>
              </button>

              <div class="actions-right">
                <span class="updated-time">Updated at {{ currentTimeStr }}</span>
                <button
                  type="button"
                  class="btn-refresh-parlor"
                  @click="refreshAll"
                >
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M23 4v6h-6"/>
                    <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                  </svg>
                  <span>Refresh</span>
                </button>
              </div>
            </div>
          </section>

          <!-- Block 2: 4-Column Metric Ribbon Block -->
          <section class="console-block numbers-card-ribbon">
            <div class="num-col">
              <span class="col-label">TONIGHT</span>
              <div class="col-value">{{ allReservations.length }}</div>
              <div class="col-sub">party · {{ allReservations.reduce((s, r) => s + (r.partySize || 2), 0) }} guests</div>
            </div>

            <div class="num-col">
              <span class="col-label">TABLES IN USE</span>
              <div class="col-value">{{ occupiedTablesCount }} / {{ branchFloorTables.length }}</div>
              <div class="segmented-progress">
                <span
                  v-for="(_, i) in branchFloorTables"
                  :key="i"
                  class="progress-seg"
                  :class="{ active: i < occupiedTablesCount }"
                ></span>
              </div>
              <div class="col-sub">{{ freeTablesCount }} free now</div>
            </div>

            <div class="num-col">
              <span class="col-label">ARRIVING</span>
              <div class="col-value">{{ upcomingReservations.length }}</div>
              <div class="col-sub">in the next hour</div>
            </div>

            <div class="num-col">
              <span class="col-label">ACTIVE TABS</span>
              <div class="col-value">{{ activeFloorReservations.length }}</div>
              <div class="col-sub">running refreshments on tab</div>
            </div>
          </section>

          <!-- Block 3: Needs You Now Queue -->
          <section v-if="needsYouItems.length > 0" class="console-block needs-you-card">
            <div class="needs-you-header">
              <div class="needs-you-title">
                <span class="gold-bullet">◆</span>
                <span>Needs you now</span>
                <span class="needs-count-pill">{{ needsYouItems.length }}</span>
              </div>
              <span class="most-urgent">Most urgent first</span>
            </div>

            <div class="needs-items-list">
              <div
                v-for="alert in needsYouItems"
                :key="alert.id"
                class="needs-item-row"
              >
                <div class="needs-item-left">
                  <div class="alert-icon-square">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10"/>
                      <polyline points="12 6 12 12 16 14"/>
                    </svg>
                  </div>
                  <div>
                    <strong class="needs-item-title">{{ alert.title }}</strong>
                    <p class="needs-item-sub">{{ alert.subtitle }}</p>
                  </div>
                </div>

                <div class="needs-item-actions">
                  <button
                    v-if="alert.type === 'IN_PLAY'"
                    type="button"
                    class="btn-gold-pill"
                    @click="openAddSnacks(alert.reservation)"
                  >
                    + Add Refreshments
                  </button>

                  <button
                    v-if="alert.type === 'IN_PLAY'"
                    type="button"
                    class="btn-primary-parlor small"
                    @click="openCheckBill(alert.reservation)"
                  >
                    Check Bill →
                  </button>

                  <button
                    v-if="alert.type === 'LATE'"
                    type="button"
                    class="btn-primary-parlor small"
                    @click="handleCheckIn(alert.reservation)"
                  >
                    Check-in Guest
                  </button>
                </div>
              </div>
            </div>
          </section>

          <!-- Block 4: Two-Column Lower Section: Floor Plan & Coming Up -->
          <div class="two-column-service-row">
            <!-- Left Column: Floor Plan -->
            <div class="column-left">
              <section class="console-block">
                <div class="section-card-header">
                  <div class="header-left">
                    <h2 class="section-title">Floor</h2>
                    <span class="live-tag"><span class="pulse-green"></span> Live</span>
                  </div>
                  <span class="usage-text">{{ occupiedTablesCount }} of {{ branchFloorTables.length }} in use</span>
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
                      <span class="table-seats-lbl">{{ table.seats }} seats · ฿{{ table.rate }}/hr</span>
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
                        + Seat walk-in here →
                      </button>
                    </template>

                    <!-- OCCUPIED TABLE: Session started -->
                    <template v-else-if="getTableStatus(table).state === 'OCCUPIED'">
                      <div class="tile-status-row">
                        <span class="state-badge badge-occupied">PLAYING</span>
                        <span class="tile-time-pill">
                          {{ getElapsedMinutes(getTableStatus(table).reservation) }}m
                        </span>
                      </div>
                      <p class="tile-guest-name">
                        {{ getCustomerName(getTableStatus(table).reservation?.customer) }}
                      </p>
                      <div class="tile-sub-info">
                        Tab: ฿{{ calculateCurrentPlayFee(getTableStatus(table).reservation) }}
                      </div>

                      <!-- Assigned Game Chip (If assigned, cannot assign another game) -->
                      <div v-if="getTableAssignedGames(table.id).length > 0" class="tile-assigned-games-box">
                        <div class="assigned-chips-wrap">
                          <span class="game-chip-capsule">
                            <svg class="w-3.5 h-3.5 inline mr-1 text-parlor-warning-700" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                              <rect x="3" y="3" width="18" height="18" rx="3"/>
                              <circle cx="8" cy="8" r="1" fill="currentColor"/>
                              <circle cx="16" cy="16" r="1" fill="currentColor"/>
                              <circle cx="12" cy="12" r="1" fill="currentColor"/>
                            </svg>
                            {{ getTableAssignedGames(table.id)[0] }}
                            <button
                              type="button"
                              class="btn-chip-unassign"
                              title="Return game to shelf"
                              @click.stop="unassignGameFromTable(table.id, getTableAssignedGames(table.id)[0])"
                            >
                              ✕
                            </button>
                          </span>
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
                          @click="openCheckBill(getTableStatus(table).reservation)"
                        >
                          Bill
                        </button>
                      </div>
                    </template>

                    <!-- RESERVED TABLE: NO game assignment allowed until started -->
                    <template v-else-if="getTableStatus(table).state === 'RESERVED'">
                      <div class="tile-status-row">
                        <span class="state-badge badge-reserved">RESERVED</span>
                      </div>
                      <p class="tile-guest-name">
                        {{ getCustomerName(getTableStatus(table).reservation?.customer) }}
                      </p>
                      <p class="tile-due-time">
                        Due at {{ formatTime(getTableStatus(table).reservation?.startsAt) }}
                      </p>
                      <button
                        type="button"
                        class="tile-action-link"
                        @click="handleCheckIn(getTableStatus(table).reservation)"
                      >
                        Check-in & Seat →
                      </button>
                    </template>
                  </article>
                </div>
              </section>
            </div>

            <!-- Right Column: Coming Up Timeline -->
            <div class="column-right">
              <section class="console-block">
                <div class="section-card-header">
                  <div class="header-left">
                    <h2 class="section-title">Coming up</h2>
                    <span class="timeline-span">Next 2 hours</span>
                  </div>
                  <span class="count-bubble">{{ upcomingReservations.length }}</span>
                </div>

                <div class="policy-rule-banner">
                  <span class="gold-bullet">◆</span>
                  <span>15-Minute Auto No-Show: Tables auto-release if customer is >15m late.</span>
                </div>

                <div v-if="upcomingReservations.length === 0" class="empty-timeline">
                  <p>No arrivals scheduled in next 2 hours.</p>
                </div>

                <div v-else class="timeline-cards-list">
                  <article
                    v-for="res in upcomingReservations"
                    :key="res.id"
                    class="timeline-card"
                  >
                    <div class="timeline-time-col">
                      <span class="time-bold">{{ formatTime(res.startsAt) }}</span>
                    </div>

                    <div class="timeline-card-content">
                      <div class="timeline-card-top">
                        <strong class="timeline-guest-name">
                          {{ getCustomerName(res.customer) }}
                        </strong>
                        <span class="party-tag">· {{ res.partySize }} guests</span>
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
                          Seat Table →
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
            </div>
          </div>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 2: FULL FLOOR & TABLES TAB            -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'floor'">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">REAL-TIME FLOOR PLAN</div>
                <h1 class="view-title">Tables & Floor Management</h1>
                <p class="view-subtitle">Live table occupancy, assigned games, guest seating, and floor status.</p>
              </div>
              <button type="button" class="btn-primary-parlor" @click="openWalkInForTable()">
                + Seat Walk-in Customer
              </button>
            </div>

            <div class="floor-tables-grid expanded">
              <article
                v-for="table in branchFloorTables"
                :key="table.id"
                class="floor-table-tile big"
                :class="getTableStatus(table).state.toLowerCase()"
              >
                <div class="tile-top-row">
                  <span class="table-num-lbl">Table {{ table.number }}</span>
                  <span class="table-seats-lbl">{{ table.seats }} Seats · ฿{{ table.rate }}/hr</span>
                </div>

                <!-- FREE TABLE: NO game assignment -->
                <template v-if="getTableStatus(table).state === 'FREE'">
                  <div class="tile-status-row">
                    <span class="state-badge badge-free">FREE</span>
                  </div>
                  <p class="tile-helper">Ready for walk-ins or scheduled bookings. Games can be assigned after seating.</p>
                  <button type="button" class="btn-primary-parlor small full" @click="openWalkInForTable(table)">
                    + Seat Walk-in at Table {{ table.number }}
                  </button>
                </template>

                <!-- OCCUPIED TABLE: Session started -->
                <template v-else-if="getTableStatus(table).state === 'OCCUPIED'">
                  <div class="tile-status-row">
                    <span class="state-badge badge-occupied">IN PLAY</span>
                    <span class="tile-time-pill">{{ getElapsedMinutes(getTableStatus(table).reservation) }} mins played</span>
                  </div>
                  <h3 class="guest-name-large">{{ getCustomerName(getTableStatus(table).reservation?.customer) }}</h3>
                  <p class="tile-sub-info">
                    Party of {{ getTableStatus(table).reservation?.partySize }} · Current Tab: ฿{{ calculateCurrentPlayFee(getTableStatus(table).reservation) }}
                  </p>

                  <!-- Assigned Game Box on Occupied Table -->
                  <div class="tile-assigned-games-box" style="margin: 8px 0;">
                    <div class="assigned-header-row">
                      <span class="assigned-label">Assigned Game:</span>
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
                    <div v-if="getTableAssignedGames(table.id).length > 0" class="assigned-chips-wrap">
                      <span class="game-chip-capsule">
                        <svg class="w-3.5 h-3.5 inline mr-1 text-parlor-warning-700" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                          <rect x="3" y="3" width="18" height="18" rx="3"/>
                          <circle cx="8" cy="8" r="1" fill="currentColor"/>
                          <circle cx="16" cy="16" r="1" fill="currentColor"/>
                          <circle cx="12" cy="12" r="1" fill="currentColor"/>
                        </svg>
                        {{ getTableAssignedGames(table.id)[0] }}
                        <button
                          type="button"
                          class="btn-chip-unassign"
                          title="Return game to shelf"
                          @click.stop="unassignGameFromTable(table.id, getTableAssignedGames(table.id)[0])"
                        >
                          ✕
                        </button>
                      </span>
                    </div>
                    <div v-else class="no-game-text">
                      No game assigned yet · Click "+ Assign Game"
                    </div>
                  </div>

                  <div class="tile-actions-row">
                    <button type="button" class="tile-btn-small" @click="openAddSnacks(getTableStatus(table).reservation)">
                      + Food/Drinks
                    </button>
                    <button type="button" class="tile-btn-gold" @click="openCheckBill(getTableStatus(table).reservation)">
                      Check Bill & Settle
                    </button>
                  </div>
                </template>

                <!-- RESERVED TABLE: NO game assignment -->
                <template v-else-if="getTableStatus(table).state === 'RESERVED'">
                  <div class="tile-status-row">
                    <span class="state-badge badge-reserved">RESERVED</span>
                  </div>
                  <h3 class="guest-name-large">{{ getCustomerName(getTableStatus(table).reservation?.customer) }}</h3>
                  <p class="tile-due-time">
                    Due at {{ formatTime(getTableStatus(table).reservation?.startsAt) }}
                  </p>
                  <button type="button" class="btn-primary-parlor small full" @click="handleCheckIn(getTableStatus(table).reservation)">
                    Check-in & Seat Table →
                  </button>
                </template>
              </article>
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
                <div class="view-eyebrow">IN-PLAY TABLE MONITORING</div>
                <h1 class="view-title">Active Play Sessions & Tabs</h1>
                <p class="view-subtitle">Monitor played time, assigned games, refreshments, and settle bills with automatic late arrival recomputation.</p>
              </div>
              <button type="button" class="btn-primary-parlor" @click="openWalkInForTable()">
                + Start Walk-in Session
              </button>
            </div>

            <div v-if="activeFloorReservations.length === 0" class="empty-state-box">
              <div class="empty-icon">
                <svg class="w-10 h-10 mx-auto text-parlor-warning-700/50" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M18 8h1a4 4 0 0 1 0 8h-1"/>
                  <path d="M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z"/>
                  <line x1="6" y1="1" x2="6" y2="4"/>
                  <line x1="10" y1="1" x2="10" y2="4"/>
                  <line x1="14" y1="1" x2="14" y2="4"/>
                </svg>
              </div>
              <h3>No Active Play Sessions</h3>
              <p>There are no tables currently playing. Seat a walk-in guest or check in a reserved booking.</p>
            </div>

            <div v-else class="sessions-table-wrap">
              <table class="parlor-table">
                <thead>
                  <tr>
                    <th>Table</th>
                    <th>Customer</th>
                    <th>Assigned Game</th>
                    <th>Checked-In</th>
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
                        <div v-if="getTableAssignedGames(res.tableId).length > 0" class="session-chips-wrap">
                          <span class="game-chip-capsule">
                            <svg class="w-3.5 h-3.5 inline mr-1 text-parlor-warning-700" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                              <rect x="3" y="3" width="18" height="18" rx="3"/>
                              <circle cx="8" cy="8" r="1" fill="currentColor"/>
                              <circle cx="16" cy="16" r="1" fill="currentColor"/>
                              <circle cx="12" cy="12" r="1" fill="currentColor"/>
                            </svg>
                            {{ getTableAssignedGames(res.tableId)[0] }}
                            <button
                              type="button"
                              class="btn-chip-unassign"
                              title="Return to shelf"
                              @click.stop="unassignGameFromTable(res.tableId, getTableAssignedGames(res.tableId)[0])"
                            >
                              ✕
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
                      <strong class="fee-text">฿{{ calculateCurrentPlayFee(res) }}</strong>
                    </td>
                    <td>
                      <div class="actions-cell">
                        <button type="button" class="btn-secondary-parlor small" @click="openAddSnacks(res)">
                          + Tab
                        </button>
                        <button type="button" class="btn-primary-parlor small" @click="openCheckBill(res)">
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
                <div class="view-eyebrow">SHIFT SCHEDULE</div>
                <h1 class="view-title">Bookings & Reservations Timeline</h1>
                <p class="view-subtitle">Automatic 15-minute no-show policy releases tables to walk-ins if customers do not arrive.</p>
              </div>
            </div>

            <div class="bookings-list-wrap">
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
                    <td><strong>{{ formatTime(res.startsAt) }}</strong></td>
                    <td>Table {{ res.tableId }}</td>
                    <td>{{ getCustomerName(res.customer) }}</td>
                    <td>{{ res.partySize }} guests</td>
                    <td>
                      <span class="status-tag" :class="res.status.toLowerCase()">
                        {{ res.status }}
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
                <div class="view-eyebrow">GAME COLLECTION & SHELF INVENTORY</div>
                <h1 class="view-title">Board Game Library & Catalog</h1>
                <p class="view-subtitle">Check game availability in real time, see which table is currently playing, and manage catalog titles.</p>
              </div>
              <div class="header-mgmt-actions">
                <button
                  v-if="isAdmin"
                  type="button"
                  class="btn-primary-parlor"
                  @click="openAddGameModal"
                >
                  + Add Board Game
                </button>
                <div class="search-games-input-wrap">
                  <input
                    v-model="gameSearchQuery"
                    type="search"
                    placeholder="Filter games by name, designer, genre..."
                    class="parlor-input"
                  />
                </div>
              </div>
            </div>

            <!-- Game Live Summary Cards -->
            <div class="analytics-ribbon-grid mb-4">
              <div class="analytics-card">
                <span class="analytics-label">TOTAL GAMES</span>
                <div class="analytics-value">{{ catalogGames.length }}</div>
                <span class="analytics-sub">Catalog titles registered</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">AVAILABLE COPIES</span>
                <div class="analytics-value text-parlor-success-700">
                  {{ catalogGames.filter(g => !getGameLocation(g.name)).length }}
                </div>
                <span class="analytics-sub">On shelf & ready to assign</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">COPIES IN USE</span>
                <div class="analytics-value text-parlor-warning-700">
                  {{ activeInUseGamesCount }}
                </div>
                <span class="analytics-sub">Currently playing at tables</span>
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
                All Games ({{ catalogGames.length }})
              </button>
              <button
                type="button"
                class="filter-pill avail"
                :class="{ active: gameStatusFilter === 'AVAILABLE' }"
                @click="gameStatusFilter = 'AVAILABLE'"
              >
                <span class="w-2 h-2 rounded-full bg-parlor-success-500 inline-block mr-1"></span>Available on Shelf ({{ catalogGames.filter(g => !getGameLocation(g.name)).length }})
              </button>
              <button
                type="button"
                class="filter-pill in-use"
                :class="{ active: gameStatusFilter === 'IN_USE' }"
                @click="gameStatusFilter = 'IN_USE'"
              >
                <span class="w-2 h-2 rounded-full bg-parlor-warning-600 inline-block mr-1"></span>In Use at Tables ({{ catalogGames.filter(g => !!getGameLocation(g.name)).length }})
              </button>
            </div>

            <div class="games-library-grid">
              <article v-for="game in filteredGames" :key="game.id" class="game-catalog-card">
                <!-- Game Status Banner (Available vs Unavailable) -->
                <div
                  class="game-status-strip"
                  :class="getGameLocation(game.name) ? 'unavail' : 'avail'"
                >
                  <span class="status-dot"></span>
                  <span v-if="getGameLocation(game.name)" class="status-strip-text">
                    UNAVAILABLE · In use at {{ getGameLocation(game.name) }}
                  </span>
                  <span v-else class="status-strip-text">
                    AVAILABLE · Ready on shelf
                  </span>
                </div>

                <div class="game-card-header">
                  <h3 class="game-name">{{ game.name }}</h3>
                  <span class="game-difficulty-pill">{{ game.difficulty || 'Medium' }}</span>
                </div>
                <p class="game-designer">Designed by {{ game.designer }}</p>
                <div class="game-meta-row">
                  <span class="inline-flex items-center gap-1">
                    <svg class="w-3.5 h-3.5 text-parlor-neutral-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/>
                      <circle cx="9" cy="7" r="4"/>
                      <path d="M22 21v-2a4 4 0 0 0-3-3.87"/>
                      <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                    </svg>
                    {{ game.players[0] }}–{{ game.players[1] }} Players
                  </span>
                  <span class="inline-flex items-center gap-1">
                    <svg class="w-3.5 h-3.5 text-parlor-neutral-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                      <circle cx="12" cy="12" r="10"/>
                      <polyline points="12 6 12 12 16 14"/>
                    </svg>
                    {{ game.playMinutes[0] }}–{{ game.playMinutes[1] }} min
                  </span>
                </div>
                <p class="game-desc">{{ game.description }}</p>

                <div class="game-card-footer">
                  <button
                    v-if="getGameLocation(game.name)"
                    type="button"
                    class="btn-return-shelf full"
                    @click="returnGameFromAnyTable(game.name)"
                  >
                    Return to Shelf (From {{ getGameLocation(game.name) }})
                  </button>
                  <button
                    v-else
                    type="button"
                    class="btn-primary-parlor small full"
                    @click="openAssignGame(game)"
                  >
                    Assign to Playing Table
                  </button>

                  <!-- Admin Management Actions for Game -->
                  <div v-if="isAdmin" class="flex items-center gap-2 mt-2 pt-2 border-t border-parlor-neutral-100">
                    <button type="button" class="btn-xs-secondary flex-1" @click="openEditGameModal(game)">
                      Edit
                    </button>
                    <button type="button" class="btn-xs-danger flex-1" @click="deleteGame(game.id)">
                      Delete
                    </button>
                  </div>
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
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">FOOD & DRINK INVENTORY</div>
                <h1 class="view-title">Food & Drink Stock Management</h1>
                <p class="view-subtitle">
                  <span v-if="isAdmin">Admin Access · Full stock control, menu item creation, profit margins, and supplier batch restocks.</span>
                  <span v-else>Floor Staff Stock Access · Daily pantry counts and stock intake adjustments.</span>
                </p>
              </div>

              <!-- Higher Role Action: Add New Food/Drink Type -->
              <div class="header-mgmt-actions">
                <button
                  v-if="isManager"
                  type="button"
                  class="btn-primary-parlor"
                  @click="openAddFoodModal"
                >
                  + Add Food & Drink Item
                </button>
                <button
                  type="button"
                  class="btn-secondary-parlor"
                  @click="openRestockModal(branchStockItems[0])"
                >
                  + Restock Delivery Batch
                </button>
              </div>
            </div>

            <!-- Stock Metrics Ribbon -->
            <div class="stock-stats-ribbon">
              <div class="stock-stat-box">
                <span class="stat-lbl">TOTAL UNITS IN STOCK</span>
                <div class="stat-num">{{ totalStockUnits }}</div>
                <span class="stat-sub">{{ branchStockItems.length }} tracked menu items</span>
              </div>
              <div class="stock-stat-box">
                <span class="stat-lbl">HEALTHY STOCK</span>
                <div class="stat-num text-green">{{ healthyStockCount }}</div>
                <span class="stat-sub">Sufficient pantry levels</span>
              </div>
              <div class="stock-stat-box">
                <span class="stat-lbl">LOW STOCK ALERT</span>
                <div class="stat-num text-amber">{{ lowStockCount }}</div>
                <span class="stat-sub">Below threshold, reorder soon</span>
              </div>
              <div class="stock-stat-box">
                <span class="stat-lbl">OUT OF STOCK</span>
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
                  Beverages
                </button>
                <button
                  type="button"
                  class="pill-tab"
                  :class="{ active: stockCategoryFilter === 'snacks' }"
                  @click="stockCategoryFilter = 'snacks'"
                >
                  Snacks & Fries
                </button>
                <button
                  type="button"
                  class="pill-tab"
                  :class="{ active: stockCategoryFilter === 'tea_coffee' }"
                  @click="stockCategoryFilter = 'tea_coffee'"
                >
                  Tea & Coffee
                </button>
                <button
                  type="button"
                  class="pill-tab"
                  :class="{ active: stockCategoryFilter === 'bakery_desserts' }"
                  @click="stockCategoryFilter = 'bakery_desserts'"
                >
                  Bakery & Desserts
                </button>
              </div>

              <div class="filter-group">
                <span class="filter-lbl">Alerts:</span>
                <button
                  type="button"
                  class="pill-tab alert"
                  :class="{ active: stockStatusFilter === 'NEED_REORDER' }"
                  @click="stockStatusFilter = stockStatusFilter === 'NEED_REORDER' ? 'ALL' : 'NEED_REORDER'"
                >
                  <span class="inline-flex items-center gap-1">
                    <svg class="w-3.5 h-3.5 text-current" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                      <line x1="12" y1="9" x2="12" y2="13"/>
                      <line x1="12" y1="17" x2="12.01" y2="17"/>
                    </svg>
                    Needs Reorder ({{ lowStockCount + outOfStockCount }})
                  </span>
                </button>
              </div>
            </div>

            <!-- Stock Table -->
            <div class="inventory-table-wrap">
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
                      </div>
                    </td>
                    <td>
                      <span class="category-tag">{{ item.category.replace('_', ' ') }}</span>
                    </td>
                    <td>
                      <div class="stock-qty-badge" :class="{ empty: item.currentStock === 0, low: item.currentStock <= item.lowStockThreshold && item.currentStock > 0 }">
                        <strong class="qty-big">{{ item.currentStock }}</strong>
                        <span class="qty-unit">{{ item.unit }}</span>
                      </div>
                    </td>
                    <td>
                      <span
                        v-if="item.currentStock === 0"
                        class="stock-status-tag out"
                      >
                        ● Out of Stock
                      </span>
                      <span
                        v-else-if="item.currentStock <= item.lowStockThreshold"
                        class="stock-status-tag low"
                      >
                        ● Low Stock (&le;{{ item.lowStockThreshold }})
                      </span>
                      <span
                        v-else
                        class="stock-status-tag healthy"
                      >
                        ● In Stock
                      </span>
                    </td>
                    <td>
                      <strong class="price-val">฿{{ item.price }}</strong>
                    </td>
                    <td v-if="isManager">
                      <div class="margin-cell">
                        <span>Cost: ฿{{ item.costPrice }}</span>
                        <strong class="margin-pill">
                          {{ Math.round(((item.price - item.costPrice) / item.price) * 100) }}% margin
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
                        <span class="step-val small">{{ item.currentStock }}</span>
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
                        Remove
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW: BUSINESS OVERVIEW & MANAGE BRANCHES  -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'branches' && isAdmin">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">BRANCH FLEET OVERVIEW</div>
                <h1 class="view-title">Business Overview & Branches</h1>
                <p class="view-subtitle">Monitor live operations, manage branch locations, and update operating hours across all properties.</p>
              </div>
              <div class="header-mgmt-actions">
                <button type="button" class="btn-primary-parlor" @click="openAddBranchModal">
                  + Add Branch
                </button>
              </div>
            </div>

            <!-- Top Level Multi-Branch Summary Cards -->
            <div class="analytics-ribbon-grid">
              <div class="analytics-card">
                <span class="analytics-label">TOTAL BRANCHES</span>
                <div class="analytics-value">{{ overviewTotalBranches }}</div>
                <span class="analytics-sub">Locations in system</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">TOTAL FLEET TABLES</span>
                <div class="analytics-value">{{ overviewTotalTables }}</div>
                <span class="analytics-sub">Seating across all branches</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">TODAY'S CHECK-INS</span>
                <div class="analytics-value">{{ overviewTotalCheckIns }}</div>
                <span class="analytics-sub">Total parties served today</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">ACTIVE SESSIONS</span>
                <div class="analytics-value text-parlor-success-700">{{ overviewTotalActiveReservations }}</div>
                <span class="analytics-sub">Live tables currently in play</span>
              </div>
            </div>

            <!-- Filter & Search Controls -->
            <div class="table-controls-bar">
              <div class="search-input-wrap">
                <input
                  v-model="branchSearchQuery"
                  type="search"
                  placeholder="Filter branch by name, code, or city..."
                  class="parlor-input"
                />
              </div>
              <div class="filter-controls-group">
                <button
                  type="button"
                  class="filter-pill"
                  :class="{ active: branchStatusFilter === 'All' }"
                  @click="branchStatusFilter = 'All'"
                >
                  All ({{ branchesList.length }})
                </button>
                <button
                  type="button"
                  class="filter-pill avail"
                  :class="{ active: branchStatusFilter === 'Active' }"
                  @click="branchStatusFilter = 'Active'"
                >
                  Active ({{ branchesList.filter(b => b.status === 'Active').length }})
                </button>
                <button
                  type="button"
                  class="filter-pill in-use"
                  :class="{ active: branchStatusFilter === 'Inactive' }"
                  @click="branchStatusFilter = 'Inactive'"
                >
                  Inactive ({{ branchesList.filter(b => b.status === 'Inactive').length }})
                </button>
              </div>
            </div>

            <!-- Branches List Table -->
            <div class="bookings-list-wrap">
              <table class="parlor-table">
                <thead>
                  <tr>
                    <th>Branch Details</th>
                    <th>Status</th>
                    <th>Tables Summary</th>
                    <th>Today's Check-ins</th>
                    <th>Operating Hours</th>
                    <th>Contact</th>
                    <th class="text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="branch in filteredBranches" :key="branch.id">
                    <td>
                      <div class="font-bold text-parlor-neutral-800">{{ branch.name }}</div>
                      <div class="text-xs text-parlor-neutral-600 font-mono">{{ branch.code }} · {{ branch.address }}, {{ branch.city }}</div>
                    </td>
                    <td>
                      <span
                        class="px-2.5 py-1 rounded-full text-xs font-semibold uppercase tracking-wider"
                        :class="branch.status === 'Active' ? 'bg-parlor-success-100 text-parlor-success-700 border border-parlor-success-300' : 'bg-parlor-neutral-100 text-parlor-neutral-600 border border-parlor-neutral-300'"
                      >
                        {{ branch.status }}
                      </span>
                    </td>
                    <td>
                      <div class="text-sm font-semibold">{{ branch.totalTables }} Total Tables</div>
                      <div class="text-xs text-parlor-neutral-600">
                        <span class="text-parlor-success-700 font-medium">{{ branch.availableTables }} free</span> ·
                        <span class="text-parlor-warning-700 font-medium">{{ branch.occupiedTables }} playing</span> ·
                        <span>{{ branch.reservedTables }} booked</span>
                      </div>
                    </td>
                    <td>
                      <div class="text-sm font-semibold text-parlor-neutral-800">{{ branch.checkInsToday }} parties</div>
                    </td>
                    <td class="text-xs">
                      <div class="font-medium text-parlor-neutral-800"><strong>Mon–Fri:</strong> {{ branch.openingHours.monFri }}</div>
                      <div class="text-parlor-neutral-600 mt-0.5"><strong>Sat:</strong> {{ branch.openingHours.sat }}</div>
                      <div class="text-parlor-neutral-600 mt-0.5"><strong>Sun:</strong> {{ branch.openingHours.sun }}</div>
                    </td>
                    <td>
                      <div class="text-xs text-parlor-neutral-800">{{ branch.phone }}</div>
                      <div class="text-xs text-parlor-neutral-600">{{ branch.email }}</div>
                    </td>
                    <td class="text-right">
                      <div class="flex items-center justify-end gap-2">
                        <button
                          type="button"
                          class="btn-xs-secondary"
                          @click="openEditBranchModal(branch)"
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          class="btn-xs-secondary"
                          @click="toggleBranchStatus(branch)"
                        >
                          {{ branch.status === 'Active' ? 'Deactivate' : 'Activate' }}
                        </button>
                        <button
                          type="button"
                          class="btn-xs-danger"
                          @click="deleteBranch(branch.id)"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="filteredBranches.length === 0">
                    <td colspan="7" class="text-center py-8 text-parlor-neutral-600">
                      No branch locations found matching your search criteria.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW: MANAGE PHYSICAL TABLES               -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'manage-tables' && isAdmin">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">PHYSICAL TABLE INVENTORY</div>
                <h1 class="view-title">Manage Tables & Floor Seating</h1>
                <p class="view-subtitle">View, create, edit, and configure physical table inventory across branches.</p>
              </div>
              <div class="header-mgmt-actions">
                <button type="button" class="btn-primary-parlor" @click="openAddTableModal">
                  + Add Table
                </button>
              </div>
            </div>

            <!-- Branch & Live Table Status Counters -->
            <div class="analytics-ribbon-grid">
              <div class="analytics-card">
                <span class="analytics-label">TOTAL TABLES</span>
                <div class="analytics-value">{{ tableCountTotal }}</div>
                <span class="analytics-sub">Registered floor units</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">AVAILABLE</span>
                <div class="analytics-value text-parlor-success-700">{{ tableCountAvailable }}</div>
                <span class="analytics-sub">Ready for walk-in / booking</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">RESERVED</span>
                <div class="analytics-value text-parlor-info-700">{{ tableCountReserved }}</div>
                <span class="analytics-sub">Scheduled for guests</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">OCCUPIED</span>
                <div class="analytics-value text-parlor-warning-700">{{ tableCountOccupied }}</div>
                <span class="analytics-sub">Currently playing</span>
              </div>
            </div>

            <!-- Controls: Branch Dropdown, Status Filter, Search -->
            <div class="table-controls-bar">
              <div class="flex items-center gap-2 flex-wrap">
                <select v-model="tableFilterBranch" class="parlor-select">
                  <option value="All branches">All branches</option>
                  <option v-for="b in branchesList" :key="b.id" :value="b.name">{{ b.name }}</option>
                </select>

                <select v-model="tableFilterStatus" class="parlor-select">
                  <option value="All statuses">All statuses</option>
                  <option value="Available">Available (Green)</option>
                  <option value="Reserved">Reserved (Blue)</option>
                  <option value="Occupied">Occupied (Amber)</option>
                  <option value="Unavailable">Unavailable (Gray)</option>
                </select>
              </div>

              <div class="search-input-wrap">
                <input
                  v-model="tableSearchQuery"
                  type="search"
                  placeholder="Search table name or ID..."
                  class="parlor-input"
                />
              </div>
            </div>

            <!-- Table Inventory List -->
            <div class="bookings-list-wrap">
              <table class="parlor-table">
                <thead>
                  <tr>
                    <th>Table Name</th>
                    <th>Branch</th>
                    <th>Capacity</th>
                    <th>Shape</th>
                    <th>Status</th>
                    <th>Last Updated</th>
                    <th class="text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="tbl in filteredPhysicalTables" :key="tbl.id">
                    <td>
                      <div class="font-bold text-parlor-neutral-800">{{ tbl.name }}</div>
                      <div class="text-xs text-parlor-neutral-600 font-mono">{{ tbl.id }}</div>
                    </td>
                    <td>
                      <span class="text-sm text-parlor-neutral-800">{{ tbl.branchName }}</span>
                    </td>
                    <td>
                      <span class="font-semibold text-parlor-neutral-800">{{ tbl.capacity }} Seats</span>
                    </td>
                    <td>
                      <span class="px-2 py-0.5 rounded text-xs bg-parlor-neutral-100 border border-parlor-neutral-300 font-medium">
                        {{ tbl.shape }}
                      </span>
                    </td>
                    <td>
                      <span
                        class="px-2.5 py-1 rounded-full text-xs font-semibold uppercase tracking-wider"
                        :class="{
                          'bg-parlor-success-100 text-parlor-success-700 border border-parlor-success-300': tbl.status === 'Available',
                          'bg-parlor-info-100 text-parlor-info-700 border border-parlor-info-300': tbl.status === 'Reserved',
                          'bg-parlor-warning-100 text-parlor-warning-700 border border-parlor-warning-300': tbl.status === 'Occupied',
                          'bg-parlor-neutral-150 text-parlor-neutral-800 border border-parlor-neutral-300': tbl.status === 'Unavailable'
                        }"
                      >
                        {{ tbl.status }}
                      </span>
                    </td>
                    <td>
                      <span class="text-xs text-parlor-neutral-600">{{ tbl.lastUpdated }}</span>
                    </td>
                    <td class="text-right">
                      <div class="flex items-center justify-end gap-2">
                        <button type="button" class="btn-xs-secondary" @click="openEditTableModal(tbl)">Edit</button>
                        <button type="button" class="btn-xs-danger" @click="deleteTable(tbl.id)">Delete</button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="filteredPhysicalTables.length === 0">
                    <td colspan="7" class="text-center py-8 text-parlor-neutral-600">
                      No tables found matching your search/filter criteria.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 7: STAFF & SHIFTS (Admin)             -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'staff-roster' && isAdmin">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">BRANCH ROSTER & TEAM</div>
                <h1 class="view-title">Staff & Shift Attendance</h1>
                <p class="view-subtitle">Active shift supervision, station assignments, and shift scheduling.</p>
              </div>
              <div class="header-mgmt-actions">
                <button type="button" class="btn-primary-parlor" @click="openAddStaffModal">
                  + Add Staff Member
                </button>
              </div>
            </div>

            <div class="staff-roster-grid">
              <article v-for="staff in branchStaffRoster" :key="staff.id" class="staff-member-card">
                <div class="staff-card-header">
                  <div class="staff-avatar-circle">
                    {{ staff.name.slice(0, 2).toUpperCase() }}
                  </div>
                  <div class="flex-1">
                    <h3 class="staff-name">{{ staff.name }}</h3>
                    <span class="staff-role-badge">{{ staff.role }}</span>
                  </div>
                  <div class="flex items-center gap-1">
                    <button type="button" class="btn-xs-secondary" @click="openEditStaffModal(staff)">Edit</button>
                    <button type="button" class="btn-xs-danger" @click="deleteStaff(staff.id)">✕</button>
                  </div>
                </div>
                <div class="staff-card-body">
                  <div class="mb-2">
                    <label class="block text-xs font-semibold text-parlor-neutral-800 mb-0.5">Assigned Shift:</label>
                    <select v-model="staff.shift" class="parlor-select text-xs w-full py-1">
                      <option v-for="s in shiftOptions" :key="s" :value="s">{{ s }}</option>
                    </select>
                  </div>
                  <p class="text-xs text-parlor-neutral-600 mb-2"><strong>Station / Area:</strong> {{ staff.assignedZone }}</p>
                  <div class="flex items-center justify-between pt-2 border-t border-parlor-neutral-100">
                    <div class="duty-status-row !mt-0">
                      <span :class="staff.status === 'ON_DUTY' ? 'pulse-green' : staff.status === 'ON_BREAK' ? 'w-2 h-2 rounded-full bg-parlor-warning-600' : 'w-2 h-2 rounded-full bg-parlor-neutral-400'"></span>
                      <span class="duty-lbl" :class="staff.status === 'ON_DUTY' ? 'text-parlor-success-700' : staff.status === 'ON_BREAK' ? 'text-parlor-warning-700' : 'text-parlor-neutral-600'">
                        {{ staff.status === 'ON_DUTY' ? 'On Duty' : staff.status === 'ON_BREAK' ? 'On Break' : 'Off Duty' }}
                      </span>
                    </div>
                    <select v-model="staff.status" class="text-xs border border-parlor-neutral-300 rounded px-1.5 py-0.5 bg-white text-parlor-neutral-800">
                      <option value="ON_DUTY">On Duty</option>
                      <option value="ON_BREAK">On Break</option>
                      <option value="OFF_DUTY">Off Duty</option>
                    </select>
                  </div>
                </div>
              </article>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 8: REVENUE & ANALYTICS (Admin)        -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'analytics' && isAdmin">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">FINANCIAL & OCCUPANCY ANALYTICS</div>
                <h1 class="view-title">Revenue & Branch Analytics</h1>
                <p class="view-subtitle">Today's real-time financial gross, hourly table time, and refreshments sales.</p>
              </div>
            </div>

            <div class="analytics-ribbon-grid">
              <div class="analytics-card">
                <span class="analytics-label">TODAY'S GROSS REVENUE</span>
                <div class="analytics-value">฿{{ todayTotalGross }}</div>
                <span class="analytics-sub">Table time + Counter refreshments</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">TABLE PLAYTIME FEES</span>
                <div class="analytics-value">฿{{ totalTableFeesToday }}</div>
                <span class="analytics-sub">Based on actual elapsed playtime</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">F&B REFRESHMENTS</span>
                <div class="analytics-value">฿{{ todayFoodDrinkRevenue }}</div>
                <span class="analytics-sub">Snacks & drinks tabs</span>
              </div>
              <div class="analytics-card">
                <span class="analytics-label">PEAK UTILIZATION</span>
                <div class="analytics-value">{{ Math.round((occupiedTablesCount / branchFloorTables.length) * 100) }}%</div>
                <span class="analytics-sub">{{ occupiedTablesCount }} of {{ branchFloorTables.length }} tables playing</span>
              </div>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 9: VENUE SETTINGS (Admin)             -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'settings' && isAdmin">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">SYSTEM CONFIGURATION</div>
                <h1 class="view-title">Venue Settings</h1>
                <p class="view-subtitle">Manage branch identity, operating hours, and capacity limits.</p>
              </div>
            </div>

            <div class="settings-form-grid">
              <div class="form-group">
                <label class="parlor-label">Branch Name:</label>
                <input v-model="venueSettings.branchName" type="text" class="parlor-input" />
              </div>
              <div class="form-group">
                <label class="parlor-label">Physical Address:</label>
                <input v-model="venueSettings.address" type="text" class="parlor-input" />
              </div>
              <div class="form-row-2">
                <div class="form-group">
                  <label class="parlor-label">Opening Time:</label>
                  <input v-model="venueSettings.openTime" type="time" class="parlor-input" />
                </div>
                <div class="form-group">
                  <label class="parlor-label">Closing Time:</label>
                  <input v-model="venueSettings.closeTime" type="time" class="parlor-input" />
                </div>
              </div>
            </div>
          </section>
        </template>

        <!-- ========================================== -->
        <!-- VIEW 10: VENUE POLICIES (Admin)            -->
        <!-- ========================================== -->
        <template v-else-if="activeTab === 'policies' && isAdmin">
          <section class="console-block">
            <div class="view-header-row">
              <div>
                <div class="view-eyebrow">OPERATIONAL GOVERNANCE & RULES</div>
                <h1 class="view-title">Venue Operational Policies</h1>
                <p class="view-subtitle">Manage venue operational rules, grace periods, play calculation, and table rate structures.</p>
              </div>
              <div class="header-mgmt-actions">
                <button type="button" class="btn-primary-parlor" @click="openAddPolicyModal">
                  + Add Policy
                </button>
              </div>
            </div>

            <div class="policies-cards-list">
              <article v-for="policy in venuePoliciesList" :key="policy.id" class="policy-card">
                <div class="policy-card-top">
                  <div class="flex-1">
                    <div class="flex items-center gap-2 mb-1.5 flex-wrap">
                      <h3 class="policy-card-title !mb-0">{{ policy.title }}</h3>
                      <span class="category-tag">{{ policy.category }}</span>
                      <span v-if="policy.metric" class="px-2 py-0.5 rounded text-xs bg-parlor-warning-100 text-parlor-warning-700 border border-parlor-warning-300 font-semibold font-mono">
                        {{ policy.metric }}
                      </span>
                    </div>
                    <p class="policy-card-desc">
                      {{ policy.description }}
                    </p>
                  </div>
                  <div class="flex items-center gap-2 shrink-0">
                    <button
                      type="button"
                      class="px-2.5 py-1 rounded-full text-xs font-semibold cursor-pointer border transition-colors"
                      :class="policy.active ? 'bg-parlor-success-100 text-parlor-success-700 border-parlor-success-300' : 'bg-parlor-neutral-100 text-parlor-neutral-600 border-parlor-neutral-300'"
                      @click="togglePolicyActive(policy)"
                    >
                      {{ policy.active ? 'Active & Enforced' : 'Inactive' }}
                    </button>
                    <button type="button" class="btn-xs-secondary" @click="openEditPolicyModal(policy)">
                      Edit
                    </button>
                    <button type="button" class="btn-xs-danger" @click="deletePolicy(policy.id)">
                      Delete
                    </button>
                  </div>
                </div>
              </article>
              <div v-if="venuePoliciesList.length === 0" class="text-center py-8 text-parlor-neutral-600 bg-parlor-page rounded-xl border border-dashed border-parlor-neutral-300">
                No venue operational policies configured. Click "+ Add Policy" to create one.
              </div>
            </div>
          </section>
        </template>
      </main>

      <!-- Assign Game to Table Modal (Enforces: 1. Only started session can receive game; 2. Cannot assign to already assigned table) -->
      <div v-if="showAssignGameModal" class="modal-backdrop" @click.self="showAssignGameModal = false">
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">TABLE GAME SERVICE</div>
              <h3 class="modal-title">Assign Game to Table</h3>
              <p class="modal-sub">
                Rule: Only tables with an active started session can receive a game, and each table can only hold one game at a time.
              </p>
            </div>
            <button type="button" class="close-x" @click="showAssignGameModal = false">✕</button>
          </div>

          <div class="modal-form-body">
            <!-- Alert if no tables are currently eligible -->
            <div v-if="eligibleTablesForGame.length === 0" class="rule-warning-box">
              <span class="gold-bullet inline-flex items-center">
                <svg class="w-4 h-4 text-parlor-warning-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                  <line x1="12" y1="9" x2="12" y2="13"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
              </span>
              <span>
                No tables eligible right now. Games can only be assigned to tables with an <strong>active started session</strong> that do not already have an assigned game.
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
                    disabled: getTableStatus(t).state !== 'OCCUPIED' || getTableAssignedGames(t.id).length > 0
                  }"
                  :disabled="getTableStatus(t).state !== 'OCCUPIED' || getTableAssignedGames(t.id).length > 0"
                  @click="assignGameTargetTableId = t.id"
                >
                  <strong>Table {{ t.number }}</strong>
                  <span v-if="getTableStatus(t).state !== 'OCCUPIED'" class="pill-reason">
                    (Not Playing)
                  </span>
                  <span v-else-if="getTableAssignedGames(t.id).length > 0" class="pill-reason has-game">
                    (Has {{ getTableAssignedGames(t.id)[0] }})
                  </span>
                  <span v-else class="pill-reason ready">
                    (Ready for game)
                  </span>
                </button>
              </div>
            </div>

            <!-- 2. Choose Game from Library -->
            <div class="form-section" style="margin-top: 14px;">
              <label class="modal-label">Choose Game to Deliver:</label>
              <div class="search-games-input-wrap" style="margin-bottom: 8px;">
                <input
                  v-model="assignModalSearch"
                  type="search"
                  placeholder="Filter available games..."
                  class="parlor-input"
                  style="width: 100%; font-size: 12px; padding: 6px 10px;"
                />
              </div>

              <div class="modal-games-list">
                <div
                  v-for="g in assignableGamesList"
                  :key="g.id"
                  class="modal-game-choice-row"
                  :class="{
                    selected: selectedGameToAssign?.id === g.id,
                    'in-use': !!getGameLocation(g.name)
                  }"
                  @click="!getGameLocation(g.name) && (selectedGameToAssign = g)"
                >
                  <div class="game-choice-info">
                    <strong class="game-choice-name">{{ g.name }}</strong>
                    <span class="game-choice-sub">
                      {{ g.players[0] }}–{{ g.players[1] }} players · {{ g.playMinutes[0] }}–{{ g.playMinutes[1] }} min
                    </span>
                  </div>
                  <div class="game-choice-status">
                    <span
                      class="mini-status-tag"
                      :class="getGameLocation(g.name) ? 'unavail' : 'avail'"
                    >
                      {{ getGameLocation(g.name) ? `In use (${getGameLocation(g.name)})` : 'Available on shelf' }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn-secondary-parlor" @click="showAssignGameModal = false">
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="!canConfirmAssign"
              @click="confirmAssignGame"
            >
              Assign {{ selectedGameToAssign?.name || 'Game' }} to Table {{ assignGameTargetTableId }} →
            </button>
          </div>
        </div>
      </div>

      <!-- Higher Role Modal: Add New Food / Drink Type (Manager & Admin only) -->
      <div v-if="showAddFoodModal && isManager" class="modal-backdrop" @click.self="showAddFoodModal = false">
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">ADMIN CATALOG CONFIGURATION</div>
              <h3 class="modal-title">Add New Food / Drink Item</h3>
              <p class="modal-sub">
                Define a new refreshment item, retail price, cost margins, and initial stock.
              </p>
            </div>
            <button type="button" class="close-x" @click="showAddFoodModal = false">✕</button>
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
            <button type="button" class="btn-secondary-parlor" @click="showAddFoodModal = false">
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="!newFoodForm.name.trim()"
              @click="submitAddNewFood"
            >
              Add Item to Menu & Stock →
            </button>
          </div>
        </div>
      </div>

      <!-- Batch Restock Modal -->
      <div v-if="showRestockModal && restockTargetItem" class="modal-backdrop" @click.self="showRestockModal = false">
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">PANTRY INTAKE</div>
              <h3 class="modal-title">Restock Inventory Batch</h3>
              <p class="modal-sub">
                Add incoming shipment to <strong>{{ restockTargetItem.name }}</strong>.
              </p>
            </div>
            <button type="button" class="close-x" @click="showRestockModal = false">✕</button>
          </div>

          <div class="modal-form-body">
            <div class="stock-current-preview">
              <span>Current Stock on Hand:</span>
              <strong>{{ restockTargetItem.currentStock }} {{ restockTargetItem.unit }}</strong>
            </div>

            <div class="form-group" style="margin-top: 14px;">
              <label class="modal-label">Quantity Received (+{{ restockTargetItem.unit }}):</label>
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
                  style="width: 100px; text-align: center; font-weight: 600;"
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
              <strong class="text-green">{{ restockTargetItem.currentStock + restockBatchQty }} {{ restockTargetItem.unit }}</strong>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn-secondary-parlor" @click="showRestockModal = false">
              Cancel
            </button>
            <button type="button" class="btn-primary-parlor" @click="submitRestock">
              Confirm Intake (+{{ restockBatchQty }} {{ restockTargetItem.unit }}) →
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
        @settled="staffBillReservation = null; queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] })"
      />

      <!-- Cancel Confirmation Modal -->
      <div v-if="showCancelModal" class="modal-backdrop" @click.self="showCancelModal = false">
        <div class="modal-card">
          <h3 class="modal-title">Cancel Reservation</h3>
          <p class="modal-sub">
            Cancel reservation for <strong>{{ getCustomerName(cancelTargetRes?.customer) }}</strong> at Table {{ cancelTargetRes?.tableId }}.
            Table will be made available immediately.
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
            <button type="button" class="btn-secondary-parlor" @click="showCancelModal = false">
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
      <div v-if="showWalkInModal" class="modal-backdrop" @click.self="showWalkInModal = false">
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">FAST COUNTER SEATING</div>
              <h3 class="modal-title">Seat Walk-in Guests</h3>
              <p class="modal-sub">
                Select table and seat immediately. Refreshments can be added on table tab anytime.
              </p>
            </div>
            <button type="button" class="close-x" @click="showWalkInModal = false">✕</button>
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
                    occupied: getTableStatus(tbl).state === 'OCCUPIED'
                  }"
                  @click="walkInTableId = tbl.id; walkInPartySize = tbl.seats"
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
            <button type="button" class="btn-secondary-parlor" @click="showWalkInModal = false">
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="staffCreateMutation.isPending.value"
              @click="handleStaffWalkInSubmit"
            >
              <span v-if="staffCreateMutation.isPending.value">Seating Table…</span>
              <span v-else>Seat & Start Playing Now →</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Quick Add Snacks Modal -->
      <div v-if="staffSnackReservation" class="modal-backdrop" @click.self="staffSnackReservation = null">
        <div class="modal-card snack-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">TABLE TAB SERVICE</div>
              <h3 class="modal-title">Add Refreshments</h3>
              <p class="modal-sub">
                Table {{ staffSnackReservation.tableId }} · {{ getCustomerName(staffSnackReservation.customer) }}
              </p>
            </div>
            <button type="button" class="close-x" @click="staffSnackReservation = null">✕</button>
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
                  <span :class="item.currentStock > 0 ? 'stock-ok' : 'stock-out'">
                    {{ item.currentStock > 0 ? `${item.currentStock} in stock` : 'Out of stock' }}
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
                <span class="step-val small">{{ getStaffSnackQty(item.id) }}</span>
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
              <button type="button" class="btn-secondary-parlor" @click="staffSnackReservation = null">
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

      <!-- Add/Edit Branch Modal -->
      <div v-if="showAddBranchModal && isAdmin" class="modal-backdrop" @click.self="showAddBranchModal = false">
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">ADMINISTRATION · BRANCH DIRECTORY</div>
              <h3 class="modal-title">{{ editingBranchId ? 'Edit Branch Location' : 'Add New Branch Location' }}</h3>
              <p class="modal-sub">Configure branch code, operating hours, and floor table capacity.</p>
            </div>
            <button type="button" class="close-x" @click="showAddBranchModal = false">✕</button>
          </div>

          <div class="modal-body">
            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Branch Name *</label>
                <input v-model="branchForm.name" type="text" class="parlor-input" placeholder="e.g. The Parlor · Silom" />
              </div>
              <div class="form-group">
                <label class="field-label">Branch Code *</label>
                <input v-model="branchForm.code" type="text" class="parlor-input font-mono" placeholder="e.g. BKK-SL3" />
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Address Line *</label>
                <input v-model="branchForm.address" type="text" class="parlor-input" placeholder="e.g. 123 Silom Rd" />
              </div>
              <div class="form-group">
                <label class="field-label">City *</label>
                <input v-model="branchForm.city" type="text" class="parlor-input" placeholder="Bangkok" />
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Contact Phone *</label>
                <input v-model="branchForm.phone" type="tel" class="parlor-input" placeholder="+66 2 123 4567" />
              </div>
              <div class="form-group">
                <label class="field-label">Email</label>
                <input v-model="branchForm.email" type="email" class="parlor-input" placeholder="branch@theparlor.com" />
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Total Tables *</label>
                <input v-model.number="branchForm.totalTables" type="number" min="1" max="100" class="parlor-input" />
              </div>
              <div class="form-group">
                <label class="field-label">Status *</label>
                <select v-model="branchForm.status" class="parlor-select">
                  <option value="Active">Active</option>
                  <option value="Inactive">Inactive</option>
                </select>
              </div>
            </div>

            <div class="form-group mt-3">
              <label class="field-label font-bold text-parlor-neutral-800">Daily Operating Hours</label>
              <div class="operating-hours-editor mt-1">
                <div class="day-hours-row">
                  <span class="day-label">Monday – Friday</span>
                  <div class="time-range-inputs">
                    <input v-model="branchForm.monFriOpen" type="time" class="time-field" />
                    <span class="time-sep">to</span>
                    <input v-model="branchForm.monFriClose" type="time" class="time-field" />
                  </div>
                </div>
                <div class="day-hours-row">
                  <span class="day-label">Saturday</span>
                  <div class="time-range-inputs">
                    <input v-model="branchForm.satOpen" type="time" class="time-field" />
                    <span class="time-sep">to</span>
                    <input v-model="branchForm.satClose" type="time" class="time-field" />
                  </div>
                </div>
                <div class="day-hours-row">
                  <span class="day-label">Sunday</span>
                  <div class="time-range-inputs">
                    <input v-model="branchForm.sunOpen" type="time" class="time-field" />
                    <span class="time-sep">to</span>
                    <input v-model="branchForm.sunClose" type="time" class="time-field" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn-secondary-parlor" @click="showAddBranchModal = false">
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="!branchForm.name.trim() || !branchForm.code.trim()"
              @click="saveBranch"
            >
              {{ editingBranchId ? 'Update Branch Location' : 'Create Branch Location' }} →
            </button>
          </div>
        </div>
      </div>

      <!-- Add/Edit Table Modal -->
      <div v-if="showAddTableModal && isAdmin" class="modal-backdrop" @click.self="showAddTableModal = false">
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">ADMINISTRATION · FLOOR INVENTORY</div>
              <h3 class="modal-title">{{ editingTableId ? 'Edit Table Configuration' : 'Add New Physical Table' }}</h3>
              <p class="modal-sub">Define seating capacity, shape, and active status.</p>
            </div>
            <button type="button" class="close-x" @click="showAddTableModal = false">✕</button>
          </div>

          <div class="modal-body">
            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Table Name *</label>
                <input v-model="tableForm.name" type="text" class="parlor-input" placeholder="e.g. Table 12" />
              </div>
              <div class="form-group">
                <label class="field-label">Branch Assignment *</label>
                <select v-model="tableForm.branchName" class="parlor-select">
                  <option v-for="b in branchesList" :key="b.id" :value="b.name">{{ b.name }}</option>
                </select>
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Capacity (Seats) *</label>
                <input v-model.number="tableForm.capacity" type="number" min="1" max="50" class="parlor-input" />
              </div>
              <div class="form-group">
                <label class="field-label">Table Shape *</label>
                <select v-model="tableForm.shape" class="parlor-select">
                  <option value="Square">Square</option>
                  <option value="Round">Round</option>
                  <option value="Rectangle">Rectangle</option>
                  <option value="Oval">Oval</option>
                </select>
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Initial Status *</label>
                <select v-model="tableForm.status" class="parlor-select">
                  <option value="Available">Available (Green)</option>
                  <option value="Reserved">Reserved (Blue)</option>
                  <option value="Occupied">Occupied (Amber)</option>
                  <option value="Unavailable">Unavailable (Gray/Maintenance)</option>
                </select>
              </div>
              <div class="form-group">
                <label class="field-label">Active on Floor</label>
                <select v-model="tableForm.active" class="parlor-select">
                  <option :value="true">Yes — Available for floor operations</option>
                  <option :value="false">No — Hidden/inactive</option>
                </select>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn-secondary-parlor" @click="showAddTableModal = false">
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="!tableForm.name.trim()"
              @click="saveTable"
            >
              {{ editingTableId ? 'Save Table Changes' : 'Add Table to Inventory' }} →
            </button>
          </div>
        </div>
      </div>

      <!-- Add/Edit Game Modal -->
      <div v-if="showAddGameModal && isAdmin" class="modal-backdrop" @click.self="showAddGameModal = false">
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">ADMINISTRATION · CATALOG CONFIGURATION</div>
              <h3 class="modal-title">{{ editingGameId ? 'Edit Board Game' : 'Add New Board Game' }}</h3>
              <p class="modal-sub">Add a new board game title and specs to the parlor shelf catalog.</p>
            </div>
            <button type="button" class="close-x" @click="showAddGameModal = false">✕</button>
          </div>

          <div class="modal-body">
            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Game Title *</label>
                <input v-model="gameForm.title" type="text" class="parlor-input" placeholder="e.g. Catan, Wingspan" />
              </div>
              <div class="form-group">
                <label class="field-label">Designer / Studio</label>
                <input v-model="gameForm.designer" type="text" class="parlor-input" placeholder="e.g. Klaus Teuber" />
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Categories (comma-separated) *</label>
                <input v-model="gameForm.categories" type="text" class="parlor-input" placeholder="e.g. Strategy, Trading" />
              </div>
              <div class="form-group">
                <label class="field-label">Complexity *</label>
                <select v-model="gameForm.complexity" class="parlor-select">
                  <option value="Light">Light (Beginner friendly)</option>
                  <option value="Medium">Medium (Casual strategy)</option>
                  <option value="Heavy">Heavy (Expert strategic)</option>
                </select>
              </div>
            </div>

            <div class="form-row-3">
              <div class="form-group">
                <label class="field-label">Min Players *</label>
                <input v-model.number="gameForm.minPlayers" type="number" min="1" max="20" class="parlor-input" />
              </div>
              <div class="form-group">
                <label class="field-label">Max Players *</label>
                <input v-model.number="gameForm.maxPlayers" type="number" min="1" max="20" class="parlor-input" />
              </div>
              <div class="form-group">
                <label class="field-label">Duration (min) *</label>
                <input v-model.number="gameForm.durationMinutes" type="number" min="5" max="360" class="parlor-input" />
              </div>
            </div>

            <div class="form-group">
              <label class="field-label">Cover Image URL</label>
              <input v-model="gameForm.coverImage" type="url" class="parlor-input" placeholder="https://example.com/game-cover.jpg" />
            </div>

            <div class="form-group">
              <label class="field-label">Description</label>
              <textarea v-model="gameForm.description" rows="2" class="parlor-input" placeholder="Brief rules or overview of gameplay..."></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn-secondary-parlor" @click="showAddGameModal = false">
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="!gameForm.title.trim()"
              @click="saveGame"
            >
              {{ editingGameId ? 'Update Game in Catalog' : 'Add Game to Catalog' }} →
            </button>
          </div>
        </div>
      </div>

      <!-- Add/Edit Staff Member Modal -->
      <div v-if="showAddStaffModal && isAdmin" class="modal-backdrop" @click.self="showAddStaffModal = false">
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">ADMINISTRATION · STAFF ROSTER</div>
              <h3 class="modal-title">{{ editingStaffId ? 'Edit Staff Member' : 'Add Staff Member' }}</h3>
              <p class="modal-sub">Assign role, shift hours, and floor station.</p>
            </div>
            <button type="button" class="close-x" @click="showAddStaffModal = false">✕</button>
          </div>

          <div class="modal-body">
            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Staff Full Name *</label>
                <input v-model="staffForm.name" type="text" class="parlor-input" placeholder="e.g. Krit Somchai" />
              </div>
              <div class="form-group">
                <label class="field-label">Role *</label>
                <select v-model="staffForm.role" class="parlor-select">
                  <option v-for="r in staffRoles" :key="r" :value="r">{{ r }}</option>
                </select>
              </div>
            </div>

            <div class="form-group">
              <label class="field-label">Assigned Shift *</label>
              <select v-model="staffForm.shift" class="parlor-select">
                <option v-for="s in shiftOptions" :key="s" :value="s">{{ s }}</option>
              </select>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Station / Assigned Area</label>
                <input v-model="staffForm.assignedZone" type="text" class="parlor-input" placeholder="e.g. Tables 1 - 4" />
              </div>
              <div class="form-group">
                <label class="field-label">Duty Status</label>
                <select v-model="staffForm.status" class="parlor-select">
                  <option value="ON_DUTY">On Duty (Clocked in)</option>
                  <option value="ON_BREAK">On Break</option>
                  <option value="OFF_DUTY">Off Duty</option>
                </select>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn-secondary-parlor" @click="showAddStaffModal = false">
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="!staffForm.name.trim()"
              @click="saveStaff"
            >
              {{ editingStaffId ? 'Save Staff Changes' : 'Add Staff Member' }} →
            </button>
          </div>
        </div>
      </div>

      <!-- Add/Edit Venue Operational Policy Modal -->
      <div v-if="showPolicyModal && isAdmin" class="modal-backdrop" @click.self="showPolicyModal = false">
        <div class="modal-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">ADMINISTRATION · VENUE GOVERNANCE</div>
              <h3 class="modal-title">{{ editingPolicyId ? 'Edit Operational Policy' : 'Add Venue Policy' }}</h3>
              <p class="modal-sub">Define operational parameters, grace limits, or pricing rules.</p>
            </div>
            <button type="button" class="close-x" @click="showPolicyModal = false">✕</button>
          </div>

          <div class="modal-body">
            <div class="form-group">
              <label class="field-label">Policy Title *</label>
              <input v-model="policyForm.title" type="text" class="parlor-input" placeholder="e.g. Automatic 15-Minute No-Show Release" />
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="field-label">Category *</label>
                <select v-model="policyForm.category" class="parlor-select">
                  <option v-for="c in policyCategories" :key="c" :value="c">{{ c }}</option>
                </select>
              </div>
              <div class="form-group">
                <label class="field-label">Key Metric / Threshold</label>
                <input v-model="policyForm.metric" type="text" class="parlor-input" placeholder="e.g. 15 Mins, ฿300/hr" />
              </div>
            </div>

            <div class="form-group">
              <label class="field-label">Policy Description *</label>
              <textarea v-model="policyForm.description" rows="3" class="parlor-input" placeholder="Describe the policy condition and operational enforcement..."></textarea>
            </div>

            <div class="form-group">
              <label class="field-label">Enforcement Status</label>
              <select v-model="policyForm.active" class="parlor-select">
                <option :value="true">Active & Enforced</option>
                <option :value="false">Inactive / Draft</option>
              </select>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn-secondary-parlor" @click="showPolicyModal = false">
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary-parlor"
              :disabled="!policyForm.title.trim()"
              @click="savePolicy"
            >
              {{ editingPolicyId ? 'Save Policy Changes' : 'Create Policy' }} →
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ========================================================= */
/* THE PARLOR CENTRAL MANAGEMENT CONSOLE STYLES              */
/* Palette: Deep Forest Green (var(--color-parlor-green-900)), Champagne Gold      */
/* (var(--color-parlor-gold-500) / var(--color-parlor-gold-300)), and Crisp White (var(--color-parlor-white)) blocks    */
/* ========================================================= */

.gl-admin-layout {
  display: flex;
  min-height: 100vh;
  background-color: var(--color-parlor-green-900);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
  color: var(--color-parlor-green-900);
}

/* SIDEBAR */
.gl-sidebar {
  width: 220px;
  background: var(--color-parlor-green-950);
  border-right: 1.5px solid var(--color-parlor-green-800);
  display: flex;
  flex-direction: column;
  padding: 18px 12px;
  flex-shrink: 0;
  box-sizing: border-box;
  overflow-x: hidden;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.gl-sidebar.collapsed {
  width: 68px;
}

.gl-sidebar-brand {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding: 0 10px;
  box-sizing: border-box;
  width: 100%;
}

.brand-dice-icon {
  width: 24px;
  min-width: 24px;
  height: 24px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-text-col {
  display: flex;
  flex-direction: column;
  margin-left: 10px;
  white-space: nowrap;
  overflow: hidden;
  opacity: 1;
  min-width: 0;
  transition: opacity 0.2s cubic-bezier(0.4, 0, 0.2, 1),
              max-width 0.25s cubic-bezier(0.4, 0, 0.2, 1),
              margin-left 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.gl-sidebar.collapsed .brand-text-col {
  opacity: 0;
  max-width: 0;
  margin-left: 0;
  pointer-events: none;
  visibility: hidden;
}

.brand-sub {
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--color-parlor-gold-500);
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-parlor-white);
  letter-spacing: -0.01em;
}

.sidebar-collapse-btn {
  background: transparent;
  border: none;
  font-size: 11px;
  color: var(--color-parlor-gold-500);
  text-align: left;
  padding: 6px 10px;
  margin-bottom: 16px;
  cursor: pointer;
  transition: color 0.15s ease, background-color 0.15s ease;
  display: flex;
  align-items: center;
  border-radius: 8px;
  box-sizing: border-box;
  width: 100%;
}

.sidebar-collapse-btn:hover {
  color: var(--color-parlor-gold-300);
  background: color-mix(in srgb, var(--color-parlor-green-800) 35%, transparent);
}

.collapse-icon-box {
  width: 24px;
  min-width: 24px;
  height: 24px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.collapse-arrow-icon {
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.collapse-arrow-icon.is-collapsed {
  transform: rotate(180deg);
}

.collapse-text {
  margin-left: 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  opacity: 1;
  font-weight: 600;
  letter-spacing: 0.04em;
  transition: opacity 0.2s cubic-bezier(0.4, 0, 0.2, 1),
              max-width 0.25s cubic-bezier(0.4, 0, 0.2, 1),
              margin-left 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.gl-sidebar.collapsed .collapse-text {
  opacity: 0;
  max-width: 0;
  margin-left: 0;
  pointer-events: none;
  visibility: hidden;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: thin;
  scrollbar-color: var(--color-parlor-green-800) transparent;
}

.nav-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 10px 4px;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--color-parlor-gold-500);
  white-space: nowrap;
  overflow: hidden;
  max-height: 36px;
  opacity: 1;
  transition: opacity 0.15s ease,
              max-height 0.25s cubic-bezier(0.4, 0, 0.2, 1),
              padding 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.gl-sidebar.collapsed .nav-section-title {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  margin: 0;
  pointer-events: none;
  visibility: hidden;
}

.role-pill-tiny {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 10px;
  background: var(--color-parlor-green-850);
  color: var(--color-parlor-gold-300);
  border: 1px solid var(--color-parlor-gold-500);
}

.role-pill-tiny.admin {
  background: var(--color-parlor-neutral-800);
  color: var(--color-parlor-gold-300);
  border-color: var(--color-parlor-gold-300);
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 8px 10px 8px 7px;
  border-left: 3px solid transparent;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-parlor-neutral-300);
  text-decoration: none;
  position: relative;
  box-sizing: border-box;
  overflow: hidden;
  width: 100%;
  transition: background-color 0.15s ease,
              color 0.15s ease,
              border-color 0.15s ease;
}

.nav-item:hover {
  background: var(--color-parlor-green-850);
  color: var(--color-parlor-white);
}

.nav-item.active {
  background: var(--color-parlor-green-800);
  color: var(--color-parlor-gold-300);
  font-weight: 700;
  border-left-color: var(--color-parlor-gold-500);
}

.nav-icon-box {
  width: 24px;
  min-width: 24px;
  height: 24px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-icon {
  flex-shrink: 0;
}

.nav-label {
  margin-left: 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  opacity: 1;
  min-width: 0;
  flex: 1;
  transition: opacity 0.2s cubic-bezier(0.4, 0, 0.2, 1),
              max-width 0.25s cubic-bezier(0.4, 0, 0.2, 1),
              margin-left 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  max-width: 160px;
}

.gl-sidebar.collapsed .nav-label {
  opacity: 0;
  max-width: 0;
  margin-left: 0;
  pointer-events: none;
  visibility: hidden;
}

.nav-badge-gold {
  margin-left: auto;
  background: var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  font-size: 10.5px;
  font-weight: 700;
  border-radius: 10px;
  padding: 1px 6px;
  white-space: nowrap;
  flex-shrink: 0;
  opacity: 1;
  transition: opacity 0.2s ease, max-width 0.25s ease;
}

.gl-sidebar.collapsed .nav-badge-gold {
  opacity: 0;
  max-width: 0;
  padding: 0;
  margin: 0;
  pointer-events: none;
  visibility: hidden;
}

.nav-divider {
  height: 1px;
  background: var(--color-parlor-green-800);
  margin: 10px 0;
  transition: margin 0.25s ease;
}

.sidebar-footer {
  border-top: 1.5px solid var(--color-parlor-green-800);
  padding-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: hidden;
  white-space: nowrap;
  max-height: 120px;
  opacity: 1;
  transition: opacity 0.15s ease,
              max-height 0.25s cubic-bezier(0.4, 0, 0.2, 1),
              padding 0.25s cubic-bezier(0.4, 0, 0.2, 1),
              border-color 0.25s ease;
}

.gl-sidebar.collapsed .sidebar-footer {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  border-top-color: transparent;
  pointer-events: none;
  visibility: hidden;
}

.user-profile-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar-gold {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1.5px solid var(--color-parlor-gold-500);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-info-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-name {
  font-size: 12.5px;
  color: var(--color-parlor-white);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role-label {
  font-size: 10.5px;
  color: var(--color-parlor-gold-500);
}

.btn-signout {
  background: transparent;
  border: 1px solid var(--color-parlor-green-600);
  color: var(--color-parlor-neutral-300);
  border-radius: 6px;
  padding: 5px 8px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  text-align: center;
  transition: all 0.12s ease;
}

.btn-signout:hover {
  background: var(--color-parlor-green-850);
  color: var(--color-parlor-gold-300);
  border-color: var(--color-parlor-gold-500);
}

@media (prefers-reduced-motion: reduce) {
  .gl-sidebar,
  .nav-item,
  .nav-label,
  .nav-badge-gold,
  .brand-text-col,
  .collapse-text,
  .collapse-arrow-icon,
  .nav-section-title,
  .sidebar-footer {
    transition: none !important;
  }
}

/* MAIN CONTENT WRAPPER & TOPBAR */
.gl-main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.gl-topbar {
  height: 60px;
  background: var(--color-parlor-green-900);
  border-bottom: 1.5px solid var(--color-parlor-green-700);
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.branch-selector-capsule {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--color-parlor-green-850);
  border: 1px solid var(--color-parlor-gold-500);
  border-radius: 20px;
  padding: 4px 12px;
}

.pin-icon {
  font-size: 11px;
  color: var(--color-parlor-gold-300);
}

.branch-select {
  border: none;
  background: transparent;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--color-parlor-gold-300);
  outline: none;
  cursor: pointer;
}

.branch-select option {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-white);
}

.console-role-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--color-parlor-green-950);
  border: 1px solid var(--color-parlor-green-600);
  border-radius: 20px;
  padding: 4px 10px;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--color-parlor-gold-500);
}

.pulse-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-parlor-success-500);
}

.topbar-search {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--color-parlor-green-950);
  border: 1px solid var(--color-parlor-green-700);
  border-radius: 20px;
  padding: 6px 14px;
  width: 320px;
}

.search-icon {
  color: var(--color-parlor-gold-500);
}

.topbar-search-input {
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--color-parlor-white);
  outline: none;
  width: 100%;
}

.topbar-search-input::placeholder {
  color: var(--color-parlor-neutral-500);
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-circle-btn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid var(--color-parlor-green-600);
  background: var(--color-parlor-green-850);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-parlor-gold-300);
  cursor: pointer;
  text-decoration: none;
  transition: all 0.12s ease;
}

.icon-circle-btn:hover {
  background: var(--color-parlor-green-800);
  border-color: var(--color-parlor-gold-500);
}

.mobile-menu-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1.5px solid var(--color-parlor-green-600);
  border-radius: 8px;
  color: var(--color-parlor-gold-300);
  width: 36px;
  height: 36px;
  cursor: pointer;
  padding: 0;
  transition: all 0.15s ease;
}

.mobile-menu-toggle:hover {
  background: var(--color-parlor-green-850);
  border-color: var(--color-parlor-gold-500);
}

.sidebar-mobile-overlay {
  display: none;
}

.topbar-avatar {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--color-parlor-green-900);
  border: 1.5px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-gold-300);
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* MAIN CONTENT BLOCKS */
.gl-content {
  padding: 24px 28px 60px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1340px;
}

.console-block {
  background: var(--color-parlor-white);
  border: 1.5px solid var(--color-parlor-gold-500);
  border-radius: 16px;
  padding: 24px 26px;
  box-shadow: 0 10px 30px color-mix(in srgb, var(--color-parlor-green-950) 25%, transparent);
}

/* Hero Block */
.hero-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.eyebrow-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.gold-sparkle {
  color: var(--color-parlor-gold-500);
  font-size: 14px;
}

.gold-bullet {
  color: var(--color-parlor-gold-500);
  font-size: 12px;
}

.hero-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-parlor-gold-700);
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hero-title {
  margin: 0;
  font-family: 'Playfair Display', Georgia, serif;
  font-size: 26px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.live-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--color-parlor-success-100);
  color: var(--color-parlor-green-900);
  border: 1px solid var(--color-parlor-success-300);
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 12px;
}

.live-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-parlor-success-500);
}

.hero-subtitle {
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--color-parlor-neutral-600);
}

.hero-actions-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 10px;
  border-top: 1px solid var(--color-parlor-neutral-150);
}

.btn-primary-parlor {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1.5px solid var(--color-parlor-gold-500);
  border-radius: 20px;
  padding: 8px 20px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 3px 8px color-mix(in srgb, var(--color-parlor-green-900) 25%, transparent);
  transition: all 0.12s ease;
}

.btn-primary-parlor:hover {
  background: var(--color-parlor-green-850);
  border-color: var(--color-parlor-gold-300);
}

.btn-primary-parlor.small {
  padding: 5px 14px;
  font-size: 12px;
}

.btn-primary-parlor.full {
  width: 100%;
  justify-content: center;
}

.btn-secondary-parlor {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--color-parlor-white);
  border: 1.5px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  border-radius: 20px;
  padding: 8px 18px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.12s ease;
}

.btn-secondary-parlor:hover {
  background: var(--color-parlor-neutral-100);
}

.btn-secondary-parlor.small {
  padding: 5px 12px;
  font-size: 12px;
}

.actions-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.updated-time {
  font-size: 12px;
  color: var(--color-parlor-gold-700);
}

.btn-refresh-parlor {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  border-radius: 20px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.btn-refresh-parlor:hover {
  background: var(--color-parlor-neutral-100);
}

/* 4-Column Metric Ribbon Block */
.numbers-card-ribbon {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  padding: 18px 24px;
}

.num-col {
  display: flex;
  flex-direction: column;
  padding: 0 16px;
  border-right: 1px solid var(--color-parlor-neutral-150);
}

.num-col:last-child {
  border-right: none;
}

.col-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-parlor-gold-700);
  margin-bottom: 2px;
}

.col-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
  line-height: 1.2;
}

.segmented-progress {
  display: flex;
  gap: 3px;
  margin: 6px 0 4px;
}

.progress-seg {
  height: 5px;
  flex: 1;
  background: var(--color-parlor-neutral-300);
  border-radius: 2px;
}

.progress-seg.active {
  background: var(--color-parlor-green-900);
}

.col-sub {
  font-size: 12px;
  color: var(--color-parlor-neutral-600);
}

/* Needs You Now Queue */
.needs-you-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.needs-you-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.needs-you-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: 'Playfair Display', Georgia, serif;
  font-size: 19px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.needs-count-pill {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1px solid var(--color-parlor-gold-500);
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: sans-serif;
}

.most-urgent {
  font-size: 11.5px;
  color: var(--color-parlor-gold-700);
}

.needs-items-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.needs-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 12px;
  background: var(--color-parlor-page);
}

.needs-item-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.alert-icon-square {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--color-parlor-neutral-100);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-gold-700);
  display: flex;
  align-items: center;
  justify-content: center;
}

.needs-item-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.needs-item-sub {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--color-parlor-neutral-600);
}

.needs-item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-gold-pill {
  background: var(--color-parlor-neutral-100);
  border: 1.5px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  border-radius: 18px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.btn-gold-pill:hover {
  background: var(--color-parlor-neutral-100);
}

/* Two-Column Lower Split */
.two-column-service-row {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 20px;
}

.section-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1.5px solid var(--color-parlor-neutral-300);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title {
  margin: 0;
  font-family: 'Playfair Display', Georgia, serif;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.live-tag {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-parlor-success-500);
}

.usage-text, .timeline-span {
  font-size: 12px;
  color: var(--color-parlor-neutral-600);
}

.count-bubble {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1px solid var(--color-parlor-gold-500);
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 12px;
}

/* Floor Grid */
.floor-tables-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.floor-tables-grid.expanded {
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.floor-table-tile {
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: var(--color-parlor-white);
  transition: all 0.12s ease;
}

.floor-table-tile.big {
  padding: 18px;
}

.floor-table-tile.free {
  border-color: var(--color-parlor-success-500);
  background: var(--color-parlor-success-100);
}

.floor-table-tile.occupied {
  border-color: var(--color-parlor-gold-500);
  background: var(--color-parlor-white);
}

.floor-table-tile.reserved {
  border-color: var(--color-parlor-neutral-500);
  background: var(--color-parlor-page);
}

.tile-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.table-num-lbl {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.table-seats-lbl {
  font-size: 11.5px;
  color: var(--color-parlor-neutral-600);
}

/* Assigned Game Chips on Tables */
.tile-assigned-games-box {
  background: var(--color-parlor-neutral-100);
  border: 1px solid var(--color-parlor-gold-500);
  border-radius: 8px;
  padding: 6px 8px;
  margin: 4px 0;
}

.assigned-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.assigned-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-parlor-gold-700);
  display: block;
}

.assigned-chips-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.game-chip-capsule {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  font-size: 11px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 12px;
}

.btn-chip-unassign {
  background: transparent;
  border: none;
  color: var(--color-parlor-gold-300);
  cursor: pointer;
  font-size: 10px;
  padding: 0;
  margin-left: 2px;
  opacity: 0.8;
}

.btn-chip-unassign:hover {
  opacity: 1;
  color: var(--color-parlor-danger-600);
}

.btn-tile-assign-game {
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  font-size: 10.5px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
  cursor: pointer;
}

.btn-tile-assign-game:hover {
  background: var(--color-parlor-neutral-100);
}

.btn-tile-assign-game.small {
  padding: 1px 6px;
  font-size: 10px;
}

.no-game-text {
  font-size: 11px;
  color: var(--color-parlor-gold-700);
  font-style: italic;
}

.tile-status-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 2px 0;
}

.state-badge {
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.03em;
  padding: 2px 7px;
  border-radius: 6px;
}

.state-badge.badge-free {
  background: var(--color-parlor-success-100);
  color: var(--color-parlor-green-900);
  border: 1px solid var(--color-parlor-success-500);
}

.state-badge.badge-occupied {
  background: var(--color-parlor-warning-100);
  color: var(--color-parlor-warning-700);
  border: 1px solid var(--color-parlor-gold-500);
}

.state-badge.badge-reserved {
  background: var(--color-parlor-neutral-100);
  color: var(--color-parlor-neutral-600);
  border: 1px solid var(--color-parlor-neutral-300);
}

.tile-time-pill {
  font-size: 10px;
  font-weight: 700;
  background: var(--color-parlor-neutral-100);
  color: var(--color-parlor-green-900);
  padding: 2px 6px;
  border-radius: 10px;
}

.tile-guest-name, .guest-name-large {
  margin: 0;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.guest-name-large {
  font-size: 16px;
  margin: 4px 0 2px;
}

.tile-helper, .tile-sub-info, .tile-due-time {
  margin: 0;
  font-size: 11.5px;
  color: var(--color-parlor-neutral-600);
}

.tile-due-time {
  color: var(--color-parlor-neutral-600);
}

.tile-action-link {
  background: transparent;
  border: none;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
  cursor: pointer;
  text-align: left;
  padding: 4px 0 0;
  text-decoration: underline;
}

.tile-actions-row {
  display: flex;
  gap: 6px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.tile-btn-small {
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  border-radius: 6px;
  padding: 4px 6px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.tile-btn-assign {
  background: var(--color-parlor-neutral-100);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  border-radius: 6px;
  padding: 4px 6px;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.tile-btn-gold {
  background: var(--color-parlor-green-900);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-gold-300);
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

/* Coming Up Timeline */
.policy-rule-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--color-parlor-neutral-100);
  border: 1px solid var(--color-parlor-gold-500);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 11.5px;
  color: var(--color-parlor-neutral-600);
  margin-bottom: 12px;
}

.empty-timeline {
  text-align: center;
  padding: 28px 16px;
  color: var(--color-parlor-gold-700);
  font-size: 13px;
}

.timeline-cards-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.timeline-card {
  display: flex;
  gap: 14px;
  background: var(--color-parlor-page);
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 12px;
  padding: 12px 14px;
}

.timeline-time-col {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.time-bold {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.timeline-card-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.timeline-card-top {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.timeline-guest-name {
  font-size: 13.5px;
  color: var(--color-parlor-green-900);
}

.party-tag, .table-tag {
  font-size: 12px;
  color: var(--color-parlor-neutral-600);
}

.table-tag {
  background: var(--color-parlor-neutral-100);
  color: var(--color-parlor-green-900);
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 6px;
  font-size: 11px;
}

.timeline-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-parlor-pill {
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  border-radius: 14px;
  padding: 3px 10px;
  font-size: 11.5px;
  font-weight: 700;
  cursor: pointer;
}

.btn-cancel-link {
  background: transparent;
  border: none;
  font-size: 11.5px;
  color: var(--color-parlor-danger-700);
  cursor: pointer;
  text-decoration: underline;
  padding: 0 4px;
}

/* GENERIC VIEW HEADERS & TABLES */
.view-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1.5px solid var(--color-parlor-neutral-300);
  gap: 16px;
  flex-wrap: wrap;
}

.view-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-parlor-gold-700);
  margin-bottom: 4px;
}

.view-title {
  margin: 0 0 4px;
  font-family: 'Playfair Display', Georgia, serif;
  font-size: 22px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.view-subtitle {
  margin: 0;
  font-size: 13.5px;
  color: var(--color-parlor-neutral-600);
}

.header-mgmt-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.role-pill-gold {
  background: var(--color-parlor-neutral-100);
  border: 1.5px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  font-size: 11.5px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 20px;
}

.role-pill-gold.admin {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
}

/* Parlor Table */
.parlor-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13.5px;
}

.parlor-table th {
  text-align: left;
  padding: 10px 14px;
  background: var(--color-parlor-page);
  color: var(--color-parlor-gold-700);
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.04em;
  border-bottom: 1.5px solid var(--color-parlor-neutral-300);
}

.parlor-table td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-parlor-neutral-150);
  color: var(--color-parlor-green-900);
}

.table-badge {
  background: var(--color-parlor-neutral-100);
  color: var(--color-parlor-green-900);
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 12px;
}

.session-games-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.session-chips-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.btn-assign-mini {
  background: var(--color-parlor-neutral-100);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  font-size: 10.5px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 10px;
  cursor: pointer;
}

.time-elapsed-badge {
  background: var(--color-parlor-success-100);
  color: var(--color-parlor-green-900);
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 12px;
  font-size: 11px;
}

.fee-text {
  color: var(--color-parlor-green-900);
  font-size: 14px;
}

.actions-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;
}

.status-tag.confirmed { background: var(--color-parlor-success-100); color: var(--color-parlor-success-700); }
.status-tag.checked_in { background: var(--color-parlor-warning-100); color: var(--color-parlor-warning-700); }
.status-tag.completed { background: var(--color-parlor-neutral-100); color: var(--color-parlor-neutral-600); }
.status-tag.cancelled { background: var(--color-parlor-danger-100); color: var(--color-parlor-danger-700); }
.status-tag.no_show { background: var(--color-parlor-danger-100); color: var(--color-parlor-danger-700); }

/* Game Library Ribbon & Cards */
.library-filter-ribbon {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.filter-pill {
  background: var(--color-parlor-white);
  border: 1.5px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  border-radius: 20px;
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.filter-pill.active {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
}

.games-library-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.game-catalog-card {
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: var(--color-parlor-white);
  position: relative;
}

/* Game Status Strip (Available / Unavailable) */
.game-status-strip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  margin-bottom: 6px;
}

.game-status-strip.avail {
  background: var(--color-parlor-success-100);
  color: var(--color-parlor-success-700);
  border: 1px solid var(--color-parlor-success-500);
}

.game-status-strip.unavail {
  background: var(--color-parlor-danger-100);
  color: var(--color-parlor-danger-700);
  border: 1px solid var(--color-parlor-danger-600);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.btn-return-shelf {
  background: var(--color-parlor-danger-100);
  border: 1.5px solid var(--color-parlor-danger-600);
  color: var(--color-parlor-danger-700);
  border-radius: 16px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  width: 100%;
}

.btn-return-shelf:hover {
  background: var(--color-parlor-danger-100);
}

.game-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.game-name {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.game-difficulty-pill {
  font-size: 10.5px;
  font-weight: 700;
  background: var(--color-parlor-neutral-100);
  color: var(--color-parlor-green-900);
  padding: 2px 6px;
  border-radius: 6px;
}

.game-designer {
  margin: 0;
  font-size: 12px;
  color: var(--color-parlor-neutral-600);
}

.game-meta-row {
  display: flex;
  gap: 12px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--color-parlor-green-900);
  margin: 4px 0;
}

.game-desc {
  margin: 0;
  font-size: 12px;
  color: var(--color-parlor-neutral-600);
  line-height: 1.4;
  flex: 1;
}

.game-card-footer {
  margin-top: 8px;
}

/* Modal Games List for Assignment */
.rule-warning-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--color-parlor-warning-100);
  border: 1px solid var(--color-parlor-warning-300);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  color: var(--color-parlor-warning-700);
  margin-bottom: 12px;
  line-height: 1.4;
}

.modal-games-list {
  max-height: 200px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.modal-game-choice-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 8px;
  cursor: pointer;
  background: var(--color-parlor-white);
}

.modal-game-choice-row.selected {
  border-color: var(--color-parlor-gold-500);
  background: var(--color-parlor-neutral-100);
}

.modal-game-choice-row.in-use {
  opacity: 0.55;
  cursor: not-allowed;
  border-color: var(--color-parlor-danger-300);
  background: var(--color-parlor-danger-100);
}

.game-choice-info {
  display: flex;
  flex-direction: column;
}

.game-choice-name {
  font-size: 13px;
  color: var(--color-parlor-green-900);
}

.game-choice-sub {
  font-size: 11px;
  color: var(--color-parlor-neutral-600);
}

.mini-status-tag {
  font-size: 10.5px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 6px;
}

.mini-status-tag.avail {
  background: var(--color-parlor-success-100);
  color: var(--color-parlor-success-700);
}

.mini-status-tag.unavail {
  background: var(--color-parlor-danger-100);
  color: var(--color-parlor-danger-700);
}

/* Table choice pill states in Assign Modal */
.tbl-choice-pill.disabled {
  opacity: 0.45;
  cursor: not-allowed;
  background: var(--color-parlor-neutral-100);
  border-color: var(--color-parlor-neutral-300);
}

.pill-reason {
  font-size: 9.5px;
  margin-top: 2px;
}

.pill-reason.has-game {
  color: var(--color-parlor-warning-600);
  font-weight: 700;
}

.pill-reason.ready {
  color: var(--color-parlor-success-700);
  font-weight: 700;
}

/* ========================================================= */
/* FOOD & DRINK STOCK MANAGEMENT STYLES                      */
/* ========================================================= */
.stock-stats-ribbon {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}

.stock-stat-box {
  background: var(--color-parlor-page);
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.stat-lbl {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-parlor-gold-700);
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
  line-height: 1.2;
  margin: 6px 0 2px;
}

.stat-num.text-green { color: var(--color-parlor-success-700); }
.stat-num.text-amber { color: var(--color-parlor-warning-600); }
.stat-num.text-red { color: var(--color-parlor-danger-700); }

.stat-sub {
  font-size: 12px;
  color: var(--color-parlor-neutral-600);
}

.stock-filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  padding: 10px 14px;
  background: var(--color-parlor-page);
  border-radius: 10px;
  border: 1px solid var(--color-parlor-neutral-300);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-lbl {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--color-parlor-gold-700);
}

.pill-tab {
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  border-radius: 14px;
  padding: 4px 10px;
  font-size: 11.5px;
  font-weight: 600;
  cursor: pointer;
}

.pill-tab.active {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
}

.pill-tab.alert.active {
  background: var(--color-parlor-warning-600);
  color: var(--color-parlor-white);
  border-color: var(--color-parlor-warning-700);
}

.stock-table td {
  padding: 12px 14px;
  vertical-align: middle;
}

.stock-item-info {
  display: flex;
  flex-direction: column;
}

.category-tag {
  font-size: 11px;
  font-weight: 700;
  text-transform: capitalize;
  background: var(--color-parlor-neutral-100);
  color: var(--color-parlor-green-900);
  padding: 2px 7px;
  border-radius: 6px;
}

.stock-qty-badge {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  background: var(--color-parlor-success-100);
  border: 1.5px solid var(--color-parlor-success-500);
  border-radius: 8px;
  padding: 4px 10px;
}

.stock-qty-badge.low {
  background: var(--color-parlor-warning-100);
  border-color: var(--color-parlor-warning-600);
}

.stock-qty-badge.empty {
  background: var(--color-parlor-danger-100);
  border-color: var(--color-parlor-danger-600);
}

.qty-big {
  font-size: 16px;
  color: var(--color-parlor-green-900);
}

.qty-unit {
  font-size: 11px;
  color: var(--color-parlor-neutral-600);
}

.stock-status-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 8px;
}

.stock-status-tag.healthy { background: var(--color-parlor-success-100); color: var(--color-parlor-success-700); }
.stock-status-tag.low { background: var(--color-parlor-warning-100); color: var(--color-parlor-warning-700); }
.stock-status-tag.out { background: var(--color-parlor-danger-100); color: var(--color-parlor-danger-700); }

.price-val {
  color: var(--color-parlor-green-900);
  font-size: 14px;
}

.margin-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11.5px;
}

.margin-pill {
  color: var(--color-parlor-success-700);
  font-size: 11px;
}

.restock-actions-cell {
  display: flex;
  gap: 6px;
}

.btn-batch-restock {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1px solid var(--color-parlor-gold-500);
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.btn-custom-restock {
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.btn-delete-sku {
  background: transparent;
  border: 1px solid var(--color-parlor-danger-300);
  color: var(--color-parlor-danger-600);
  border-radius: 6px;
  padding: 3px 6px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.btn-delete-sku:hover {
  background: var(--color-parlor-danger-100);
}

.stock-current-preview, .new-stock-preview {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--color-parlor-page);
  border-radius: 8px;
  font-size: 13px;
  color: var(--color-parlor-green-900);
}

.new-stock-preview {
  margin-top: 10px;
  background: var(--color-parlor-success-100);
  border: 1px solid var(--color-parlor-success-400);
}

/* Staff Roster Grid */
.staff-roster-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.staff-member-card {
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 12px;
  padding: 16px;
  background: var(--color-parlor-white);
}

.staff-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.staff-avatar-circle {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1.5px solid var(--color-parlor-gold-500);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
}

.staff-name {
  margin: 0 0 2px;
  font-size: 16px;
  color: var(--color-parlor-green-900);
}

.staff-role-badge {
  font-size: 11px;
  font-weight: 700;
  background: var(--color-parlor-neutral-100);
  color: var(--color-parlor-green-900);
  padding: 1px 8px;
  border-radius: 10px;
}

.staff-card-body p {
  margin: 4px 0;
  font-size: 13px;
  color: var(--color-parlor-neutral-600);
}

.duty-status-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.pulse-green {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-parlor-success-500);
}

.duty-lbl {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--color-parlor-success-700);
}

/* Analytics Ribbon Grid */
.analytics-ribbon-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.analytics-card {
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 12px;
  padding: 18px;
  background: var(--color-parlor-page);
}

.analytics-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-parlor-gold-700);
}

.analytics-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--color-parlor-green-900);
  margin: 6px 0 2px;
}

.analytics-sub {
  font-size: 12px;
  color: var(--color-parlor-neutral-600);
}

/* Policies Cards */
.policies-cards-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.policy-card {
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 12px;
  padding: 18px;
  background: var(--color-parlor-page);
}

.policy-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.policy-card-title {
  margin: 0 0 6px;
  font-size: 16px;
  color: var(--color-parlor-green-900);
}

.policy-card-desc {
  margin: 0;
  font-size: 13.5px;
  color: var(--color-parlor-neutral-600);
  line-height: 1.5;
}

.policy-active-tag {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1px solid var(--color-parlor-gold-500);
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 12px;
  white-space: nowrap;
}

/* MODALS */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: color-mix(in srgb, var(--color-parlor-green-950) 70%, transparent);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.modal-card {
  background: var(--color-parlor-white);
  border: 2px solid var(--color-parlor-gold-500);
  border-radius: 16px;
  padding: 24px;
  width: 100%;
  max-width: 520px;
  box-shadow: 0 16px 40px color-mix(in srgb, var(--color-parlor-green-950) 35%, transparent);
}

.modal-card.snack-card {
  max-width: 480px;
}

.walkin-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1.5px solid var(--color-parlor-neutral-300);
}

.eyebrow-sub {
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--color-parlor-gold-700);
}

.modal-title {
  margin: 2px 0 4px;
  font-family: 'Playfair Display', Georgia, serif;
  font-size: 20px;
  color: var(--color-parlor-green-900);
}

.modal-sub {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-parlor-neutral-600);
}

.close-x {
  background: transparent;
  border: none;
  font-size: 18px;
  color: var(--color-parlor-gold-700);
  cursor: pointer;
}

.tables-choice-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 6px;
}

.tbl-choice-pill {
  background: var(--color-parlor-page);
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 8px;
  padding: 8px 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  font-size: 11px;
  color: var(--color-parlor-green-900);
}

.tbl-choice-pill.active {
  background: var(--color-parlor-green-900);
  border-color: var(--color-parlor-gold-500);
  color: var(--color-parlor-gold-300);
}

.tbl-choice-pill.occupied {
  opacity: 0.45;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 12px;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.modal-label, .parlor-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.modal-input, .modal-select, .parlor-input {
  border: 1.5px solid var(--color-parlor-gold-500);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13.5px;
  background: var(--color-parlor-white);
  color: var(--color-parlor-green-900);
  outline: none;
}

.stepper-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-btn {
  width: 32px;
  height: 32px;
  border: 1.5px solid var(--color-parlor-gold-500);
  background: var(--color-parlor-white);
  border-radius: 6px;
  font-size: 16px;
  cursor: pointer;
  color: var(--color-parlor-green-900);
}

.step-val {
  font-size: 14px;
  font-weight: 700;
  min-width: 24px;
  text-align: center;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1.5px solid var(--color-parlor-neutral-300);
}

/* Snack Modal Body */
.snack-list-body {
  max-height: 280px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 12px 0;
}

.snack-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 8px;
}

.snack-row.out-of-stock-row {
  opacity: 0.55;
  background: var(--color-parlor-danger-100);
}

.snack-info {
  display: flex;
  flex-direction: column;
}

.snack-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.snack-price {
  font-size: 11.5px;
  color: var(--color-parlor-gold-700);
}

.stock-ok {
  color: var(--color-parlor-success-700);
  font-size: 11px;
}

.stock-out {
  color: var(--color-parlor-danger-600);
  font-weight: 700;
  font-size: 11px;
}

.snack-modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1.5px solid var(--color-parlor-neutral-300);
  padding-top: 12px;
  margin-top: 12px;
}

.snack-total {
  font-size: 13px;
  color: var(--color-parlor-green-900);
}

.total-val {
  font-size: 18px;
  color: var(--color-parlor-green-900);
  margin-left: 6px;
}

.modal-buttons {
  display: flex;
  gap: 8px;
}

/* ========================================== */
/* ADMIN TABLES & BRANCHES EXPANSION STYLES   */
/* ========================================== */
.table-controls-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 12px;
}

.parlor-select {
  height: 38px;
  padding: 0 12px;
  background-color: var(--color-parlor-white);
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 8px;
  font-size: 13px;
  color: var(--color-parlor-green-900);
  outline: none;
  transition: all 0.2s ease;
  cursor: pointer;
}

.parlor-select:focus {
  border-color: var(--color-parlor-green-900);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-parlor-green-900) 10%, transparent);
}

.btn-xs-secondary {
  padding: 4px 10px;
  background: var(--color-parlor-neutral-150);
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-parlor-green-900);
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-xs-secondary:hover {
  background: var(--color-parlor-neutral-150);
  border-color: var(--color-parlor-neutral-400);
}

.btn-xs-danger {
  padding: 4px 10px;
  background: var(--color-parlor-danger-100);
  border: 1px solid var(--color-parlor-danger-300);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-parlor-danger-700);
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-xs-danger:hover {
  background: var(--color-parlor-danger-300);
  border-color: var(--color-parlor-danger-300);
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-row-3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px;
}

/* Operating Hours Editor */
.operating-hours-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--color-parlor-page);
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 8px;
  padding: 10px 14px;
}

.day-hours-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.day-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-parlor-green-900);
  min-width: 130px;
}

.time-range-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.time-field {
  padding: 5px 8px;
  border: 1.5px solid var(--color-parlor-gold-500);
  border-radius: 6px;
  font-size: 13px;
  background: var(--color-parlor-white);
  color: var(--color-parlor-green-900);
  font-family: inherit;
  outline: none;
}

.time-field:focus {
  border-color: var(--color-parlor-green-900);
}

.time-sep {
  font-size: 12px;
  color: var(--color-parlor-neutral-500);
  font-weight: 500;
}

/* Responsive Table Wrappers */
.sessions-table-wrap,
.bookings-list-wrap,
.inventory-table-wrap {
  width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  margin-bottom: 12px;
}

/* ========================================== */
/* RESPONSIVE DESIGN BREAKPOINTS             */
/* ========================================== */

@media (max-width: 1200px) {
  .stock-stats-ribbon,
  .analytics-ribbon-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .games-library-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 1024px) {
  .gl-content {
    padding: 20px 20px 48px;
  }

  .stat-ribbon {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .num-col {
    padding: 10px 14px;
    border-right: none;
    border-bottom: 1px solid var(--color-parlor-neutral-150);
  }

  .num-col:last-child {
    border-bottom: none;
  }

  .floor-tables-grid,
  .floor-tables-grid.expanded {
    grid-template-columns: repeat(2, 1fr);
  }

  .staff-roster-grid {
    grid-template-columns: 1fr;
  }

  .form-row-3 {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .sidebar-mobile-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: color-mix(in srgb, var(--color-parlor-green-950) 65%, transparent);
    backdrop-filter: blur(2px);
    z-index: 998;
  }

  .mobile-menu-toggle {
    display: flex;
  }

  .sidebar-collapse-btn {
    display: none;
  }

  .gl-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: 999;
    box-shadow: 4px 0 24px color-mix(in srgb, var(--color-parlor-green-950) 50%, transparent);
    transform: translateX(0);
    transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    width: 250px;
  }

  .gl-sidebar.collapsed {
    transform: translateX(-100%);
    width: 250px;
    pointer-events: none;
  }

  .gl-sidebar.collapsed .brand-text-col,
  .gl-sidebar.collapsed .nav-label,
  .gl-sidebar.collapsed .nav-section-title,
  .gl-sidebar.collapsed .sidebar-footer {
    opacity: 1;
    max-width: none;
    max-height: none;
    visibility: visible;
    pointer-events: auto;
  }

  .gl-topbar {
    height: auto;
    min-height: 56px;
    padding: 10px 16px;
    flex-wrap: wrap;
    gap: 10px;
  }

  .topbar-left {
    flex: 1;
    min-width: 0;
  }

  .topbar-search {
    order: 3;
    width: 100%;
    margin-top: 4px;
  }

  .topbar-actions {
    order: 2;
  }

  .gl-content {
    padding: 14px 14px 40px;
    gap: 14px;
  }

  .console-block {
    padding: 16px;
    border-radius: 12px;
  }

  .stock-stats-ribbon,
  .analytics-ribbon-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .games-library-grid {
    grid-template-columns: 1fr;
  }

  .form-row-2,
  .form-row-3 {
    grid-template-columns: 1fr;
  }

  .table-controls-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .table-controls-bar > * {
    width: 100%;
  }

  .operating-hours-editor .day-hours-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    padding-bottom: 8px;
    border-bottom: 1px dashed var(--color-parlor-neutral-300);
  }

  .operating-hours-editor .day-hours-row:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }

  .time-range-inputs {
    width: 100%;
    justify-content: flex-start;
  }

  .modal-card {
    max-height: 90vh;
    overflow-y: auto;
  }
}

@media (max-width: 640px) {
  .hero-title {
    font-size: 22px;
  }

  .view-title {
    font-size: 20px;
  }

  .console-role-pill {
    display: none;
  }

  .stat-ribbon {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .floor-tables-grid,
  .floor-tables-grid.expanded {
    grid-template-columns: 1fr;
  }

  .stock-filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .library-filter-ribbon {
    flex-wrap: wrap;
  }

  .modal-card {
    padding: 18px 16px;
  }
}
</style>

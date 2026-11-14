import { mount } from '@vue/test-utils';
import { ref } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { formatStoreTime } from '../../data/session-timing';
import WorkspaceSessions from './WorkspaceSessions.vue';

const { workspace } = vi.hoisted(() => ({
  workspace: { current: null as unknown },
}));
vi.mock('../context', () => ({
  useManagementWorkspace: () => workspace.current,
}));

function stub(hourlyRate: number | undefined) {
  const res = {
    id: 'r1',
    tableId: 't1',
    live: true,
    partySize: 2,
    customer: { displayName: 'Khun Ananda' },
    checkedInAt: '2026-05-01T06:10:00Z',
    startsAt: '2026-05-01T06:00:00Z',
    endsAt: '2026-05-01T08:00:00Z',
    hourlyRate,
  };
  return {
    activeFloorReservations: ref([res]),
    branchFloorTables: [],
    getTableStatus: () => ({ state: 'FREE', reservation: null }),
    formatTime: formatStoreTime,
    getElapsedMinutes: () => 30,
    getSessionTiming: () => ({
      elapsedSeconds: 1800,
      remainingSeconds: 6000,
      overtimeSeconds: 0,
      billableHours: 1,
      ended: false,
      completed: false,
      expiringSoon: false,
    }),
    calculateCurrentPlayFee: () => (hourlyRate ?? 0) * 1,
    getCustomerName: () => 'Khun Ananda',
    openWalkInForTable: vi.fn(),
    openCheckBill: vi.fn(),
    getTableAssignedGames: () => [],
    openAssignGameForTable: vi.fn(),
    unassignGameFromTable: vi.fn(),
    openAddSnacks: vi.fn(),
    selectedBranchId: ref('b1'),
  };
}

describe('Live sessions rate column', () => {
  it('shows a server rate of zero as ฿0, not the sample default', () => {
    workspace.current = stub(0);
    const w = mount(WorkspaceSessions);
    const cells = w.findAll('td.numeric').map((c) => c.text());
    expect(cells[0]).toBe('฿0');
    expect(w.text()).toContain('15:00');
    expect(w.text()).toContain('Est. table fee');
  });

  it('keeps the sample default only when no rate exists', () => {
    workspace.current = stub(undefined);
    const w = mount(WorkspaceSessions);
    expect(w.findAll('td.numeric')[0].text()).toBe('฿200');
  });
});

import { flushPromises } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import TableManagementView from './TableManagementView.vue';
import { renderScreen, stubApi, type RecordedCall } from '../../test/api-stub';
import type { TableResponse } from '../../generated/api/types.gen';

const branches = [
  { id: 'sukhumvit', name: 'Sukhumvit' },
  { id: 'silom', name: 'Silom' },
  { id: 'central', name: 'Bangkok' },
];

function table(
  id: number,
  branch: string,
  status: TableResponse['status'],
): TableResponse {
  return {
    id,
    name: `Table ${id}`,
    branch,
    capacity: 4,
    shape: 'Round',
    status,
    active: true,
    zone: id % 2 === 0 ? 'Private Room' : 'Main Hall',
    lastUpdated: '2026-09-11T00:00:00Z',
  };
}

function initialTables(): TableResponse[] {
  const reserved = new Set([3, 8, 12, 14, 16]);
  const occupied = new Set([5, 10, 18]);
  return [
    ...Array.from({ length: 20 }, (_, index) =>
      table(
        index + 1,
        'Sukhumvit',
        reserved.has(index + 1)
          ? 'Reserved'
          : occupied.has(index + 1)
            ? 'Occupied'
            : 'Available',
      ),
    ),
    table(21, 'Silom', 'Available'),
    table(22, 'Silom', 'Reserved'),
    table(23, 'Bangkok', 'Available'),
    table(24, 'Bangkok', 'Occupied'),
    table(25, 'Bangkok', 'Unavailable'),
  ];
}

function apiForTables() {
  const records = initialTables();
  const calls: RecordedCall[] = stubApi([
    (request) => {
      const url = new URL(request.url);
      return request.method === 'GET' && url.pathname === '/api/v1/branches'
        ? { body: { items: branches } }
        : undefined;
    },
    async (request) => {
      const url = new URL(request.url);
      if (!url.pathname.startsWith('/api/v1/tables')) return undefined;

      if (request.method === 'GET' && url.pathname === '/api/v1/tables') {
        const branch = url.searchParams.get('branch');
        const search = url.searchParams.get('search')?.toLowerCase() ?? '';
        const zone = url.searchParams.get('zone');
        const status = url.searchParams.get('status');
        const page = Number(url.searchParams.get('page') ?? 0);
        const pageSize = Number(url.searchParams.get('pageSize') ?? 5);
        const filtered = records.filter(
          (record) =>
            (!branch || record.branch === branch) &&
            (!search || record.name.toLowerCase().includes(search)) &&
            (!zone || record.zone === zone) &&
            (!status || record.status === status),
        );
        return {
          body: {
            items: filtered.slice(page * pageSize, (page + 1) * pageSize),
            total: filtered.length,
            page,
            pageSize,
            totalPages: Math.ceil(filtered.length / pageSize),
          },
        };
      }

      const id = Number(url.pathname.split('/').at(-1));
      if (request.method === 'POST') {
        const created = {
          ...(JSON.parse(await request.text()) as Omit<
            TableResponse,
            'id' | 'lastUpdated'
          >),
          id: Math.max(...records.map((record) => record.id)) + 1,
          lastUpdated: '2026-09-11T00:00:00Z',
        };
        records.unshift(created);
        return { status: 201, body: created };
      }
      if (request.method === 'PUT') {
        const updated = {
          ...(JSON.parse(await request.text()) as Omit<
            TableResponse,
            'id' | 'lastUpdated'
          >),
          id,
          lastUpdated: '2026-09-11T00:00:00Z',
        };
        const index = records.findIndex((record) => record.id === id);
        records[index] = updated;
        return { body: updated };
      }
      if (request.method === 'DELETE') {
        records.splice(
          records.findIndex((record) => record.id === id),
          1,
        );
        return { status: 204 };
      }
      return undefined;
    },
  ]);
  return { calls };
}

async function mountView() {
  const { calls } = apiForTables();
  const { wrapper } = await renderScreen(TableManagementView, '/tables');
  await flushPromises();
  return { wrapper, calls };
}

describe('Owner table management screen', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('renders the table management view on the default route', async () => {
    vi.useFakeTimers();
    const { wrapper } = await mountView();

    expect(wrapper.text()).toContain('Table management');
    expect(wrapper.text()).toContain('Total tables');
    expect(wrapper.text()).toContain('+ Add table');
    expect(wrapper.text()).toContain('Table 1');
  });

  it('shows the loading state before tables are ready', async () => {
    apiForTables();
    const { wrapper } = await renderScreen(TableManagementView, '/tables', {
      flush: false,
    });

    expect(wrapper.find('[data-testid="tables-loading"]').exists()).toBe(true);
    await flushPromises();
    expect(wrapper.find('[data-testid="tables-loading"]').exists()).toBe(false);
  });

  it('filters tables and shows an empty state when nothing matches', async () => {
    vi.useFakeTimers();
    const { wrapper } = await mountView();
    const search = wrapper.get('input[type="search"]');

    await search.setValue('does not exist');
    await flushPromises();

    expect(wrapper.text()).toContain('No tables match your filters');
    expect(wrapper.find('[aria-label="Table pagination"]').exists()).toBe(
      false,
    );
  });

  it('displays accurate live summary cards for the selected branch', async () => {
    vi.useFakeTimers();
    const { wrapper } = await mountView();

    // Sukhumvit branch has 20 tables: 12 Available, 5 Reserved, 3 Occupied
    const summaryCards = wrapper.findAll('article');
    expect(summaryCards).toHaveLength(4);
    expect(summaryCards[0].text()).toContain('Total tables');
    expect(summaryCards[0].text()).toContain('20');
    expect(summaryCards[1].text()).toContain('Available');
    expect(summaryCards[1].text()).toContain('12');
    expect(summaryCards[2].text()).toContain('Reserved');
    expect(summaryCards[2].text()).toContain('5');
    expect(summaryCards[3].text()).toContain('Occupied');
    expect(summaryCards[3].text()).toContain('3');

    // Switch branch to Silom: 2 tables (1 Available, 1 Reserved)
    const branchSelect = wrapper.get('#panel-branch');
    await branchSelect.setValue('Silom');
    await flushPromises();

    expect(summaryCards[0].text()).toContain('2');
    expect(summaryCards[1].text()).toContain('1');
    expect(summaryCards[2].text()).toContain('1');
    expect(summaryCards[3].text()).toContain('0');
  });

  it('creates a table after validating the form', async () => {
    vi.useFakeTimers();
    const { wrapper, calls } = await mountView();
    const addTable = wrapper
      .findAll('button')
      .find((button) => button.text() === '+ Add table');
    if (!addTable) throw new Error('Add table button was not rendered');
    await addTable.trigger('click');

    expect(wrapper.text()).toContain('Add table');
    await wrapper.get('#table-management-form').trigger('submit');
    expect(wrapper.text()).toContain('Table name is required');

    await wrapper.get('input[placeholder="Table name"]').setValue('Table 30');
    await wrapper.get('#form-capacity').setValue('6');
    await wrapper.get('#table-management-form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('Table 30');
    expect(wrapper.text()).toContain('6 seats');
    expect(
      calls.some(
        (call) =>
          call.method === 'POST' &&
          call.url.endsWith('/api/v1/tables') &&
          call.body?.includes('"name":"Table 30"'),
      ),
    ).toBe(true);
  });

  it('rejects duplicate table names within the same branch and allows in other branches', async () => {
    vi.useFakeTimers();
    const { wrapper } = await mountView();
    const addTable = wrapper
      .findAll('button')
      .find((button) => button.text() === '+ Add table');
    if (!addTable) throw new Error('Add table button was not rendered');
    await addTable.trigger('click');

    const nameInput = wrapper.get('input[placeholder="Table name"]');
    const branchSelect = wrapper.get('#form-branch');

    // Table 1 already exists in Sukhumvit
    await nameInput.setValue('Table 1');
    await branchSelect.setValue('Sukhumvit');
    await wrapper.get('#table-management-form').trigger('submit');

    expect(wrapper.text()).toContain(
      'A table with this name already exists in this branch',
    );

    // Case-insensitive duplicate check
    await nameInput.setValue('table 1');
    await wrapper.get('#table-management-form').trigger('submit');
    expect(wrapper.text()).toContain(
      'A table with this name already exists in this branch',
    );

    // Switch branch to Bangkok where Table 1 does not exist
    await branchSelect.setValue('Bangkok');
    await wrapper.get('#table-management-form').trigger('submit');

    // Form successfully submitted and returned to list
    expect(wrapper.text()).not.toContain(
      'A table with this name already exists in this branch',
    );
  });

  it('supports capacity options and validates name length constraints', async () => {
    vi.useFakeTimers();
    const { wrapper } = await mountView();
    const addTable = wrapper
      .findAll('button')
      .find((button) => button.text() === '+ Add table');
    if (!addTable) throw new Error('Add table button was not rendered');
    await addTable.trigger('click');

    const nameInput = wrapper.get('input[placeholder="Table name"]');
    const capacitySelect = wrapper.get('#form-capacity');

    await capacitySelect.setValue('50');
    expect((capacitySelect.element as HTMLSelectElement).value).toBe('50');

    await nameInput.setValue('Unique Table');
    await nameInput.setValue('A'.repeat(101));
    await wrapper.get('#table-management-form').trigger('submit');
    expect(wrapper.text()).toContain('Table name cannot exceed 100 characters');
  });

  it('cancels table creation without saving', async () => {
    vi.useFakeTimers();
    const { wrapper } = await mountView();
    const addTable = wrapper
      .findAll('button')
      .find((button) => button.text() === '+ Add table');
    if (!addTable) throw new Error('Add table button was not rendered');
    await addTable.trigger('click');

    expect(wrapper.text()).toContain('Add table');
    const cancel = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Cancel');
    if (!cancel) throw new Error('Cancel button was not rendered');
    await cancel.trigger('click');

    expect(wrapper.text()).toContain('Table management');
    expect(wrapper.find('h1').text()).toBe('Table management');
    expect(wrapper.find('#table-management-form').exists()).toBe(false);
  });

  it('edits and views an existing table', async () => {
    vi.useFakeTimers();
    const { wrapper } = await mountView();
    const edit = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Edit');
    if (!edit) throw new Error('Edit button was not rendered');
    await edit.trigger('click');

    await wrapper
      .get('input[placeholder="Table name"]')
      .setValue('Table 1 Updated');
    await wrapper.get('#table-management-form').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('Table 1 Updated');

    const view = wrapper
      .findAll('button')
      .find((button) => button.text() === 'View');
    if (!view) throw new Error('View button was not rendered');
    await view.trigger('click');
    expect(wrapper.text()).toContain('Table details');
    const done = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Done');
    if (!done) throw new Error('Done button was not rendered');
    await done.trigger('click');
    expect(wrapper.text()).not.toContain('Table details');
  });

  it('displays accurate status badges for all table statuses', async () => {
    vi.useFakeTimers();
    const { wrapper } = await mountView();

    // Table 1 is Available (green)
    expect(wrapper.find('.bg-\\[\\#e9f5ee\\]').text()).toContain('Available');
    // Table 3 is Reserved (blue)
    expect(wrapper.find('.bg-\\[\\#edf3fb\\]').text()).toContain('Reserved');
    // Table 5 is Occupied (orange)
    expect(wrapper.find('.bg-\\[\\#fff4e9\\]').text()).toContain('Occupied');

    // Switch to Bangkok branch which has an Unavailable table
    const branchSelect = wrapper.get('#panel-branch');
    await branchSelect.setValue('Bangkok');
    await flushPromises();

    expect(wrapper.find('.bg-\\[\\#fdeeed\\]').text()).toContain('Unavailable');
  });

  it('deletes a table only after confirmation', async () => {
    vi.useFakeTimers();
    const confirm = vi.spyOn(window, 'confirm');
    const { wrapper } = await mountView();
    const deleteButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Delete');
    if (!deleteButton) throw new Error('Delete button was not rendered');

    confirm.mockReturnValue(false);
    await deleteButton.trigger('click');
    expect(wrapper.text()).toContain('Table 1');

    confirm.mockReturnValue(true);
    await deleteButton.trigger('click');
    await flushPromises();
    expect(wrapper.text()).not.toContain('Table 1');
  });

  it('navigates through paginated pages', async () => {
    vi.useFakeTimers();
    const { wrapper } = await mountView();

    expect(wrapper.text()).toContain('Showing 1-5 of 20');
    expect(wrapper.text()).toContain('Table 1');

    const nextButton = wrapper.get('button[aria-label="Next page"]');
    await nextButton.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('Showing 6-10 of 20');
    expect(wrapper.text()).toContain('Table 6');

    const prevButton = wrapper.get('button[aria-label="Previous page"]');
    await prevButton.trigger('click');

    expect(wrapper.text()).toContain('Showing 1-5 of 20');
    expect(wrapper.text()).toContain('Table 1');
  });

  it('changes branch and resets filters to the first page', async () => {
    vi.useFakeTimers();
    const { wrapper } = await mountView();
    const selects = wrapper.findAll('select');

    await selects[0].setValue('Silom');
    await flushPromises();
    const rows = () => wrapper.findAll('tbody tr').map((row) => row.text());
    expect(rows()).toContainEqual(expect.stringContaining('Table 21'));
    expect(rows()).not.toContainEqual(expect.stringContaining('Table 1 '));

    await selects[1].setValue('Main Hall');
    await flushPromises();
    expect(rows()).toContainEqual(expect.stringContaining('Table 21'));
    await selects[2].setValue('Reserved');
    await flushPromises();
    expect(wrapper.text()).toContain('No tables match your filters');
  });
});

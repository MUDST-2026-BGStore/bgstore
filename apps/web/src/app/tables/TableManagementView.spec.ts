import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { router } from '../../router';
import TableManagementView from './TableManagementView.vue';

async function mountView() {
  await router.push('/tables');
  await router.isReady();

  const wrapper = mount(TableManagementView, {
    global: {
      plugins: [router],
    },
  });

  await vi.runAllTimersAsync();
  await flushPromises();
  return wrapper;
}

describe('Owner table management screen', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('renders the table management view on the default route', async () => {
    vi.useFakeTimers();
    const wrapper = await mountView();

    expect(wrapper.text()).toContain('Table management');
    expect(wrapper.text()).toContain('Total tables');
    expect(wrapper.text()).toContain('+ Add table');
    expect(wrapper.text()).toContain('Table 1');
  });

  it('shows the loading state before tables are ready', async () => {
    vi.useFakeTimers();
    await router.push('/tables');
    await router.isReady();
    const wrapper = mount(TableManagementView, {
      global: { plugins: [router] },
    });

    expect(wrapper.find('.animate-pulse').exists()).toBe(true);
    await vi.runAllTimersAsync();
    expect(wrapper.find('.animate-pulse').exists()).toBe(false);
  });

  it('filters tables and shows an empty state when nothing matches', async () => {
    vi.useFakeTimers();
    const wrapper = await mountView();
    const search = wrapper.get('input[type="search"]');

    await search.setValue('does not exist');

    expect(wrapper.text()).toContain('No tables match your filters');
    expect(wrapper.find('[aria-label="Table pagination"]').exists()).toBe(
      false,
    );
  });

  it('displays accurate live summary cards for the selected branch', async () => {
    vi.useFakeTimers();
    const wrapper = await mountView();

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

    expect(summaryCards[0].text()).toContain('2');
    expect(summaryCards[1].text()).toContain('1');
    expect(summaryCards[2].text()).toContain('1');
    expect(summaryCards[3].text()).toContain('0');
  });

  it('creates a table after validating the form', async () => {
    vi.useFakeTimers();
    const wrapper = await mountView();
    const addTable = wrapper
      .findAll('button')
      .find((button) => button.text() === '+ Add table');
    if (!addTable) throw new Error('Add table button was not rendered');
    await addTable.trigger('click');

    expect(wrapper.text()).toContain('Add table');
    const save = wrapper.get('button[type="submit"]');
    await save.trigger('click');
    expect(wrapper.text()).toContain('Table name is required');

    await wrapper.get('input[placeholder="Table 5"]').setValue('Table 30');
    await wrapper.get('#form-capacity').setValue('6');
    await save.trigger('click');

    expect(wrapper.text()).toContain('Table 30');
    expect(wrapper.text()).toContain('6 seats');
  });

  it('rejects duplicate table names within the same branch and allows in other branches', async () => {
    vi.useFakeTimers();
    const wrapper = await mountView();
    const addTable = wrapper
      .findAll('button')
      .find((button) => button.text() === '+ Add table');
    if (!addTable) throw new Error('Add table button was not rendered');
    await addTable.trigger('click');

    const nameInput = wrapper.get('input[placeholder="Table 5"]');
    const branchSelect = wrapper.get('#form-branch');
    const save = wrapper.get('button[type="submit"]');

    // Table 1 already exists in Sukhumvit
    await nameInput.setValue('Table 1');
    await branchSelect.setValue('Sukhumvit');
    await save.trigger('click');

    expect(wrapper.text()).toContain(
      'A table with this name already exists in this branch',
    );

    // Case-insensitive duplicate check
    await nameInput.setValue('table 1');
    await save.trigger('click');
    expect(wrapper.text()).toContain(
      'A table with this name already exists in this branch',
    );

    // Switch branch to Bangkok where Table 1 does not exist
    await branchSelect.setValue('Bangkok');
    await save.trigger('click');

    // Form successfully submitted and returned to list
    expect(wrapper.text()).not.toContain(
      'A table with this name already exists in this branch',
    );
  });

  it('supports capacity options and validates name length constraints', async () => {
    vi.useFakeTimers();
    const wrapper = await mountView();
    const addTable = wrapper
      .findAll('button')
      .find((button) => button.text() === '+ Add table');
    if (!addTable) throw new Error('Add table button was not rendered');
    await addTable.trigger('click');

    const nameInput = wrapper.get('input[placeholder="Table 5"]');
    const capacitySelect = wrapper.get('#form-capacity');
    const save = wrapper.get('button[type="submit"]');

    await capacitySelect.setValue('50');
    expect((capacitySelect.element as HTMLSelectElement).value).toBe('50');

    await nameInput.setValue('Unique Table');
    await nameInput.setValue('A'.repeat(51));
    await save.trigger('click');
    expect(wrapper.text()).toContain('Table name cannot exceed 50 characters');
  });

  it('cancels table creation without saving', async () => {
    vi.useFakeTimers();
    const wrapper = await mountView();
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
    const wrapper = await mountView();
    const edit = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Edit');
    if (!edit) throw new Error('Edit button was not rendered');
    await edit.trigger('click');

    await wrapper
      .get('input[placeholder="Table 5"]')
      .setValue('Table 1 Updated');
    await wrapper.get('button[type="submit"]').trigger('click');
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
    const wrapper = await mountView();

    // Table 1 is Available (green)
    expect(wrapper.find('.bg-\\[\\#e9f5ee\\]').text()).toContain('Available');
    // Table 3 is Reserved (blue)
    expect(wrapper.find('.bg-\\[\\#edf3fb\\]').text()).toContain('Reserved');
    // Table 5 is Occupied (orange)
    expect(wrapper.find('.bg-\\[\\#fff4e9\\]').text()).toContain('Occupied');

    // Switch to Bangkok branch which has an Unavailable table
    const branchSelect = wrapper.get('#panel-branch');
    await branchSelect.setValue('Bangkok');

    expect(wrapper.find('.bg-\\[\\#fdeeed\\]').text()).toContain('Unavailable');
  });

  it('deletes a table only after confirmation', async () => {
    vi.useFakeTimers();
    const confirm = vi.spyOn(window, 'confirm');
    const wrapper = await mountView();
    const deleteButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Delete');
    if (!deleteButton) throw new Error('Delete button was not rendered');

    confirm.mockReturnValue(false);
    await deleteButton.trigger('click');
    expect(wrapper.text()).toContain('Table 1');

    confirm.mockReturnValue(true);
    await deleteButton.trigger('click');
    expect(wrapper.text()).not.toContain('Table 1');
  });

  it('navigates through paginated pages', async () => {
    vi.useFakeTimers();
    const wrapper = await mountView();

    expect(wrapper.text()).toContain('Showing 1-5 of 20');
    expect(wrapper.text()).toContain('Table 1');

    const nextButton = wrapper.get('button[aria-label="Next page"]');
    await nextButton.trigger('click');

    expect(wrapper.text()).toContain('Showing 6-10 of 20');
    expect(wrapper.text()).toContain('Table 6');

    const prevButton = wrapper.get('button[aria-label="Previous page"]');
    await prevButton.trigger('click');

    expect(wrapper.text()).toContain('Showing 1-5 of 20');
    expect(wrapper.text()).toContain('Table 1');
  });

  it('changes branch and resets filters to the first page', async () => {
    vi.useFakeTimers();
    const wrapper = await mountView();
    const selects = wrapper.findAll('select');

    await selects[0].setValue('Silom');
    const rows = () => wrapper.findAll('tbody tr').map((row) => row.text());
    expect(rows()).toContainEqual(expect.stringContaining('Table 12'));
    expect(rows()).not.toContainEqual(expect.stringContaining('Table 1 '));

    await selects[1].setValue('Main Hall');
    expect(rows()).toContainEqual(expect.stringContaining('Table 12'));
    await selects[2].setValue('Reserved');
    expect(wrapper.text()).toContain('No tables match your filters');
  });
});

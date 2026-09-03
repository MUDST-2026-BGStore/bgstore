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
    expect(wrapper.text()).toContain('Capacity is required');

    await wrapper.get('input[placeholder="Table 5"]').setValue('Table 30');
    await wrapper.get('input[placeholder="4"]').setValue('6');
    await wrapper.get('select').setValue('Sukhumvit');
    await save.trigger('click');

    expect(wrapper.text()).toContain('Table 30');
    expect(wrapper.text()).toContain('6 seats');
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

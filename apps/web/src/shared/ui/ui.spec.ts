import { mount } from '@vue/test-utils';
import { computed, effectScope, ref } from 'vue';
import { afterEach, expect, it } from 'vitest';
import {
  AppButton,
  AppCheckbox,
  AppChoiceGroup,
  AppDataTable,
  AppField,
  AppNumberStepper,
  AppSelectionBar,
  AppTableToolbar,
  AppCollectionPanel,
  useTableSelection,
} from './index';

let wrapper: ReturnType<typeof mount> | undefined;
const scopes: ReturnType<typeof effectScope>[] = [];
afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
  for (const scope of scopes) scope.stop();
  scopes.length = 0;
});
function selectionFor(ids: string[]) {
  const visible = ref(ids);
  const scope = effectScope();
  scopes.push(scope);
  const selection = scope.run(() =>
    useTableSelection(computed(() => visible.value)),
  );
  if (!selection) throw new Error('Selection scope did not initialize');
  return { visible, ...selection };
}

it('defaults action buttons to non-submitting controls and preserves disabled semantics', async () => {
  wrapper = mount(AppButton, {
    props: { appearance: 'primary', intent: 'danger' },
    slots: { default: 'Remove item' },
  });
  expect(wrapper.get('button').attributes('type')).toBe('button');
  expect(wrapper.get('button').text()).toBe('Remove item');
  await wrapper.setProps({ disabled: true });
  expect((wrapper.get('button').element as HTMLButtonElement).disabled).toBe(
    true,
  );
});

it('keeps action headers screen-reader named while sharing one table structure', () => {
  wrapper = mount(AppDataTable, {
    props: {
      label: 'Games',
      columns: [{ label: 'Game' }, { label: 'Actions', action: true }],
    },
    slots: {
      default: '<tr><td>Azul</td><td><button>Assign</button></td></tr>',
    },
  });
  expect(wrapper.get('caption').text()).toBe('Games');
  expect(wrapper.findAll('thead th')).toHaveLength(2);
  expect(wrapper.findAll('thead th')[1].get('.table-caption').text()).toBe(
    'Actions',
  );
  expect(wrapper.get('tbody').text()).toContain('Azul');
  expect(wrapper.get('[role="region"]').attributes('aria-label')).toBe('Games');
});

it('represents partial selection with a native indeterminate checkbox', async () => {
  wrapper = mount(AppCheckbox, {
    props: {
      label: 'Select visible games',
      modelValue: false,
      indeterminate: true,
    },
  });
  expect((wrapper.get('input').element as HTMLInputElement).indeterminate).toBe(
    true,
  );
  await wrapper.get('input').setValue(true);
  expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([true]);
});

it('selects individual rows, all visible rows, and clears selection', () => {
  const selection = selectionFor(['azul', 'wingspan']);
  selection.toggle('azul', true);
  expect(selection.count.value).toBe(1);
  expect(selection.indeterminate.value).toBe(true);
  selection.toggleAll(true);
  expect([...selection.selectedIds.value]).toEqual(['azul', 'wingspan']);
  expect(selection.allSelected.value).toBe(true);
  expect(selection.indeterminate.value).toBe(false);
  selection.toggle('azul', false);
  expect(selection.count.value).toBe(1);
  selection.clear();
  expect(selection.count.value).toBe(0);
});

it('drops hidden selections when filters change and rejects unknown row IDs', () => {
  const selection = selectionFor(['azul', 'wingspan', 'catan']);
  selection.toggleAll(true);
  selection.visible.value = ['azul'];
  expect([...selection.selectedIds.value]).toEqual(['azul']);
  selection.toggle('wingspan', true);
  expect(selection.count.value).toBe(1);
  selection.visible.value = [];
  expect(selection.count.value).toBe(0);
  expect(selection.allSelected.value).toBe(false);
});

it('shows batch commands only while records are selected', async () => {
  wrapper = mount(AppSelectionBar, {
    props: { count: 0 },
    slots: { default: '<button>Return to shelf</button>' },
  });
  expect(wrapper.get('[role="region"]').attributes('aria-hidden')).toBe('true');
  expect(wrapper.get('[role="region"]').attributes('inert')).toBeDefined();
  await wrapper.setProps({ count: 2 });
  expect(wrapper.get('[role="region"]').attributes('aria-hidden')).toBe(
    'false',
  );
  expect(wrapper.get('[role="region"]').attributes('inert')).toBeUndefined();
  expect(wrapper.get('[role="status"]').text()).toBe('2 selected');
  await wrapper.findAll('button')[1].trigger('click');
  expect(wrapper.emitted('clear')).toHaveLength(1);
});

it('swaps toolbar summaries and normal actions for selection commands without removing either layout', async () => {
  wrapper = mount(AppTableToolbar, {
    props: { resultLabel: '16 games', selectedCount: 0 },
    slots: {
      default: '<input aria-label="Search games">',
      actions: '<button>Add game</button>',
      selection: '<button>Return to shelf</button>',
    },
  });
  const normalAction = wrapper.findAll('button')[0];
  expect(
    normalAction.element.closest('[aria-hidden]')?.getAttribute('aria-hidden'),
  ).toBe('false');
  expect(wrapper.getComponent(AppSelectionBar).attributes('aria-hidden')).toBe(
    'true',
  );
  await wrapper.setProps({ selectedCount: 2 });
  expect(
    normalAction.element.closest('[aria-hidden]')?.getAttribute('aria-hidden'),
  ).toBe('true');
  expect(normalAction.element.closest('[inert]')).not.toBeNull();
  expect(wrapper.get('input').element.closest('[inert]')).toBeNull();
  expect(wrapper.getComponent(AppSelectionBar).attributes('aria-hidden')).toBe(
    'false',
  );
  await wrapper
    .getComponent(AppSelectionBar)
    .findAll('button')[1]
    .trigger('click');
  expect(wrapper.emitted('clearSelection')).toEqual([[]]);
  await wrapper.setProps({ selectedCount: 0 });
  expect(normalAction.element.closest('[inert]')).toBeNull();
});

it('uses one collection shell for metrics, filters, and records without unused selection controls', () => {
  wrapper = mount(AppCollectionPanel, {
    props: {
      title: 'Retail stock',
      titleId: 'stock-title',
      resultLabel: '5 SKUs',
      metrics: [
        { label: 'On hand', value: 29 },
        { label: 'Held', value: 13 },
        { label: 'Available', value: 16 },
        { label: 'SKUs', value: 5 },
      ],
    },
    slots: {
      filters: '<input aria-label="Search stock">',
      default: '<p>Stock records</p>',
    },
  });
  expect(wrapper.get('h2').attributes('id')).toBe('stock-title');
  expect(
    wrapper
      .findAll('[aria-label="Retail stock summary"] strong')
      .map((item) => item.text()),
  ).toEqual(['29', '13', '16', '5']);
  expect(wrapper.get('[aria-label="Table controls"]').text()).toContain(
    '5 SKUs',
  );
  expect(wrapper.findComponent(AppSelectionBar).exists()).toBe(false);
  expect(wrapper.text()).toContain('Stock records');
});

it('clamps integer quantities to their bounds and restores empty input', async () => {
  wrapper = mount(AppNumberStepper, {
    props: { label: 'Guests', modelValue: 2, min: 1, max: 5 },
  });
  await wrapper.get('input').setValue('100');
  expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([5]);
  await wrapper.get('input').setValue('');
  expect((wrapper.get('input').element as HTMLInputElement).value).toBe('2');
  await wrapper.setProps({ modelValue: 5 });
  expect(
    (wrapper.findAll('button')[1].element as HTMLButtonElement).disabled,
  ).toBe(true);
  await wrapper.setProps({ disabled: true });
  expect((wrapper.get('input').element as HTMLInputElement).disabled).toBe(
    true,
  );
});

it('connects reusable field labels to their controls', () => {
  wrapper = mount(AppField, {
    props: { label: 'Guest name', inputId: 'guest-name' },
    slots: { default: '<input id="guest-name" />' },
  });
  expect(wrapper.get('label').attributes('for')).toBe(
    wrapper.get('input').attributes('id'),
  );
});

it('keeps unavailable choices disabled and emits the selected record ID', async () => {
  wrapper = mount(AppChoiceGroup, {
    props: {
      label: 'Table',
      modelValue: '1',
      options: [
        { id: '1', label: 'Table 1' },
        { id: '2', label: 'Table 2', disabled: true },
      ],
    },
  });
  expect(wrapper.findAll('button')[0].attributes('aria-pressed')).toBe('true');
  expect(
    (wrapper.findAll('button')[1].element as HTMLButtonElement).disabled,
  ).toBe(true);
  await wrapper.findAll('button')[0].trigger('click');
  expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['1']);
});

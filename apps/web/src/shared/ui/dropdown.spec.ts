import { mount } from '@vue/test-utils';
import { afterEach, expect, it } from 'vitest';
import AppFilterMenu from './AppFilterMenu.vue';
import type { FilterValues } from './dropdown-types';

let wrapper: ReturnType<typeof mount> | undefined;
afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
});

const groups = [
  {
    id: 'status',
    label: 'Status',
    options: [
      { value: 'low', label: 'Low stock' },
      { value: 'out', label: 'Out of stock' },
    ],
  },
  {
    id: 'category',
    label: 'Category',
    options: [{ value: 'games', label: 'Retail games' }],
  },
];

it('emits independent checkbox choices without mutating the supplied filter values', async () => {
  const values: FilterValues = { status: ['low'], category: ['games'] };
  wrapper = mount(AppFilterMenu, { props: { groups, modelValue: values } });
  expect(wrapper.findAll('label').map((label) => label.text())).toEqual([
    'Low stock',
    'Out of stock',
    'Retail games',
  ]);
  const inputs = wrapper.findAll('input[type="checkbox"]');
  await inputs[1].setValue(true);
  expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([
    { status: ['low', 'out'], category: ['games'] },
  ]);
  expect(values).toEqual({ status: ['low'], category: ['games'] });
  await inputs[0].setValue(false);
  expect(wrapper.emitted('update:modelValue')?.[1]).toEqual([
    { status: [], category: ['games'] },
  ]);
});

it('exposes collapsible labelled groups and a reusable clear-all action', async () => {
  wrapper = mount(AppFilterMenu, {
    props: { groups, modelValue: { status: ['low'] } },
  });
  const status = wrapper
    .findAll('button')
    .find((button) => button.text() === 'Status');
  const category = wrapper
    .findAll('button')
    .find((button) => button.text() === 'Category');
  expect(status?.attributes('aria-expanded')).toBe('true');
  expect(category?.attributes('aria-expanded')).toBe('false');
  await category?.trigger('click');
  expect(category?.attributes('aria-expanded')).toBe('true');
  const clear = wrapper
    .findAll('button')
    .find((button) => button.text() === 'Clear all');
  await clear?.trigger('click');
  expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([{}]);
});

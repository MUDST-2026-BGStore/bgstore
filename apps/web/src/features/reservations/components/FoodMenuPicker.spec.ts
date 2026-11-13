import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import FoodMenuPicker from './FoodMenuPicker.vue';
import type { FoodMenuItem, FoodOrderItem } from '../types';

const cola: FoodMenuItem = {
  id: '018f0000-0000-7000-8000-000000000001',
  name: 'Coca-Cola Original (Can 325ml)',
  description: 'Chilled can.',
  category: 'beverages',
  price: 35,
  popular: true,
  available: 2,
};
const chips: FoodMenuItem = {
  id: '018f0000-0000-7000-8000-000000000002',
  name: "Lay's Classic Rock Salt (Big Bag 73g)",
  description: 'Salted chips.',
  category: 'snacks',
  price: 40,
  available: 0,
};

function mountPicker(modelValue: FoodOrderItem[] = []) {
  return mount(FoodMenuPicker, { props: { modelValue, items: [cola, chips] } });
}

describe('FoodMenuPicker', () => {
  it('shows only the categories the branch menu has', () => {
    const text = mountPicker().text();
    expect(text).toContain('Pre-Order Food & Drinks (10% Discount)');
    expect(text).toContain('Drinks & Soda');
    expect(text).toContain('Packaged Snacks & Chips');
    expect(text).not.toContain('Bakery & Desserts');
  });

  it('filters items by category', async () => {
    const wrapper = mountPicker();
    const drinks = wrapper
      .findAll('.cat-tab')
      .find((tab) => tab.text().includes('Drinks'));
    await drinks?.trigger('click');
    const names = wrapper.findAll('.item-name').map((el) => el.text());
    expect(names).toEqual([cola.name]);
  });

  it('adds items and never beyond what the branch has left', async () => {
    const wrapper = mountPicker([{ item: cola, quantity: 2 }]);
    const plus = wrapper.find('.step-btn.plus');
    expect(plus.attributes('disabled')).toBeDefined();
    await plus.trigger('click');
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();

    const fresh = mountPicker();
    await fresh.find('.step-btn.plus').trigger('click');
    expect(fresh.emitted('update:modelValue')?.[0]?.[0]).toEqual([
      { item: cola, quantity: 1 },
    ]);
  });

  it('marks sold-out items instead of offering them', () => {
    expect(mountPicker().text()).toContain('Sold out');
  });
});

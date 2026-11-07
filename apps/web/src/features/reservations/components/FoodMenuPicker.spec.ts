import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import FoodMenuPicker from './FoodMenuPicker.vue';
import { FOOD_MENU_ITEMS } from '../data/food-menu';
import type { FoodOrderItem } from '../types';

describe('FoodMenuPicker Component', () => {
  it('renders menu categories and discount banner', () => {
    const wrapper = mount(FoodMenuPicker, {
      props: {
        modelValue: [],
      },
    });

    expect(wrapper.text()).toContain('Pre-Order Food & Drinks (10% Discount)');
    expect(wrapper.text()).toContain('All Items');
    expect(wrapper.text()).toContain('Drinks & Soda');
  });

  it('filters items when category tab is selected', async () => {
    const wrapper = mount(FoodMenuPicker, {
      props: {
        modelValue: [],
      },
    });

    const categoryBtns = wrapper.findAll('.cat-tab');
    const beveragesBtn = categoryBtns.find((b) => b.text().includes('Drinks'));
    await beveragesBtn?.trigger('click');

    const cardNames = wrapper.findAll('.item-name').map((el) => el.text());
    expect(cardNames).toContain('Coca-Cola Original (Can 325ml)');
    expect(cardNames).not.toContain("Lay's Classic Rock Salt (Big Bag 73g)");
  });

  it('emits updated order list when adding and incrementing items', async () => {
    const initialItem = FOOD_MENU_ITEMS[0];
    const initialOrder: FoodOrderItem[] = [];

    const wrapper = mount(FoodMenuPicker, {
      props: {
        modelValue: initialOrder,
      },
    });

    const firstAddBtn = wrapper.find('.step-btn.plus');
    await firstAddBtn.trigger('click');

    const emitted = wrapper.emitted('update:modelValue');
    expect(emitted).toBeTruthy();
    const updatedList = emitted![0][0] as FoodOrderItem[];
    expect(updatedList.length).toBe(1);
    expect(updatedList[0].item.id).toBe(initialItem.id);
    expect(updatedList[0].quantity).toBe(1);
  });
});


import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import { createI18n } from 'vue-i18n';
import { messages } from '../../i18n';
import ReservationDatePicker from './ReservationDatePicker.vue';

const mountDatePicker = () =>
  mount(ReservationDatePicker, {
    props: {
      modelValue: '2026-09-12',
      minDate: '2026-09-11',
      maxDate: '2026-11-10',
    },
    global: {
      plugins: [createI18n({ legacy: false, locale: 'en', messages })],
    },
  });

describe('ReservationDatePicker', () => {
  it('opens from the full field and selects an available date', async () => {
    const wrapper = mountDatePicker();

    expect(wrapper.find('.calendar-panel').exists()).toBe(false);
    await wrapper.get('.date-picker-trigger').trigger('click');

    expect(wrapper.get('.calendar-panel').attributes('role')).toBe('dialog');
    expect(wrapper.get('.calendar-header').text()).toContain('September 2026');
    expect(wrapper.get('[data-date="2026-09-10"]').attributes('disabled')).toBe(
      '',
    );

    await wrapper.get('[data-date="2026-09-18"]').trigger('click');

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['2026-09-18']);
    expect(wrapper.find('.calendar-panel').exists()).toBe(false);
  });

  it('moves between months without leaving the allowed range', async () => {
    const wrapper = mountDatePicker();
    await wrapper.get('.date-picker-trigger').trigger('click');

    expect(
      wrapper.get('[aria-label="Previous month"]').attributes('disabled'),
    ).toBe('');
    await wrapper.get('[aria-label="Next month"]').trigger('click');

    expect(wrapper.get('.calendar-header').text()).toContain('October 2026');
    expect(
      wrapper.get('[aria-label="Previous month"]').attributes('disabled'),
    ).toBeUndefined();
  });
});

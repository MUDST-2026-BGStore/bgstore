import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import { messages } from '../i18n';
import ActiveSessionView from './ActiveSessionView.vue';

const mountView = () =>
  mount(ActiveSessionView, {
    global: {
      plugins: [createI18n({ legacy: false, locale: 'en', messages })],
    },
  });

describe('ActiveSessionView', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-11T08:00:00+07:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  it('shows the active client session and fee breakdown', () => {
    const wrapper = mountView();

    expect(wrapper.get('h1').text()).toBe('Current Session Details');
    expect(wrapper.text()).not.toContain('Your current visit');
    expect(wrapper.get('.elapsed-time').text()).toBe('01:45:00');
    expect(wrapper.get('.fee').text()).toContain('250.00');
    expect(wrapper.get('.fee-card').text()).toContain('2 people');
    expect(wrapper.get('.fee-card').text()).toContain('THB 50 / person');

    wrapper.unmount();
  });

  it('updates the local elapsed-time display every second', async () => {
    const wrapper = mountView();

    await vi.advanceTimersByTimeAsync(1000);

    expect(wrapper.get('.elapsed-time').text()).toBe('01:45:01');
    wrapper.unmount();
  });

  it('supports the temporary call-staff and end-playing interactions', async () => {
    const wrapper = mountView();

    await wrapper.get('.primary-action').trigger('click');
    expect(wrapper.get('.action-notice').text()).toContain(
      'staff-call interaction is ready',
    );

    await wrapper.get('.secondary-action').trigger('click');
    expect(wrapper.get('[role="dialog"]').text()).toContain(
      'End this session?',
    );
    await wrapper.get('.confirm-end').trigger('click');
    expect(wrapper.get('.action-notice').text()).toContain(
      'end-session interaction is ready',
    );

    wrapper.unmount();
  });
});

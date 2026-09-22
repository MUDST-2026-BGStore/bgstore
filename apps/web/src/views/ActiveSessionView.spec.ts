import { flushPromises } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderScreen, route, stubApi } from '../test/api-stub';
import ActiveSessionView from './ActiveSessionView.vue';

const SESSION = {
  reservationId: 'res-1',
  locationName: 'Central — Rama I Road, Pathum Wan',
  tableName: 'Table 12',
  // 105 minutes before the frozen clock below.
  startedAt: '2026-09-10T23:15:00Z',
  partySize: 2,
  ratePerHour: 50,
  accruedAmount: 250,
  currency: 'THB',
};

const sessionHandler = route('/me/active-session', {
  body: SESSION,
});

describe('ActiveSessionView', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-11T08:00:00+07:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('renders the checked-in session the API returned', async () => {
    stubApi([sessionHandler]);

    const { wrapper } = await renderScreen(ActiveSessionView, '/sessions/active');

    expect(wrapper.get('h1').text()).toBe('Current Session Details');
    expect(wrapper.get('.elapsed-time').text()).toBe('01:45:00');
    expect(wrapper.text()).toContain('Table 12');
    expect(wrapper.get('.fee').text()).toContain('250.00');
    // Intl formats THB with a non-breaking space before the amount.
    const feeCard = wrapper
      .get('.fee-card')
      .text()
      .replaceAll(String.fromCharCode(160), ' ');
    expect(feeCard).toContain('2 people');
    expect(feeCard).toContain('THB 50 / hour');

    wrapper.unmount();
  });

  it('updates the local elapsed-time display every second', async () => {
    stubApi([sessionHandler]);

    const { wrapper } = await renderScreen(ActiveSessionView, '/sessions/active');
    await vi.advanceTimersByTimeAsync(1000);

    expect(wrapper.get('.elapsed-time').text()).toBe('01:45:01');
    wrapper.unmount();
  });

  it('shows an empty state when the client has no checked-in session', async () => {
    stubApi([route('/me/active-session', { status: 404, body: { status: 404 } })]);

    const { wrapper } = await renderScreen(ActiveSessionView, '/sessions/active');

    expect(wrapper.text()).toContain('No active session');
    expect(wrapper.find('.session-layout').exists()).toBe(false);
    wrapper.unmount();
  });

  it('sends a real staff request and confirms it to the client', async () => {
    const calls = stubApi([
      sessionHandler,
      route('/reservations/res-1/assistance', { body: {} }, 'POST'),
    ]);

    const { wrapper } = await renderScreen(ActiveSessionView, '/sessions/active');
    await wrapper.get('.primary-action').trigger('click');
    await flushPromises();

    const request = calls.find((call) => call.url.includes('/assistance'));
    expect(request?.method).toBe('POST');
    expect(JSON.parse(request?.body ?? '{}')).toMatchObject({
      kind: 'CallStaff',
    });
    expect(wrapper.get('.action-notice').text()).toContain(
      'asked to come to your table',
    );
    wrapper.unmount();
  });

  it('records the end-of-play request after the client confirms', async () => {
    const calls = stubApi([
      sessionHandler,
      route('/reservations/res-1/assistance', { body: {} }, 'POST'),
    ]);

    const { wrapper } = await renderScreen(ActiveSessionView, '/sessions/active');
    await wrapper.get('.secondary-action').trigger('click');
    expect(wrapper.get('[role="dialog"]').text()).toContain('End this session?');

    await wrapper.get('.confirm-end').trigger('click');
    await flushPromises();

    const request = calls.find((call) => call.url.includes('/assistance'));
    expect(JSON.parse(request?.body ?? '{}')).toMatchObject({
      kind: 'EndPlaying',
    });
    expect(wrapper.get('.action-notice').text()).toContain(
      'confirm the final charge',
    );
    wrapper.unmount();
  });
});

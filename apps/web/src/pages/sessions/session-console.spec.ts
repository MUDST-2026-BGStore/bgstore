import { flushPromises } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderScreen, route, stubApi } from '../../test/api-stub';
import SessionConsolePage from './SessionConsolePage.vue';

const SESSIONS = {
  items: [
    {
      id: 'res-waiting',
      title: 'Table 5',
      date: '2026-09-23',
      timeSlot: '09:00–12:00',
      partySize: 5,
      tableId: 5,
      tableName: 'Table 5',
      seats: 6,
      ratePerHour: 120,
      status: 'Reserved',
      customerName: 'Jane Doe',
      phoneNumber: '0123456789',
      checkInTime: '-',
      actualCheckOut: '-',
      overtimeMinutes: 0,
      totalPrice: 0,
      canCancel: true,
    },
    {
      id: 'res-playing',
      title: 'Table 8',
      date: '2026-09-23',
      timeSlot: '10:00–13:00',
      partySize: 4,
      tableId: 8,
      tableName: 'Table 8',
      seats: 8,
      ratePerHour: 150,
      status: 'CheckedIn',
      customerName: 'Somchai P.',
      phoneNumber: '0898765432',
      checkInTime: '2026-09-23T03:00:00Z',
      actualCheckOut: '-',
      overtimeMinutes: 0,
      totalPrice: 0,
      canCancel: false,
    },
  ],
};

const sessionsHandler = route('/sessions', { body: SESSIONS });

const receipt = (overrides: Record<string, unknown> = {}) => ({
  reservationId: 'res-playing',
  tableName: 'Table 8',
  partySize: 4,
  startedAt: '2026-09-23T03:00:00Z',
  endedAt: '2026-09-23T06:00:00Z',
  hours: 3,
  ratePerHour: 150,
  totalDue: 240,
  currency: 'THB',
  paymentMethod: 'PromptPay',
  paidAt: '2026-09-23T06:00:00Z',
  payment: {
    gateway: 'bogus',
    reference: 'bogus-ref-123',
    paidAt: '2026-09-23T06:00:00Z',
  },
  ...overrides,
});

const mountConsole = (options: Parameters<typeof renderScreen>[2] = {}) =>
  renderScreen(SessionConsolePage, '/staff/sessions', options);

describe('SessionConsolePage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('lists the reservations staff can act on', async () => {
    stubApi([sessionsHandler]);

    const { wrapper } = await mountConsole();

    expect(wrapper.get('h1').text()).toBe('Session console');
    const rows = wrapper.findAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[0].text()).toContain('Table 5');
    expect(rows[0].text()).toContain('Waiting to start');
    expect(rows[1].text()).toContain('In play');
    wrapper.unmount();
  });

  it('shows the empty state when nothing is waiting', async () => {
    stubApi([route('/sessions', { body: { items: [] } })]);

    const { wrapper } = await mountConsole();

    expect(wrapper.text()).toContain('Nothing is waiting to start');
    expect(wrapper.find('table').exists()).toBe(false);
    wrapper.unmount();
  });

  it('checks a waiting reservation in', async () => {
    const calls = stubApi([
      sessionsHandler,
      route('/reservations/res-waiting/check-in', { body: {} }, 'POST'),
    ]);

    const { wrapper } = await mountConsole();
    await wrapper.findAll('tbody tr')[0].get('button').trigger('click');
    await flushPromises();

    const request = calls.find((call) => call.url.includes('/check-in'));
    expect(request?.method).toBe('POST');
    wrapper.unmount();
  });

  it('closes a session and confirms the gateway settlement', async () => {
    const calls = stubApi([
      sessionsHandler,
      route('/reservations/res-playing/check-out', { body: receipt() }, 'POST'),
    ]);

    const { wrapper } = await mountConsole({ attachTo: document.body });
    await wrapper.findAll('tbody tr')[1].get('button').trigger('click');

    expect(wrapper.get('[role="dialog"]').text()).toContain(
      'Close the session',
    );
    await wrapper.get('#checkout-amount').setValue('240');
    await wrapper.get('#checkout-method').setValue('PromptPay');
    // The demo gateway shows a decorative PromptPay QR while selected.
    expect(wrapper.get('form').find('img[src*="promptpay"]').exists()).toBe(
      true,
    );
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    const request = calls.find((call) => call.url.includes('/check-out'));
    expect(request?.method).toBe('POST');
    expect(JSON.parse(request?.body ?? '{}')).toEqual({
      finalAmount: 240,
      paymentMethod: 'PromptPay',
    });

    // The dialog stays open so staff can read the settlement to the guest.
    const dialog = wrapper.get('[role="dialog"]');
    expect(dialog.text()).toContain('Session closed');
    expect(dialog.text()).toContain('240.00');
    expect(dialog.text()).toContain('QR Code / PromptPay');
    expect(dialog.text()).toContain('bogus');
    expect(dialog.text()).toContain('bogus-ref-123');
    expect(document.activeElement?.textContent).toContain('Done');

    await dialog.get('button').trigger('click');
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('charges a card through the bogus gateway and prints it on the receipt', async () => {
    const calls = stubApi([
      sessionsHandler,
      route(
        '/reservations/res-playing/check-out',
        {
          body: receipt({
            paymentMethod: 'Card',
            payment: {
              gateway: 'bogus',
              reference: 'bogus-ref-123',
              paidAt: '2026-09-23T06:00:00Z',
              card: { brand: 'Visa', last4: '4242' },
            },
          }),
        },
        'POST',
      ),
    ]);

    const { wrapper } = await mountConsole();
    await wrapper.findAll('tbody tr')[1].get('button').trigger('click');
    await wrapper.get('#checkout-amount').setValue('240');
    await wrapper.get('#checkout-method').setValue('Card');

    const form = wrapper.get('form');
    // With no digits yet the network is unknown; the logo follows the typed
    // leading digits.
    expect(form.find('img[src*="unknown"]').exists()).toBe(true);

    await wrapper.get('#checkout-card-number').setValue('4242424242424242');
    expect(form.find('img[src*="visa"]').exists()).toBe(true);
    await wrapper.get('#checkout-card-expiry').setValue('12/29');
    await wrapper.get('#checkout-card-cvv').setValue('123');
    await form.trigger('submit');
    await flushPromises();

    const request = calls.find((call) => call.url.includes('/check-out'));
    expect(request?.method).toBe('POST');
    expect(JSON.parse(request?.body ?? '{}')).toEqual({
      finalAmount: 240,
      paymentMethod: 'Card',
      card: {
        number: '4242424242424242',
        cvv: '123',
        expiryMonth: 12,
        expiryYear: 2029,
      },
    });

    const dialog = wrapper.get('[role="dialog"]');
    expect(dialog.text()).toContain('Session closed');
    expect(dialog.text()).toContain('•••• 4242');
    wrapper.unmount();
  });

  it('holds the card charge until the entry passes the local checks', async () => {
    const calls = stubApi([
      sessionsHandler,
      route(
        '/reservations/res-playing/check-out',
        {
          body: receipt({
            paymentMethod: 'Card',
            payment: {
              gateway: 'bogus',
              reference: 'bogus-ref-123',
              paidAt: '2026-09-23T06:00:00Z',
              card: { brand: 'Amex', last4: '0005' },
            },
          }),
        },
        'POST',
      ),
    ]);

    const { wrapper } = await mountConsole();
    await wrapper.findAll('tbody tr')[1].get('button').trigger('click');
    await wrapper.get('#checkout-amount').setValue('240');
    await wrapper.get('#checkout-method').setValue('Card');

    const form = wrapper.get('form');
    const submit = form.findAll('button')[1];
    const number = wrapper.get<HTMLInputElement>('#checkout-card-number');
    const expiry = wrapper.get<HTMLInputElement>('#checkout-card-expiry');
    const cvv = wrapper.get<HTMLInputElement>('#checkout-card-cvv');

    expect(submit.attributes('disabled')).toBeDefined();

    // Luhn-failing number never arms the charge.
    await number.setValue('4242424242424241');
    await expiry.setValue('12/29');
    await cvv.setValue('123');
    expect(submit.attributes('disabled')).toBeDefined();

    // An Amex needs four security digits.
    await number.setValue('378282246310005');
    await cvv.setValue('123');
    expect(submit.attributes('disabled')).toBeDefined();

    await cvv.setValue('1234');
    expect(submit.attributes('disabled')).toBeUndefined();

    await form.trigger('submit');
    await flushPromises();

    const request = calls.find((call) => call.url.includes('/check-out'));
    expect(JSON.parse(request?.body ?? '{}').card).toEqual({
      number: '378282246310005',
      cvv: '1234',
      expiryMonth: 12,
      expiryYear: 2029,
    });
    wrapper.unmount();
  });

  it('names the card-network decline reason instead of a generic failure', async () => {
    stubApi([
      sessionsHandler,
      route(
        '/reservations/res-playing/check-out',
        {
          status: 402,
          body: {
            code: 'insufficient_funds',
            detail: 'The card network declined the charge.',
          },
        },
        'POST',
      ),
    ]);

    const { wrapper } = await mountConsole();
    await wrapper.findAll('tbody tr')[1].get('button').trigger('click');
    await wrapper.get('#checkout-amount').setValue('240');
    await wrapper.get('#checkout-method').setValue('Card');
    await wrapper.get('#checkout-card-number').setValue('4242424242424242');
    await wrapper.get('#checkout-card-expiry').setValue('12/29');
    await wrapper.get('#checkout-card-cvv').setValue('111');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'insufficient funds',
    );
    // The confirmed card is still on the form for the retry.
    expect(
      wrapper.get<HTMLInputElement>('#checkout-card-number').element.value,
    ).toBe('4242 4242 4242 4242');
    wrapper.unmount();
  });

  it('keeps the checkout open when the gateway fails the charge', async () => {
    stubApi([
      sessionsHandler,
      route(
        '/reservations/res-playing/check-out',
        {
          status: 502,
          body: {
            code: 'gateway_unavailable',
            detail:
              'The payment gateway could not process the charge; please retry.',
          },
        },
        'POST',
      ),
    ]);

    const { wrapper } = await mountConsole();
    await wrapper.findAll('tbody tr')[1].get('button').trigger('click');
    await wrapper.get('#checkout-amount').setValue('240');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'temporarily unavailable',
    );
    // The confirmed amount is still on the form for the retry.
    expect(
      wrapper.get<HTMLInputElement>('#checkout-amount').element.value,
    ).toBe('240');
    wrapper.unmount();
  });

  it('refuses a settled amount that is not positive', async () => {
    const calls = stubApi([
      sessionsHandler,
      route('/reservations/res-playing/check-out', { body: {} }, 'POST'),
    ]);

    const { wrapper } = await mountConsole();
    await wrapper.findAll('tbody tr')[1].get('button').trigger('click');
    await wrapper.get('#checkout-amount').setValue('0');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'must be greater than 0',
    );
    expect(calls.some((call) => call.url.includes('/check-out'))).toBe(false);
    wrapper.unmount();
  });

  it('waives the fee without a charge', async () => {
    const calls = stubApi([
      sessionsHandler,
      route(
        '/reservations/res-playing/check-out',
        { body: receipt({ paymentMethod: 'Waived', payment: undefined }) },
        'POST',
      ),
    ]);

    const { wrapper } = await mountConsole();
    await wrapper.findAll('tbody tr')[1].get('button').trigger('click');
    await wrapper.get('#checkout-method').setValue('Waived');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    const request = calls.find((call) => call.url.includes('/check-out'));
    expect(JSON.parse(request?.body ?? '{}')).toEqual({
      finalAmount: 0,
      paymentMethod: 'Waived',
    });

    const dialog = wrapper.get('[role="dialog"]');
    expect(dialog.text()).toContain('Session closed');
    expect(dialog.text()).toContain('without a charge');
    expect(dialog.text()).not.toContain('bogus-ref-123');
    wrapper.unmount();
  });
});

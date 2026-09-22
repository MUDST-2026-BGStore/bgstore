import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { createI18n } from 'vue-i18n';
import { createMemoryHistory, createRouter } from 'vue-router';
import { messages } from '../i18n';
import { routes } from '../router';
import CreateReservationView from './CreateReservationView.vue';
import { route, stubApi } from '../test/api-stub';

const mountReservation = async (query = '') => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createRouter({ history: createMemoryHistory(), routes });
  await router.push(`/reservations/new${query}`);
  await router.isReady();
  const wrapper = mount(CreateReservationView, {
    global: {
      plugins: [
        router,
        createI18n({ legacy: false, locale: 'en', messages }),
        [VueQueryPlugin, { queryClient }],
      ],
    },
  });
  await flushPromises();
  return wrapper;
};

const completeClientStep = async (
  wrapper: Awaited<ReturnType<typeof mountReservation>>,
) => {
  await wrapper.get('#reservation-first-name').setValue('Jane');
  await wrapper.get('#reservation-last-name').setValue('Doe');
  await wrapper.get('#reservation-nickname').setValue('Janie');
  await wrapper.get('#reservation-phone').setValue('088-888-8888');
  await wrapper.get('form').trigger('submit');
};

const tableButton = (
  wrapper: Awaited<ReturnType<typeof mountReservation>>,
  number: number,
) => {
  const button = wrapper
    .findAll('.table-option')
    .find((candidate) => candidate.text().includes(`Table ${number}`));
  if (!button) {
    throw new Error(`Table ${number} was not rendered`);
  }
  return button;
};

describe('CreateReservationView', () => {
  beforeEach(() => {
    stubApi([
      route('/branches', {
        body: { items: [{ id: 'central', name: 'Central Rama II' }] },
      }),
      route('/me', {
        body: {
          subject: 'staff',
          username: 'staff',
          email: 'staff@example.test',
          firstName: 'Staff',
          lastName: 'User',
          roles: ['STAFF'],
          onboardingRequired: false,
        },
      }),
      route('/clients', {
        body: {
          items: [
            {
              subject: 'client-somsri',
              displayName: 'Somsri Jaidee · 089-555-0123',
              firstName: 'Somsri',
              lastName: 'Jaidee',
              phone: '089-555-0123',
            },
          ],
        },
      }),
      route('/reservations/availability', {
        body: {
          tables: [
            { id: 3, name: 'Table 3', capacity: 4, available: false },
            { id: 6, name: 'Table 6', capacity: 6, available: true },
            { id: 10, name: 'Table 10', capacity: 4, available: false },
          ],
        },
      }),
      route('/reservations', { status: 201, body: { id: 'created' } }, 'POST'),
    ]);
  });
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('starts with the staff client and branch fields', async () => {
    const wrapper = await mountReservation();

    expect(wrapper.get('h1').text()).toBe('Create reservation');
    expect(wrapper.findAll('.reservation-stepper li')).toHaveLength(4);
    expect(wrapper.get('[aria-current="step"]').text()).toContain('Client');
    expect(
      wrapper.get('#reservation-client-search').attributes('placeholder'),
    ).toBe('Search by name or phone number');
    expect(
      (wrapper.get('#reservation-branch').element as HTMLSelectElement).value,
    ).toBe('Central Rama II');
    expect(wrapper.get('.primary-button').attributes('disabled')).toBeDefined();
  });

  it('preselects the branch the directory screen linked with', async () => {
    stubApi([
      route('/branches', {
        body: {
          items: [
            { id: 'central', name: 'Central Rama II' },
            { id: 'silom', name: 'Silom' },
          ],
        },
      }),
      route('/me', {
        body: {
          subject: 'staff',
          username: 'staff',
          email: 'staff@example.test',
          firstName: 'Staff',
          lastName: 'User',
          roles: ['STAFF'],
          onboardingRequired: false,
        },
      }),
      route('/clients', { body: { items: [] } }),
      route('/reservations/availability', { body: { tables: [] } }),
    ]);

    const wrapper = await mountReservation('?branch=Silom');

    expect(
      (wrapper.get('#reservation-branch').element as HTMLSelectElement).value,
    ).toBe('Silom');
  });

  it('completes all four staff reservation steps', async () => {
    const wrapper = await mountReservation();

    await completeClientStep(wrapper);
    expect(wrapper.get('[aria-current="step"]').text()).toContain(
      'Time & Party',
    );

    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[aria-current="step"]').text()).toContain('Table');

    expect(tableButton(wrapper, 3).attributes('disabled')).toBeDefined();
    expect(tableButton(wrapper, 10).attributes('disabled')).toBeDefined();

    await tableButton(wrapper, 6).trigger('click');
    expect(wrapper.get('.selected-card').text()).toContain('Table 6');
    await wrapper.get('.primary-button').trigger('click');

    expect(wrapper.get('[aria-current="step"]').text()).toContain('Confirm');
    expect(wrapper.get('.confirmation-list').text()).toContain(
      'Jane Doe (Janie)',
    );
    expect(wrapper.get('.confirmation-list').text()).toContain(
      'Central Rama II',
    );
    expect(wrapper.get('.confirmation-list').text()).toContain('Table 6');
    expect(wrapper.get('.confirmation-list').text()).toContain('5 guests');

    await wrapper.get('.primary-button').trigger('click');
    await flushPromises();
    expect(wrapper.get('.reservation-success').text()).toContain(
      'Reservation details ready',
    );
  });

  it('keeps the staff draft when moving back through the flow', async () => {
    const wrapper = await mountReservation();

    await completeClientStep(wrapper);
    await wrapper.get('[aria-label="Increase party size"]').trigger('click');
    await wrapper.get('form').trigger('submit');
    await wrapper.get('.secondary-button').trigger('click');

    expect(wrapper.get('[aria-pressed="true"]').text()).toBe('6');
    await wrapper.get('.secondary-button').trigger('click');
    expect(
      (wrapper.get('#reservation-first-name').element as HTMLInputElement)
        .value,
    ).toBe('Jane');
    expect(
      (wrapper.get('#reservation-phone').element as HTMLInputElement).value,
    ).toBe('088-888-8888');
  });

  it('fills known client details when staff selects a search result', async () => {
    const wrapper = await mountReservation();

    await wrapper
      .get('#reservation-client-search')
      .setValue('Somsri Jaidee · 089-555-0123');

    expect(
      (wrapper.get('#reservation-first-name').element as HTMLInputElement)
        .value,
    ).toBe('Somsri');
    expect(
      (wrapper.get('#reservation-last-name').element as HTMLInputElement).value,
    ).toBe('Jaidee');
    expect(
      (wrapper.get('#reservation-phone').element as HTMLInputElement).value,
    ).toBe('089-555-0123');
  });
});

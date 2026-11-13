import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { configureApiTransport } from '@theparlor/api-client';
import { createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { provideManagementWorkspace } from '../context';
import WorkspaceSettings from './WorkspaceSettings.vue';

const BRANCH_ID = '00000000-0000-4000-8000-000000000001';

describe('WorkspaceSettings', () => {
  let client: QueryClient;

  beforeEach(() => {
    configureApiTransport({ baseUrl: 'http://localhost' });
    client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
  });

  afterEach(() => {
    client.clear();
    configureApiTransport({});
    vi.unstubAllGlobals();
  });

  function mountSettings(onPatch: (body: Record<string, unknown>) => Response) {
    const patchCalls: Record<string, unknown>[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (input: Request) => {
        const { pathname } = new URL(input.url);
        if (pathname === '/api/auth/csrf') {
          return Response.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'csrf-token',
          });
        }
        if (pathname === '/api/v1/branches' && input.method === 'GET') {
          return Response.json({
            items: [
              {
                id: BRANCH_ID,
                name: 'The Parlor · Siam',
                address: '991 Rama I Rd, Pathum Wan',
                phone: '+6621234567',
                openingHours: [
                  {
                    dayOfWeek: 'MONDAY',
                    opensAt: '10:00',
                    closesAt: '22:00',
                  },
                ],
                active: true,
                fulfillsDelivery: true,
              },
            ],
          });
        }
        if (
          pathname === `/api/v1/branches/${BRANCH_ID}` &&
          input.method === 'PATCH'
        ) {
          const parsed = (await input.json()) as Record<string, unknown>;
          patchCalls.push(parsed);
          return onPatch(parsed);
        }
        return Response.json({ items: [] });
      }),
    );

    const Harness = defineComponent({
      setup() {
        provideManagementWorkspace();
        return () => h(WorkspaceSettings);
      },
    });

    const wrapper = mount(Harness, {
      global: {
        plugins: [createPinia(), [VueQueryPlugin, { queryClient: client }]],
      },
      attachTo: document.body,
    });

    return { wrapper, patchCalls };
  }

  it('prefills fields from the selected branch and sends only changed name on save', async () => {
    const { wrapper, patchCalls } = mountSettings((body) =>
      Response.json({
        id: BRANCH_ID,
        name: body.name ?? 'The Parlor · Siam',
        address: '991 Rama I Rd, Pathum Wan',
        phone: '+6621234567',
        openingHours: [
          { dayOfWeek: 'MONDAY', opensAt: '10:00', closesAt: '22:00' },
        ],
        active: true,
        fulfillsDelivery: true,
      }),
    );

    await vi.waitFor(() =>
      expect(
        (wrapper.get('#venue-name').element as HTMLInputElement).value,
      ).toBe('The Parlor · Siam'),
    );
    expect(
      (wrapper.get('#venue-address').element as HTMLInputElement).value,
    ).toBe('991 Rama I Rd, Pathum Wan');
    expect(
      (wrapper.get('#venue-phone').element as HTMLInputElement).value,
    ).toBe('+6621234567');
    expect(wrapper.findAll('[data-testid="opening-period-row"]')).toHaveLength(
      1,
    );

    const saveButton = wrapper.get('button[type="submit"]');
    expect(saveButton.attributes('disabled')).toBeDefined();

    await wrapper.get('#venue-name').setValue('The Parlor · Siam Paragon');
    expect(saveButton.attributes('disabled')).toBeUndefined();

    await wrapper.get('form').trigger('submit.prevent');
    await vi.waitFor(() =>
      expect(patchCalls).toEqual([{ name: 'The Parlor · Siam Paragon' }]),
    );
    wrapper.unmount();
  });

  it('shows the server message on 409 and keeps edits', async () => {
    const { wrapper } = mountSettings(() =>
      Response.json(
        {
          status: 409,
          code: 'CONFLICT',
          message: 'New opening hours exclude an existing reservation.',
        },
        { status: 409 },
      ),
    );

    await vi.waitFor(() =>
      expect(
        (wrapper.get('#venue-name').element as HTMLInputElement).value,
      ).toBe('The Parlor · Siam'),
    );
    await wrapper.get('#venue-name').setValue('Edited Branch Name');
    await wrapper.get('form').trigger('submit.prevent');
    await vi.waitFor(() =>
      expect(wrapper.get('[role="alert"]').text()).toContain(
        'New opening hours exclude an existing reservation.',
      ),
    );
    expect((wrapper.get('#venue-name').element as HTMLInputElement).value).toBe(
      'Edited Branch Name',
    );
    wrapper.unmount();
  });

  it('sends 24:00 when Until midnight is checked', async () => {
    const { wrapper, patchCalls } = mountSettings((body) =>
      Response.json({
        id: BRANCH_ID,
        name: 'The Parlor · Siam',
        address: '991 Rama I Rd, Pathum Wan',
        phone: '+6621234567',
        openingHours: body.openingHours,
        active: true,
        fulfillsDelivery: true,
      }),
    );

    await vi.waitFor(() =>
      expect(
        wrapper.findAll('[data-testid="opening-period-row"]'),
      ).toHaveLength(1),
    );
    const checkbox = wrapper.get(
      'input[type="checkbox"][aria-label="Until midnight"]',
    );
    await checkbox.setValue(true);

    await wrapper.get('form').trigger('submit.prevent');
    await vi.waitFor(() =>
      expect(patchCalls).toEqual([
        {
          openingHours: [
            {
              dayOfWeek: 'MONDAY',
              opensAt: '10:00',
              closesAt: '24:00',
            },
          ],
        },
      ]),
    );
    wrapper.unmount();
  });
});

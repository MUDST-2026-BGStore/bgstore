import { mount } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { configureApiTransport } from '@theparlor/api-client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import WorkspaceAdministration from './WorkspaceAdministration.vue';

let queryClient: QueryClient;
let wrapper: ReturnType<typeof mount> | undefined;

beforeEach(() => {
  HTMLDialogElement.prototype.showModal ??= function () {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close ??= function () {
    this.removeAttribute('open');
  };
  configureApiTransport({ baseUrl: 'http://localhost' });
  queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
});

afterEach(() => {
  wrapper?.unmount();
  queryClient.clear();
  configureApiTransport({});
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

function renderAdmin() {
  wrapper = mount(WorkspaceAdministration, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: { RouterLink: true },
    },
  });
  return wrapper;
}

it('lists branch assignments, searches onboarded accounts, grants and revokes', async () => {
  const assignments = [
    {
      staffSubject: 'staff-1',
      name: 'Krit Somchai',
      phone: '+66812345678',
      grantedBy: 'admin-1',
      grantedAt: '2026-01-05T03:00:00Z',
    },
  ];
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async (input: Request) => {
      const url = new URL(input.url);
      const path = url.pathname;
      if (path === '/api/auth/csrf') {
        return Response.json({
          token: 'csrf',
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
        });
      }
      if (path === '/api/v1/secure/whoami') {
        return Response.json({ subject: 'admin-1', roles: ['admin'] });
      }
      if (path === '/api/v1/branches') {
        return Response.json({ items: [{ id: 'branch-1', name: 'Siam' }] });
      }
      if (
        input.method === 'GET' &&
        path === '/api/v1/branches/branch-1/staff-assignments'
      ) {
        return Response.json({ items: [...assignments] });
      }
      if (path === '/api/v1/admin/accounts') {
        return Response.json({
          items: [
            {
              subject: 'staff-2',
              name: 'Natt Worapat',
              phone: '+66899990000',
            },
          ],
        });
      }
      if (
        input.method === 'PUT' &&
        path === '/api/v1/admin/branches/branch-1/staff-assignments/staff-2'
      ) {
        const created = {
          staffSubject: 'staff-2',
          name: 'Natt Worapat',
          phone: '+66899990000',
          grantedBy: 'admin-1',
          grantedAt: '2026-01-06T03:00:00Z',
        };
        assignments.push(created);
        return Response.json(created);
      }
      if (
        input.method === 'DELETE' &&
        path === '/api/v1/admin/branches/branch-1/staff-assignments/staff-1'
      ) {
        assignments.splice(0, 1);
        return new Response(null, { status: 204 });
      }
      return new Response('Not Found', { status: 404 });
    }),
  );

  const page = renderAdmin();
  await vi.waitFor(() => expect(page.text()).toContain('Krit Somchai'));
  expect(page.text()).toContain(
    'Branch access applies to accounts with the staff role.',
  );

  await page.get('input[type="search"]').setValue('Natt');

  await vi.waitFor(() => expect(page.text()).toContain('Natt Worapat'), {
    timeout: 2000,
  });
  await page.get('button[aria-label="Assign Natt Worapat"]').trigger('click');
  await vi.waitFor(
    () => expect(page.get('table').text()).toContain('Natt Worapat'),
    { timeout: 2000 },
  );

  await page.get('button[aria-label="Remove Krit Somchai"]').trigger('click');
  const dialog = page.find('dialog');
  expect(dialog.exists()).toBe(true);
  const confirmBtn = dialog
    .findAll('button')
    .find((b) => b.text().trim() === 'Remove');
  await confirmBtn!.trigger('click');

  await vi.waitFor(() =>
    expect(page.get('table').text()).not.toContain('Krit Somchai'),
  );
});

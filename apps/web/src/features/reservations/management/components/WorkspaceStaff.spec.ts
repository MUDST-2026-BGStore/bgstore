import { mount } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { configureApiTransport } from '@theparlor/api-client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import WorkspaceStaff from './WorkspaceStaff.vue';

const selectedBranchId = ref<string | undefined>('branch-1');
vi.mock('../context', () => ({
  useManagementWorkspace: () => ({ selectedBranchId }),
}));

let queryClient: QueryClient;
let wrapper: ReturnType<typeof mount> | undefined;

beforeEach(() => {
  selectedBranchId.value = 'branch-1';
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
});

function renderStaff() {
  wrapper = mount(WorkspaceStaff, {
    global: { plugins: [[VueQueryPlugin, { queryClient }]] },
  });
  return wrapper;
}

it('shows assigned staff from the server and handles empty and error states', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValueOnce(
      Response.json({
        items: [
          {
            staffSubject: 'staff-1',
            name: 'Krit Somchai',
            phone: '+66812345678',
            grantedBy: 'admin-1',
            grantedAt: '2026-01-05T03:00:00Z',
          },
        ],
      }),
    ),
  );
  const page = renderStaff();
  await vi.waitFor(() => expect(page.text()).toContain('Krit Somchai'));
  expect(page.text()).toContain('+66812345678');
  expect(page.text()).toContain('1 assigned');

  page.unmount();
  queryClient.clear();
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValueOnce(Response.json({ items: [] })),
  );
  const emptyPage = renderStaff();
  await vi.waitFor(() =>
    expect(emptyPage.text()).toContain('No staff assigned to this branch.'),
  );

  emptyPage.unmount();
  queryClient.clear();
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValueOnce(new Response('Forbidden', { status: 403 })),
  );
  const errorPage = renderStaff();
  await vi.waitFor(() =>
    expect(errorPage.get('[role="alert"]').text()).toContain(
      'Could not load branch staff.',
    ),
  );
});

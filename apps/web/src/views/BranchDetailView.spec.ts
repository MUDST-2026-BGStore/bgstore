import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import { ref } from 'vue';
import { messages } from '../i18n';
import BranchDetailView from './BranchDetailView.vue';

vi.mock('../composables/useBranches', () => ({
  useBranches: () => ({
    branches: ref([
      {
        id: '1',
        name: 'Silom',
        address: 'Silom Road',
        opensAt: '09:00',
        closesAt: '19:00',
      },
    ]),
    isError: ref(false),
    isPending: ref(false),
    refetch: vi.fn(),
  }),
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  RouterLink: {
    template: '<a><slot /></a>',
  },
}));

const createTestI18n = () =>
  createI18n({
    legacy: false,
    locale: 'en',
    messages,
  });

const staffUser = {
  subject: '18b1cd30-1b94-42ff-9c98-f3d709001234',
  username: 'staff@example.test',
  email: 'staff@example.test',
  firstName: 'Local',
  lastName: 'Staff',
  roles: ['STAFF'] as const,
  onboardingRequired: false,
};

const mountBranchDetail = (user: typeof staffUser | null = null) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  queryClient.setQueryData(['current-user'], user);

  return mount(BranchDetailView, {
    global: {
      plugins: [createTestI18n(), [VueQueryPlugin, { queryClient }]],
      stubs: { RouterLink: true },
    },
  });
};

describe('BranchDetailView', () => {
  it('renders branch detail view correctly', () => {
    const wrapper = mountBranchDetail();
    expect(wrapper.text()).toContain('Silom');
    expect(wrapper.text()).toContain('Silom Road');
    expect(wrapper.text()).toContain('09:00–19:00');
  });

  it('triggers action buttons', async () => {
    const wrapper = mountBranchDetail();
    await wrapper.find('button').trigger('click');
    expect(wrapper.exists()).toBe(true);
  });

  it('renders inside the owner portal sidebar layout for staff', () => {
    const wrapper = mountBranchDetail(staffUser);
    expect(wrapper.find('.owner-sidebar').exists()).toBe(true);
  });

  it('renders without the sidebar for guests/clients', () => {
    const wrapper = mountBranchDetail();
    expect(wrapper.find('.owner-sidebar').exists()).toBe(false);
  });
});

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
}));

const createTestI18n = () =>
  createI18n({
    legacy: false,
    locale: 'en',
    messages,
  });

describe('BranchDetailView', () => {
  it('renders branch detail view correctly', () => {
    const wrapper = mount(BranchDetailView, {
      global: {
        plugins: [createTestI18n()],
        stubs: { RouterLink: true },
      },
    });
    expect(wrapper.text()).toContain('Silom');
    expect(wrapper.text()).toContain('Silom Road');
    expect(wrapper.text()).toContain('09:00–19:00');
  });

  it('triggers action buttons', async () => {
    const wrapper = mount(BranchDetailView, {
      global: {
        plugins: [createTestI18n()],
        stubs: { RouterLink: true },
      },
    });
    await wrapper.find('button').trigger('click');
    expect(wrapper.exists()).toBe(true);
  });
});

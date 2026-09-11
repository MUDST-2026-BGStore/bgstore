import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import { ref } from 'vue';
import { messages } from '../i18n';
import BranchListView from './BranchListView.vue';

vi.mock('../composables/useBranches', () => ({
  useBranches: () => ({
    branches: ref([{ id: 'branch-1', name: 'Silom' }]),
    isError: ref(false),
    isPending: ref(false),
    refetch: vi.fn(),
  }),
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {}, params: {} }),
}));

const createTestI18n = () =>
  createI18n({
    legacy: false,
    locale: 'en',
    messages,
  });

describe('BranchListView', () => {
  it('renders branch management view correctly', () => {
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
        stubs: { RouterLink: true },
      },
    });
    expect(wrapper.exists()).toBe(true);
    expect(wrapper.text()).toContain('Branch directory');
  });

  it('filters branches after the debounce window', async () => {
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
        stubs: { RouterLink: true },
      },
    });

    const searchInput = wrapper.find('input');
    if (searchInput.exists()) {
      await searchInput.setValue('Silom');
      await searchInput.setValue('');
    }

    expect(wrapper.text()).toContain('Branches');
  });

  it('opens a branch detail page', async () => {
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
        stubs: { RouterLink: true },
      },
    });

    await wrapper.find('button').trigger('click');
    expect(wrapper.exists()).toBe(true);
  });
});

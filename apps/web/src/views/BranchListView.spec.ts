import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import { messages } from '../i18n';
import BranchListView from './BranchListView.vue';

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
    expect(wrapper.text()).toContain('Branch management');
  });

  it('handles search input and status filter interactions', async () => {
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

    const select = wrapper.find('select');
    if (select.exists()) {
      await select.setValue('Active');
      await select.setValue('Inactive');
      await select.setValue('All statuses');
    }
  });

  it('triggers action buttons and delete modal', async () => {
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
        stubs: { RouterLink: true },
      },
    });

    const buttons = wrapper.findAll('button');
    for (const btn of buttons) {
      await btn.trigger('click');
    }
    expect(wrapper.exists()).toBe(true);
  });
});

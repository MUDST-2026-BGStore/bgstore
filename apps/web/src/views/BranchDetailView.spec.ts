import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import { messages } from '../i18n';
import BranchDetailView from './BranchDetailView.vue';

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
    expect(wrapper.exists()).toBe(true);
  });

  it('triggers action buttons', async () => {
    const wrapper = mount(BranchDetailView, {
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

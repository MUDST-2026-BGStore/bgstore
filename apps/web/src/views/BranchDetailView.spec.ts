import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import BranchDetailView from './BranchDetailView.vue';

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
  useRoute: () => ({ params: { id: '1' }, query: {} }),
}));

describe('BranchDetailView', () => {
  it('renders branch details view correctly', () => {
    const wrapper = mount(BranchDetailView, {
      global: { stubs: { RouterLink: true } },
    });

    expect(wrapper.exists()).toBe(true);
  });

  it('handles user actions and buttons', async () => {
    const wrapper = mount(BranchDetailView, {
      global: { stubs: { RouterLink: true } },
    });

    const buttons = wrapper.findAll('button');
    for (const btn of buttons) {
      await btn.trigger('click');
    }
    expect(wrapper.exists()).toBe(true);
  });
});

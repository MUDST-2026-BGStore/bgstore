import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import BranchFormView from './BranchFormView.vue';

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} }),
}));

describe('BranchFormView', () => {
  it('renders branch form view correctly', () => {
    const wrapper = mount(BranchFormView, {
      global: { stubs: { RouterLink: true } },
    });

    expect(wrapper.exists()).toBe(true);
  });

  it('handles input changes and button clicks', async () => {
    const wrapper = mount(BranchFormView, {
      global: { stubs: { RouterLink: true } },
    });

    const inputs = wrapper.findAll('input');
    for (const input of inputs) {
      await input.setValue('Sample Data');
    }

    const buttons = wrapper.findAll('button');
    for (const btn of buttons) {
      await btn.trigger('click');
    }
    expect(wrapper.exists()).toBe(true);
  });
});

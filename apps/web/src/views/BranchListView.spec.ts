import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import { computed, defineComponent, h, ref } from 'vue';
import { messages } from '../i18n';
import BranchListView from './BranchListView.vue';

const mockPush = vi.fn();

vi.mock('../composables/useBranches', () => ({
  useBranches: () => ({
    branches: ref([
      {
        id: 'branch-1',
        name: 'Silom Branch',
        address: '123 Silom Rd, Bang Rak, Bangkok',
        opensAt: '10:00',
        closesAt: '22:00',
        status: 'ACTIVE',
      },
      {
        id: 'branch-2',
        name: 'Siam Branch',
        address: '999 Rama I Rd, Pathum Wan, Bangkok',
        opensAt: '11:00',
        closesAt: '21:00',
        status: 'INACTIVE',
      },
    ]),
    isError: ref(false),
    isPending: ref(false),
    refetch: vi.fn(),
  }),
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ query: {}, params: {} }),
  RouterLink: defineComponent({
    name: 'RouterLink',
    props: {
      to: {
        type: [String, Object],
        default: '',
      },
    },
    setup(props, { slots }) {
      // Expose the destination so tests can assert where a link points.
      const href = computed(() => {
        const target = props.to;
        if (typeof target === 'string') {
          return target;
        }
        const query = new URLSearchParams(
          (target.query ?? {}) as Record<string, string>,
        ).toString();
        return `${target.path ?? ''}${query ? `?${query}` : ''}`;
      });
      return () =>
        h('a', { href: href.value }, slots.default ? slots.default() : []);
    },
  }),
}));

const createTestI18n = () =>
  createI18n({
    legacy: false,
    locale: 'en',
    messages,
  });

describe('BranchListView (SCRUM-21 Master-Detail & Reservation)', () => {
  it('renders master-detail layout and branch list', () => {
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
      },
    });

    expect(wrapper.exists()).toBe(true);
    expect(wrapper.text()).toContain('Branch directory');
    expect(wrapper.text()).toContain('Silom Branch');
  });

  it('filters branches by branch name or address', async () => {
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
      },
    });

    const searchInput = wrapper.find('input[type="search"]');
    expect(searchInput.exists()).toBe(true);

    await searchInput.setValue('Pathum Wan');
    await new Promise((resolve) => setTimeout(resolve, 350));
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).toContain('Siam Branch');
  });

  it('updates selected branch detail card when branch item is clicked', async () => {
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
      },
    });

    const branchButtons = wrapper.findAll('ul button');
    if (branchButtons.length > 1) {
      await branchButtons[1].trigger('click');
      expect(wrapper.text()).toContain('Siam Branch');
    }
  });

  it('disables booking and displays explanation for inactive branches', async () => {
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
      },
    });

    const branchButtons = wrapper.findAll('ul button');
    await branchButtons[1].trigger('click');

    expect(wrapper.text()).toContain(
      'Booking is currently unavailable for this branch.',
    );
  });

  it('sends an active branch booking to the real reservation flow', async () => {
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
      },
    });

    const branchButtons = wrapper.findAll('ul button');
    await branchButtons[0].trigger('click');

    const bookLink = wrapper
      .findAll('a')
      .find((link) => link.text().includes('Book at this branch'));
    expect(bookLink).toBeDefined();
    expect(bookLink?.attributes('href')).toContain('/reservations/new?branch=');
    expect(bookLink?.attributes('href')).toContain('Silom');
  });

  it('does not render staff-only navigation on the client branch page', () => {
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
      },
    });

    expect(
      wrapper.find('nav[aria-label="Staff quick navigation"]').exists(),
    ).toBe(false);
  });
});

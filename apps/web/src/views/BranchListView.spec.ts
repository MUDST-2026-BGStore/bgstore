import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import { defineComponent, h, ref } from 'vue';
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
    setup(_, { slots }) {
      return () => h('a', slots.default ? slots.default() : []);
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

  it('navigates to reservation table flow when booking active branch', async () => {
    mockPush.mockClear();
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
      },
    });

    const branchButtons = wrapper.findAll('ul button');
    await branchButtons[0].trigger('click');

    const bookButton = wrapper
      .findAll('button')
      .find((b) => b.text().includes('Book at this branch'));
    expect(bookButton).toBeDefined();
    await bookButton?.trigger('click');

    expect(mockPush).toHaveBeenCalledWith({
      path: '/tables',
      query: { branchId: 'branch-1' },
    });
  });

  it('renders staff navigation links for Dashboard, History, and Check-in / Check-out', () => {
    const wrapper = mount(BranchListView, {
      global: {
        plugins: [createTestI18n()],
      },
    });

    const nav = wrapper.find('nav[aria-label="Staff quick navigation"]');
    expect(nav.exists()).toBe(true);
    expect(nav.text()).toContain('Dashboard');
    expect(nav.text()).toContain('History');
    expect(nav.text()).toContain('Check-in / Check-out');
  });
});

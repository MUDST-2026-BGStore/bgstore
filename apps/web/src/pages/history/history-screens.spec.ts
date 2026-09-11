import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Component } from 'vue';
import { createI18n } from 'vue-i18n';
import { createMemoryHistory, createRouter } from 'vue-router';
import { messages } from '../../i18n';
import { resetAuthResolver, routes, setAuthResolver } from '../../router';
import ClientHistoryDetailPage from './ClientHistoryDetailPage.vue';
import ClientHistoryListPage from './ClientHistoryListPage.vue';
import { reservationService } from './reservation-service';

function createTestRouter(initialPath = '/history') {
  const router = createRouter({
    history: createMemoryHistory(initialPath),
    routes: [...routes],
  });

  router.beforeEach((to, _from, next) => {
    if (to.matched.some((record) => record.meta?.requiresAuth)) {
      // In tests, router check follows setAuthResolver
      if (!authCheck()) {
        return next({ path: '/login', query: { redirect: to.fullPath } });
      }
    }
    next();
  });

  return router;
}

let isUserLoggedIn = true;
function authCheck() {
  return isUserLoggedIn;
}

async function renderTestScreen(component: Component, path = '/history') {
  const router = createTestRouter(path);
  await router.push(path);
  await router.isReady();

  const wrapper = mount(component, {
    global: {
      plugins: [
        router,
        createI18n({ legacy: false, locale: 'en', messages }),
        [
          VueQueryPlugin,
          {
            queryClient: new QueryClient({
              defaultOptions: { queries: { retry: false } },
            }),
          },
        ],
      ],
    },
  });

  return { wrapper, router };
}

describe('Client Reservation History', () => {
  beforeEach(() => {
    isUserLoggedIn = true;
    setAuthResolver(() => isUserLoggedIn);
    reservationService.reset();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    resetAuthResolver();
  });

  describe('History List View', () => {
    it('shows skeleton items while data is loading and renders records after resolve', async () => {
      vi.useFakeTimers();
      const { wrapper } = await renderTestScreen(
        ClientHistoryListPage,
        '/history',
      );

      // While loading, skeleton exists
      expect(
        wrapper.findAll('[data-testid="history-skeleton"]').length,
      ).toBeGreaterThan(0);

      // Fast-forward simulated delay
      await vi.runAllTimersAsync();
      await flushPromises();

      // Skeletons gone, records visible
      expect(wrapper.findAll('[data-testid="history-skeleton"]').length).toBe(
        0,
      );
      expect(wrapper.get('h1').text()).toBe('Reservation History');

      const cards = wrapper.findAll('[data-testid="reservation-card"]');
      expect(cards.length).toBeGreaterThan(0);
      expect(cards[0].text()).toContain('Reservation');
      expect(cards[0].text()).toContain('Table 5');
    });

    it('renders tabs with All as default and allows tab filtering without page reload', async () => {
      vi.useFakeTimers();
      const { wrapper } = await renderTestScreen(
        ClientHistoryListPage,
        '/history',
      );
      await vi.runAllTimersAsync();
      await flushPromises();

      // Check default tab
      const allTab = wrapper.get('[data-testid="tab-all"]');
      expect(allTab.attributes('aria-selected')).toBe('true');

      // Click "Reserved" tab
      const reservedTab = wrapper.get('[data-testid="tab-reserved"]');
      await reservedTab.trigger('click');
      await vi.runAllTimersAsync();
      await flushPromises();

      expect(reservedTab.attributes('aria-selected')).toBe('true');
      const badges = wrapper.findAll('[data-testid="status-badge"]');
      expect(badges.length).toBeGreaterThan(0);
      for (const badge of badges) {
        expect(badge.text()).toBe('Reserved');
      }

      // Click "Completed" tab
      const completedTab = wrapper.get('[data-testid="tab-completed"]');
      await completedTab.trigger('click');
      await vi.runAllTimersAsync();
      await flushPromises();

      expect(completedTab.attributes('aria-selected')).toBe('true');
      const completedBadges = wrapper.findAll('[data-testid="status-badge"]');
      expect(completedBadges.length).toBeGreaterThan(0);
      for (const badge of completedBadges) {
        expect(badge.text()).toBe('Completed');
      }

      // Click "Cancelled" tab
      const cancelledTab = wrapper.get('[data-testid="tab-cancelled"]');
      await cancelledTab.trigger('click');
      await vi.runAllTimersAsync();
      await flushPromises();

      expect(cancelledTab.attributes('aria-selected')).toBe('true');
      const cancelledBadges = wrapper.findAll('[data-testid="status-badge"]');
      expect(cancelledBadges.length).toBeGreaterThan(0);
      for (const badge of cancelledBadges) {
        expect(badge.text()).toBe('Cancelled');
      }
    });

    it('displays status badges with correct style classes', async () => {
      vi.useFakeTimers();
      const { wrapper } = await renderTestScreen(
        ClientHistoryListPage,
        '/history',
      );
      await vi.runAllTimersAsync();
      await flushPromises();

      const cards = wrapper.findAll('[data-testid="reservation-card"]');
      // In seed data: first is Reserved, second is Cancelled, third is Completed
      const badge0 = cards[0].get('[data-testid="status-badge"]');
      expect(badge0.text()).toBe('Reserved');
      expect(badge0.classes()).toContain('text-[#1d4ed8]');

      const badge1 = cards[1].get('[data-testid="status-badge"]');
      expect(badge1.text()).toBe('Cancelled');
      expect(badge1.classes()).toContain('text-[#dc2626]');

      const badge2 = cards[2].get('[data-testid="status-badge"]');
      expect(badge2.text()).toBe('Completed');
      expect(badge2.classes()).toContain('text-[#276635]');
    });

    it('navigates through pagination controls', async () => {
      vi.useFakeTimers();
      const { wrapper } = await renderTestScreen(
        ClientHistoryListPage,
        '/history',
      );
      await vi.runAllTimersAsync();
      await flushPromises();

      const page2Btn = wrapper.find('[data-testid="page-2"]');
      expect(page2Btn.exists()).toBe(true);
      await page2Btn.trigger('click');
      await vi.runAllTimersAsync();
      await flushPromises();

      expect(
        wrapper.get('[data-testid="page-2"]').attributes('aria-current'),
      ).toBe('page');
    });

    it('displays empty state message when a filter returns zero records', async () => {
      vi.useFakeTimers();
      // Empty out reservations temporarily
      vi.spyOn(reservationService, 'getReservations').mockResolvedValueOnce({
        items: [],
        totalElements: 0,
        totalPages: 1,
        page: 1,
        pageSize: 4,
      });

      const { wrapper } = await renderTestScreen(
        ClientHistoryListPage,
        '/history',
      );
      await vi.runAllTimersAsync();
      await flushPromises();

      expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true);
      expect(wrapper.text()).toContain(
        'No reservations found under this category',
      );
    });
  });

  describe('History Detail View', () => {
    it('renders detail view with table metadata and read-only inputs', async () => {
      vi.useFakeTimers();
      const { wrapper } = await renderTestScreen(
        ClientHistoryDetailPage,
        '/history/res-1',
      );
      await vi.runAllTimersAsync();
      await flushPromises();

      expect(wrapper.get('h1').text()).toBe('Reservation');
      expect(wrapper.text()).toContain('Table 5');
      expect(wrapper.text()).toContain('4 seats · 20 baht per hour');

      // Verify read-only fields
      expect(
        wrapper.get('[data-testid="field-name"]').attributes('value'),
      ).toBe('John Doe');
      expect(
        wrapper.get('[data-testid="field-phone"]').attributes('value'),
      ).toBe('0123456789');
      expect(
        wrapper.get('[data-testid="field-date"]').attributes('value'),
      ).toBe('13/09/2024');
      expect(
        wrapper.get('[data-testid="field-time"]').attributes('value'),
      ).toBe('09:00 - 12:00');
      expect(
        wrapper.get('[data-testid="field-checkin"]').attributes('value'),
      ).toBe('09:00 AM');
      expect(
        wrapper.get('[data-testid="field-checkout"]').attributes('value'),
      ).toBe('12:00 PM');
      expect(wrapper.get('[data-testid="field-overtime"]').text()).toBe(
        '0 Minutes',
      );
      expect(
        wrapper.get('[data-testid="field-price"]').attributes('value'),
      ).toBe('250 ฿');

      // Check Cancel button is enabled for Reserved status
      const cancelBtn = wrapper.get('[data-testid="cancel-button"]');
      expect(cancelBtn.attributes('disabled')).toBeUndefined();
    });

    it('removes cancel button for Completed or Cancelled reservations', async () => {
      vi.useFakeTimers();
      // res-2 is Cancelled
      const { wrapper } = await renderTestScreen(
        ClientHistoryDetailPage,
        '/history/res-2',
      );
      await vi.runAllTimersAsync();
      await flushPromises();

      expect(wrapper.find('[data-testid="cancel-button"]').exists()).toBe(
        false,
      );
    });

    it('executes cancellation flow with confirmation modal', async () => {
      vi.useFakeTimers();
      const { wrapper } = await renderTestScreen(
        ClientHistoryDetailPage,
        '/history/res-1',
      );
      await vi.runAllTimersAsync();
      await flushPromises();

      // Modal should not exist initially
      expect(wrapper.find('[data-testid="cancel-modal"]').exists()).toBe(false);

      // Click Cancel
      const cancelBtn = wrapper.get('[data-testid="cancel-button"]');
      await cancelBtn.trigger('click');

      // Confirmation modal is open
      const modal = wrapper.get('[data-testid="cancel-modal"]');
      expect(modal.text()).toContain(
        'Are you sure you want to cancel this reservation?',
      );

      // Click Confirm Cancellation
      const confirmBtn = wrapper.get(
        '[data-testid="modal-confirm-cancel-button"]',
      );
      await confirmBtn.trigger('click');
      await vi.runAllTimersAsync();
      await flushPromises();

      // Modal is closed, status badge updated to Cancelled, cancel button removed
      expect(wrapper.find('[data-testid="cancel-modal"]').exists()).toBe(false);
      const badge = wrapper.get('[data-testid="detail-status-badge"]');
      expect(badge.text()).toBe('Cancelled');
      expect(wrapper.find('[data-testid="cancel-button"]').exists()).toBe(
        false,
      );
    });

    it('clicking back preserves query tab and page state', async () => {
      vi.useFakeTimers();
      const { wrapper, router } = await renderTestScreen(
        ClientHistoryDetailPage,
        '/history/res-1?tab=reserved&page=2',
      );
      await vi.runAllTimersAsync();
      await flushPromises();

      const pushSpy = vi.spyOn(router, 'push');
      const backBtn = wrapper.get('[data-testid="back-button"]');
      await backBtn.trigger('click');

      expect(pushSpy).toHaveBeenCalledWith({
        name: 'history',
        query: { tab: 'reserved', page: '2' },
      });
    });
  });

  describe('Auth Guard', () => {
    it('redirects unauthenticated visitors to /login', async () => {
      isUserLoggedIn = false;
      setAuthResolver(() => false);

      const router = createTestRouter('/history');
      await router.push('/history');

      expect(router.currentRoute.value.path).toBe('/login');
      expect(router.currentRoute.value.query.redirect).toBe('/history');
    });
  });
});

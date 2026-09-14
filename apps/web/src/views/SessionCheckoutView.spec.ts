import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import { createI18n } from 'vue-i18n';
import { createMemoryHistory, createRouter } from 'vue-router';
import { messages } from '../i18n';
import SessionCheckoutView from './SessionCheckoutView.vue';

describe('SessionCheckoutView', () => {
  it.each(['en', 'th'])(
    'selects one payment method and returns to the session in %s',
    async (locale) => {
      const router = createRouter({
        history: createMemoryHistory(),
        routes: [
          { path: '/checkout', component: SessionCheckoutView },
          {
            path: '/sessions/active',
            name: 'client-active-session',
            component: { template: '<div />' },
          },
        ],
      });
      await router.push('/checkout');
      await router.isReady();
      const wrapper = mount(SessionCheckoutView, {
        global: {
          plugins: [router, createI18n({ legacy: false, locale, messages })],
        },
      });
      expect(wrapper.get('.receipt-card footer').text()).toContain('200.00');
      expect(wrapper.findAll('input[type="radio"]')).toHaveLength(3);
      expect(wrapper.get('input:checked').attributes('value')).toBe(
        'promptpay',
      );
      for (const method of ['promptpay', 'bank', 'cash']) {
        await wrapper.get(`input[value="${method}"]`).setValue();
        expect(wrapper.findAll('input:checked')).toHaveLength(1);
        expect(wrapper.get('input:checked').attributes('value')).toBe(method);
      }
      await wrapper.get('.confirm-payment').trigger('click');
      expect(wrapper.get('.payment-complete').text()).toContain(
        messages[locale as 'en' | 'th'].checkout.paymentComplete,
      );
      expect(wrapper.find('.complete-icon').exists()).toBe(true);
      expect(wrapper.find('.payment-pane').exists()).toBe(false);
      await wrapper.get('.success-back').trigger('click');
      await new Promise((resolve) => setTimeout(resolve, 0));
      expect(router.currentRoute.value.name).toBe('client-active-session');
      wrapper.unmount();
    },
  );
});

import { computed, type MaybeRefOrGetter, toValue } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { listMyOrders, type Order } from '@theparlor/api-client';
import { collectPages } from './catalog';

export const MY_ORDERS_QUERY_KEY = ['storefront', 'orders', 'mine'] as const;

/**
 * The signed-in customer's orders across branches, newest first. Guests have no
 * order history: their order is shown once, in the checkout confirmation.
 */
export function useMyOrders(enabled: MaybeRefOrGetter<boolean>) {
  return useQuery<Order[]>({
    queryKey: MY_ORDERS_QUERY_KEY,
    enabled: computed(() => toValue(enabled)),
    staleTime: 15_000,
    retry: false,
    // While a PromptPay QR is waiting, keep its state current.
    refetchInterval: (query) =>
      query.state.data?.some(
        (order) =>
          order.status === 'PLACED' &&
          order.payment.method === 'PROMPTPAY' &&
          order.payment.status === 'PENDING',
      )
        ? 5_000
        : false,
    queryFn: async ({ signal }) => {
      const orders = await collectPages((cursor) =>
        listMyOrders({ limit: 100, cursor }, undefined, signal),
      );
      // The API pages in ID order, which is oldest first.
      return orders.reverse();
    },
  });
}

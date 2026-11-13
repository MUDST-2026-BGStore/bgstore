import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { listBranches, type Branch } from '@theparlor/api-client';
import { queryClient } from '../lib/api';
import { collectPages } from '../lib/pagination';
import { branchCoordinates } from '../data/branches';

export interface StoreBranch {
  id: string;
  name: string;
  address: string;
  phone?: string;
  /** Every active branch hands over pickup orders. */
  pickup: boolean;
  /** The one branch that ships delivery orders. */
  fulfillsDelivery: boolean;
  openingHours: Branch['openingHours'];
  /** Approximate map position; absent for branches without a known position. */
  location?: { lat: number; lng: number };
}

export const BRANCHES_QUERY_KEY = ['storefront', 'branches'] as const;

/**
 * The active branches, read once from the API and shared by store selection, cart
 * fulfillment and the locations page. Stores cannot call useQuery, so this goes
 * through the shared query client; its cache still deduplicates and ages the read.
 */
export const useBranchDirectoryStore = defineStore('branch-directory', () => {
  const branches = ref<StoreBranch[]>([]);
  const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
  let pending: Promise<void> | undefined;

  const deliveryBranch = computed(() =>
    branches.value.find((branch) => branch.fulfillsDelivery),
  );
  const byId = (id: string | undefined | null) =>
    branches.value.find((branch) => branch.id === id);

  function load(force = false): Promise<void> {
    if (pending) return pending;
    if (status.value === 'ready' && !force) return Promise.resolve();
    status.value = branches.value.length ? status.value : 'loading';
    pending = queryClient
      .fetchQuery({
        queryKey: BRANCHES_QUERY_KEY,
        meta: { public: true },
        staleTime: force ? 0 : 300_000,
        queryFn: ({ signal }) =>
          collectPages((cursor) =>
            listBranches({ limit: 100, cursor }, undefined, signal),
          ),
      })
      .then((items) => {
        branches.value = items.map((branch) => ({
          id: branch.id,
          name: branch.name,
          address: branch.address,
          phone: branch.phone,
          pickup: true,
          fulfillsDelivery: branch.fulfillsDelivery,
          openingHours: branch.openingHours,
          location: branchCoordinates(branch.name),
        }));
        status.value = 'ready';
      })
      .catch(() => {
        status.value = 'error';
      })
      .finally(() => {
        pending = undefined;
      });
    return pending;
  }

  return { branches, status, deliveryBranch, byId, load };
});

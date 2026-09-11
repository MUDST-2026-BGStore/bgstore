import { computed } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { branchesQueryOptions } from '../queries/games';
import type { Branch } from '../generated/api/types.gen';

export function useBranches() {
  const query = useQuery(branchesQueryOptions());
  const branches = computed(() => query.data.value ?? []);
  const getBranchById = (id: string) =>
    branches.value.find((branch) => branch.id === id);

  return {
    branches,
    getBranchById,
    isPending: query.isPending,
    isError: query.isError,
    refetch: query.refetch,
  };
}

export type { Branch };

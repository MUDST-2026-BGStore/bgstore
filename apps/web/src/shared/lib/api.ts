import { configureApiTransport } from '@theparlor/api-client';
import { clearAuth } from '@theparlor/auth';
import { QueryClient } from '@tanstack/vue-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      staleTime: 0,
    },
    mutations: { retry: false },
  },
});

export function configureApi(): void {
  configureApiTransport({
    baseUrl: import.meta.env.VITE_API_BASE_URL || '',
    onUnauthorized: () => {
      clearAuth();
      // Public catalog reads remain valid for guests. Removing an active public
      // query here cancels its rejection and leaves the view pending forever.
      queryClient.removeQueries({
        predicate: (query) => query.meta?.public !== true,
      });
    },
  });
}

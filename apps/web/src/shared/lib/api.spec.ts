import { afterEach, expect, it, vi } from 'vitest';
import { QueryObserver } from '@tanstack/vue-query';
import { listGames } from '@theparlor/api-client';
import { configureApi, queryClient } from './api';

afterEach(() => {
  queryClient.clear();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

it('settles a rejected guest catalog request as an error instead of leaving its observer loading', async () => {
  vi.stubEnv('VITE_API_BASE_URL', 'https://theparlor.test');
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(null, { status: 401 })),
  );
  configureApi();
  queryClient.setQueryData(['private-profile'], { name: 'Previous user' });
  queryClient.setQueryDefaults(['public-taxonomy'], { meta: { public: true } });
  queryClient.setQueryData(['public-taxonomy'], { items: [] });
  const observer = new QueryObserver(queryClient, {
    queryKey: ['storefront', 'catalog'],
    meta: { public: true },
    queryFn: () => listGames({ limit: 100 }),
    retry: false,
  });
  const unsubscribe = observer.subscribe(() => {
    /* Keep the view's query active. */
  });
  try {
    await observer.refetch();
    expect(observer.getCurrentResult().status).toBe('error');
    expect(observer.getCurrentResult().isPending).toBe(false);
    expect(queryClient.getQueryData(['private-profile'])).toBeUndefined();
    expect(queryClient.getQueryData(['public-taxonomy'])).toEqual({
      items: [],
    });
  } finally {
    unsubscribe();
  }
});

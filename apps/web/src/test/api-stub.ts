import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { flushPromises, mount } from '@vue/test-utils';
import { vi } from 'vitest';
import type { Component } from 'vue';
import { createI18n } from 'vue-i18n';
import { createMemoryHistory, createRouter } from 'vue-router';
import { client } from '../generated/api/client.gen';
import { messages } from '../i18n';
import { routes } from '../router';

/** One request the component made, as the stubbed `fetch` saw it. */
export interface RecordedCall {
  method: string;
  url: string;
  body?: string;
}

export interface StubbedResponse {
  status?: number;
  body?: unknown;
}

/**
 * Answers a request, or returns undefined to let the next handler try.
 *
 * Handlers receive the real `Request` the generated client built, so a test
 * asserting on a URL or a body is asserting on what the browser would send.
 */
export type ApiHandler = (
  request: Request,
) => StubbedResponse | undefined | Promise<StubbedResponse | undefined>;

/**
 * Replaces `fetch` so the generated client, the query layer and the screens all
 * run for real against canned HTTP responses. Returns the recorded calls.
 */
export function stubApi(handlers: ApiHandler[]): RecordedCall[] {
  const calls: RecordedCall[] = [];

  client.setConfig({ baseUrl: 'http://localhost/api/v1' });
  vi.stubGlobal(
    'fetch',
    vi.fn(async (request: Request) => {
      const body =
        request.method === 'GET' || request.method === 'DELETE'
          ? undefined
          : await request.clone().text();
      calls.push({ method: request.method, url: request.url, body });

      for (const handler of handlers) {
        const answer = await handler(request);
        if (answer) {
          return jsonResponse(answer);
        }
      }

      return jsonResponse({ status: 404, body: { status: 404 } });
    }),
  );

  return calls;
}

/** Handler that answers one path (and optionally one method). */
export function route(
  path: string,
  answer: StubbedResponse,
  method = 'GET',
): ApiHandler {
  return (request) => {
    const url = new URL(request.url);

    return request.method === method && url.pathname === '/api/v1' + path
      ? answer
      : undefined;
  };
}

function jsonResponse({ status = 200, body }: StubbedResponse): Response {
  if (status === 204 || body === undefined) {
    return new Response(null, { status });
  }

  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Mounts a screen with the real routes, i18n and a retry-free query client. */
export async function renderScreen(
  component: Component,
  path: string,
  options: { flush?: boolean } = {},
) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [...routes],
  });
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
  if (options.flush !== false) {
    await flushPromises();
  }

  return { wrapper, router };
}

/** The query string the component sent on its last call to `path`. */
export function lastQuery(
  calls: RecordedCall[],
  path: string,
): URLSearchParams | undefined {
  const matches = calls.filter(
    (call) => new URL(call.url).pathname === '/api/v1' + path,
  );
  const last = matches.at(-1);

  return last ? new URL(last.url).searchParams : undefined;
}

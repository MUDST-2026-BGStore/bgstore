import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosError } from 'axios';
import {
  createBranch,
  csrf,
  hello,
  me,
  session,
  whoami,
} from '../generated/api';
import { configureApiTransport } from './transport';

const origin = 'https://theparlor.test';
const branch = {
  name: 'Central',
  address: 'Main Street',
  timezone: 'Asia/Bangkok',
  currency: 'THB',
  openingHours: [],
  active: true,
};
const token = (value = 'masked-token') =>
  Response.json({
    token: value,
    headerName: 'X-CSRF-TOKEN',
    parameterName: '_csrf',
  });

beforeEach(() => configureApiTransport({ baseUrl: `${origin}/` }));
afterEach(() => {
  vi.unstubAllGlobals();
  configureApiTransport({});
});

describe('generated session client', () => {
  it('obtains a fresh CSRF token before every write and preserves caller headers', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(token('first'))
      .mockResolvedValueOnce(Response.json({ id: 'one' }))
      .mockResolvedValueOnce(token('second'))
      .mockResolvedValueOnce(Response.json({ id: 'two' }));
    vi.stubGlobal('fetch', fetch);

    await createBranch(branch, { headers: { 'X-Request-ID': 'request-one' } });
    await createBranch(branch);

    expect(
      fetch.mock.calls.map(([request]) => [request.url, request.method]),
    ).toEqual([
      [`${origin}/api/auth/csrf`, 'GET'],
      [`${origin}/api/v1/branches`, 'POST'],
      [`${origin}/api/auth/csrf`, 'GET'],
      [`${origin}/api/v1/branches`, 'POST'],
    ]);
    const first = fetch.mock.calls[1][0] as Request;
    expect(first.headers.get('X-CSRF-TOKEN')).toBe('first');
    expect(first.headers.get('X-Request-ID')).toBe('request-one');
    expect(first.headers.get('Content-Type')).toBe('application/json');
    expect(JSON.parse(await first.text())).toEqual(branch);
    expect(fetch.mock.calls[3][0].headers.get('X-CSRF-TOKEN')).toBe('second');
  });

  it('does not submit a write when CSRF initialization fails', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('', { status: 503 }));
    vi.stubGlobal('fetch', fetch);
    await expect(createBranch(branch)).rejects.toMatchObject({
      response: { status: 503 },
    });
    expect(fetch).toHaveBeenCalledOnce();
  });

  it('sends same-origin cookies and bypasses the HTTP cache without bearer tokens', async () => {
    const fetch = vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(Response.json({ username: 'staff', roles: ['staff'] })),
      );
    vi.stubGlobal('fetch', fetch);
    expect((await whoami()).roles).toContain('staff');
    const request = fetch.mock.calls[0][0] as Request;
    expect(request.credentials).toBe('same-origin');
    expect(request.cache).toBe('no-store');
    expect(request.headers.has('Authorization')).toBe(false);
    expect(fetch).toHaveBeenCalledOnce();
  });

  it('reads an anonymous session and CSRF through generated operations', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(Response.json({ authenticated: false }))
      .mockResolvedValueOnce(token());
    vi.stubGlobal('fetch', fetch);
    expect((await session()).authenticated).toBe(false);
    expect((await csrf()).token).toBe('masked-token');
    expect(
      fetch.mock.calls.map(([request]) => [request.url, request.method]),
    ).toEqual([
      [`${origin}/api/auth/session`, 'GET'],
      [`${origin}/api/auth/csrf`, 'GET'],
    ]);
  });

  it.each([me, () => createBranch(branch)])(
    'notifies once when a request or CSRF check returns 401',
    async (operation) => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(new Response('', { status: 401 })),
      );
      const expired = vi.fn();
      configureApiTransport({ baseUrl: origin, onUnauthorized: expired });
      await expect(operation()).rejects.toMatchObject({
        response: { status: 401 },
      });
      expect(expired).toHaveBeenCalledOnce();
    },
  );

  it.each(['not authorized', '{"reason":"expired"}'])(
    'preserves HTTP error body %s',
    async (body) => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            new Response(body, { status: 401, statusText: 'Unauthorized' }),
          ),
      );
      const error = await whoami().catch((error: unknown) => error);
      expect(error).toBeInstanceOf(AxiosError);
      expect(error).toMatchObject({
        response: {
          status: 401,
          data: body.startsWith('{') ? { reason: 'expired' } : body,
        },
      });
    },
  );

  it('honors cancellation without sending the request', async () => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    const controller = new AbortController();
    controller.abort();
    await expect(hello(undefined, controller.signal)).rejects.toMatchObject({
      code: 'ERR_CANCELED',
    });
    expect(fetch).not.toHaveBeenCalled();
  });
});

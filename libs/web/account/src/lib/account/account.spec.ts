import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { configureApiTransport, type Profile } from '@theparlor/api-client';
import { clearAccount, ensureAccount, useAccount } from '../../index';

beforeEach(() => {
  clearAccount();
  configureApiTransport({ baseUrl: 'http://localhost' });
});

afterEach(() => {
  configureApiTransport({});
  vi.unstubAllGlobals();
});

it('loads the current account using the generated API client', async () => {
  const account: Profile = {
    id: 'account-1',
    firstName: 'Test',
    lastName: 'User',
    phoneNumber: '0812345678',
    preferences: { preferredLocale: 'en-US' },
  };
  const fetch = vi.fn().mockResolvedValue(Response.json(account));
  vi.stubGlobal('fetch', fetch);

  await expect(ensureAccount()).resolves.toEqual({ status: 'ready', account });
  expect(new Request(fetch.mock.calls[0][0]).url).toBe(
    'http://localhost/api/v1/me',
  );
  expect(useAccount().value).toEqual({ status: 'ready', account });
});

it('marks the account as missing when the API returns 404', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(Response.json({ status: 404 }, { status: 404 })),
  );

  await expect(ensureAccount()).resolves.toEqual({ status: 'missing' });
  expect(useAccount().value.status).toBe('missing');
});

it('propagates server errors without treating the account as missing', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(Response.json({ status: 500 }, { status: 500 })),
  );

  await expect(ensureAccount()).rejects.toMatchObject({
    response: { status: 500 },
  });
  expect(useAccount().value.status).toBe('unknown');
});

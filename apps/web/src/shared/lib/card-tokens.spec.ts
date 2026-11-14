import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { httpStatus } from './api-errors';
import { resetCardTokenization, tokenizeCard } from './card-tokens';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const card = {
  number: '4242 4242 4242 4242',
  expMonth: 12,
  expYear: 2030,
  cvc: '123',
  holderName: 'Test Holder',
};

function configured(configuration: object) {
  request.mockImplementation(({ url }: { url: string }) => {
    if (url === '/api/v1/payments/configuration')
      return Promise.resolve(configuration);
    if (url === '/api/v1/payments/tokens')
      return Promise.resolve({ token: 'server-token' });
    return Promise.reject(new Error(`unexpected ${url}`));
  });
}

beforeEach(() => {
  request.mockReset();
  resetCardTokenization();
});

afterEach(() => {
  delete window.Omise;
});

it('posts the card to the server when the provider allows it', async () => {
  configured({ cardTokenization: 'SERVER' });
  await expect(tokenizeCard(card)).resolves.toBe('server-token');
  expect(request).toHaveBeenCalledWith(
    expect.objectContaining({ url: '/api/v1/payments/tokens' }),
    undefined,
  );
});

it('sends the card straight to Omise and never to the server', async () => {
  configured({ cardTokenization: 'OMISE', omisePublicKey: 'pkey_test_1' });
  const setPublicKey = vi.fn();
  const createToken = vi.fn((_type, _card, callback) =>
    callback(200, { id: 'tokn_test_1' }),
  );
  window.Omise = { setPublicKey, createToken };
  await expect(tokenizeCard(card)).resolves.toBe('tokn_test_1');
  expect(setPublicKey).toHaveBeenCalledWith('pkey_test_1');
  expect(createToken).toHaveBeenCalledWith(
    'card',
    {
      name: 'Test Holder',
      number: '4242424242424242',
      expiration_month: 12,
      expiration_year: 2030,
      security_code: '123',
    },
    expect.any(Function),
  );
  const urls = request.mock.calls.map(([options]) => options.url);
  expect(urls).toEqual(['/api/v1/payments/configuration']);
});

it('reports a rejected card as invalid details and an outage as a provider failure', async () => {
  configured({ cardTokenization: 'OMISE', omisePublicKey: 'pkey_test_1' });
  window.Omise = {
    setPublicKey: vi.fn(),
    createToken: vi.fn((_type, _card, callback) =>
      callback(400, { code: 'invalid_card', message: 'number is invalid' }),
    ),
  };
  const invalid = await tokenizeCard(card).catch((error: unknown) => error);
  expect(httpStatus(invalid)).toBe(400);

  window.Omise.createToken = vi.fn((_type, _card, callback) =>
    callback(401, { code: 'authentication_failure', message: 'bad key' }),
  );
  const outage = await tokenizeCard(card).catch((error: unknown) => error);
  expect(httpStatus(outage)).toBe(502);
});

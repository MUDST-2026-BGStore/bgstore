import { describe, expect, it } from 'vitest';
import { ApiRequestError, apiErrorMessage, toApiError } from './api-error';

describe('toApiError', () => {
  it('uses the problem detail and keeps the status', () => {
    const error = toApiError(
      { status: 400, detail: 'The reservation must fit branch opening hours.' },
      new Response(null, { status: 400 }),
    );

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error.status).toBe(400);
    expect(error.message).toBe(
      'The reservation must fit branch opening hours.',
    );
  });

  it('lists rejected fields of a validation problem', () => {
    const error = toApiError(
      {
        status: 422,
        errors: [{ field: 'partySize', message: 'invalid' }],
      },
      new Response(null, { status: 422 }),
    );

    expect(error.message).toBe('partySize: invalid');
  });

  it('explains an empty 404 instead of hiding it', () => {
    const error = toApiError({}, new Response(null, { status: 404 }));

    expect(error.status).toBe(404);
    expect(error.message).toContain('could not be found');
  });

  it('names an unmapped status', () => {
    const error = toApiError('', new Response(null, { status: 418 }));

    expect(error.message).toContain('418');
  });

  it('ignores an HTML error page', () => {
    const error = toApiError(
      '<html>Bad gateway</html>',
      new Response(null, { status: 502 }),
    );

    expect(error.message).toContain('not reachable');
  });
});

describe('apiErrorMessage', () => {
  it('falls back when nothing specific is known', () => {
    expect(apiErrorMessage(undefined, 'Failed.')).toBe('Failed.');
    expect(apiErrorMessage(new TypeError('Failed to fetch'), 'x')).toBe(
      'Failed to fetch',
    );
  });
});

describe('toApiError keeps the problem body reachable', () => {
  it('exposes the rejected fields on the failure itself', () => {
    const error = toApiError(
      { status: 422, errors: [{ field: 'copies', message: 'invalid' }] },
      new Response(null, { status: 422 }),
    );

    expect((error as unknown as { errors: unknown }).errors).toEqual([
      { field: 'copies', message: 'invalid' },
    ]);
    expect(error.status).toBe(422);
  });
});

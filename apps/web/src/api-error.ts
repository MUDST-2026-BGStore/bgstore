import { client } from './generated/api/client.gen';
import type {
  ProblemDetail,
  ValidationProblem,
} from './generated/api/types.gen';

/**
 * A failed API call, carrying the HTTP status and the most specific reason the
 * response gave, plus the fields of the problem document the server sent. The generated client throws the bare response body, which is
 * empty for a 404 from the proxy and loses the status either way.
 */
export class ApiRequestError extends Error {
  readonly status: number | undefined;

  constructor(message: string, status?: number) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
  }
}

const statusReasons: Record<number, string> = {
  400: 'The request was not accepted.',
  401: 'Your session has expired. Please sign in again.',
  403: 'You do not have permission to do this.',
  404: 'The requested resource could not be found.',
  409: 'The request conflicts with the current state.',
  422: 'Some of the submitted values are not valid.',
  429: 'Too many requests. Please wait a moment and try again.',
  502: 'The service is not reachable right now.',
  503: 'The service is not reachable right now.',
  504: 'The service took too long to respond.',
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** The server's own explanation, when the body is a problem document. */
function problemReason(body: unknown): string | undefined {
  if (typeof body === 'string') {
    return body.trim() && body.length < 300 && !body.startsWith('<')
      ? body.trim()
      : undefined;
  }
  if (!isRecord(body)) return undefined;

  const problem = body as Partial<ValidationProblem & ProblemDetail>;
  const fields = Array.isArray(problem.errors)
    ? problem.errors
        .filter((entry) => isRecord(entry) && typeof entry.field === 'string')
        .map((entry) => `${entry.field}: ${entry.message}`)
    : [];
  if (fields.length > 0) return fields.join('; ');
  if (typeof problem.detail === 'string' && problem.detail.trim()) {
    return problem.detail.trim();
  }
  return undefined;
}

/** Wraps whatever the generated client threw so callers can read status and reason. */
export function toApiError(
  error: unknown,
  response?: Response,
): ApiRequestError {
  if (error instanceof ApiRequestError) return error;

  const status =
    response?.status ??
    (isRecord(error) && typeof error.status === 'number'
      ? error.status
      : undefined);
  const reason =
    problemReason(error) ??
    (status === undefined ? undefined : statusReasons[status]) ??
    (status === undefined
      ? undefined
      : `The server answered ${status}${response?.statusText ? ` ${response.statusText}` : ''}.`);

  const wrapped = new ApiRequestError(
    reason ?? (error instanceof Error ? error.message : 'Request failed.'),
    status,
  );
  // Screens read the problem document straight off the failure (for example
  // `errors` on a 422), so the body's own fields stay reachable.
  if (isRecord(error) && !(error instanceof Error)) {
    const body: Record<string, unknown> = { ...error };
    delete body['message'];
    delete body['name'];
    delete body['stack'];
    Object.assign(wrapped, body, { status });
  }
  return wrapped;
}

/**
 * The reason to show for a failed request, or `fallback` when nothing specific
 * is known (for example a network failure, which has no response to read).
 */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) return error.message;
  const reason = problemReason(error);
  if (reason) return reason;
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}

let installed = false;

/** Makes every generated-client call fail with an {@link ApiRequestError}. */
export function installApiErrorHandling(): void {
  if (installed) return;
  installed = true;
  client.interceptors.error.use((error, response) =>
    toApiError(error, response),
  );
}

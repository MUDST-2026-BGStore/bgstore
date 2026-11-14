/** The HTTP status of a failed API call, or undefined for network and other errors. */
export function httpStatus(error: unknown): number | undefined {
  return (error as { response?: { status?: number } } | null)?.response?.status;
}

/** The stable error code from the API's Problem body, when it sent one. */
export function problemCode(error: unknown): string | undefined {
  const data = (error as { response?: { data?: unknown } } | null)?.response
    ?.data;
  const code = (data as { code?: unknown } | null)?.code;
  return typeof code === 'string' ? code : undefined;
}

/** The human-readable message from the API's Problem body, when it sent one. */
export function problemMessage(error: unknown): string | undefined {
  const data = (error as { response?: { data?: unknown } } | null)?.response
    ?.data as { message?: unknown; detail?: unknown } | null | undefined;
  if (typeof data?.message === 'string' && data.message.trim())
    return data.message;
  if (typeof data?.detail === 'string' && data.detail.trim())
    return data.detail;
  return undefined;
}

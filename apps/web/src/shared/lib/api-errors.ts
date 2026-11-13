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

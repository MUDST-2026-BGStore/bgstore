import axios, { type AxiosError, type AxiosRequestConfig } from 'axios';
import { csrf } from '../generated/api';

const http = axios.create({
  baseURL: '',
  adapter: 'fetch',
  fetchOptions: { cache: 'no-store' },
  // Resolve fetch at request time (also allows tests to replace it).
  env: { fetch: (...args) => globalThis.fetch(...args) },
});

let onUnauthorized: (() => void) | undefined;

export function configureApiTransport(config: {
  baseUrl?: string;
  onUnauthorized?: () => void;
}): void {
  http.defaults.baseURL = config.baseUrl?.replace(/\/$/, '') ?? '';
  onUnauthorized = config.onUnauthorized;
}

export async function apiRequest<T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> {
  const request = axios.mergeConfig(config, { ...config, ...options });
  if (
    !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(
      (request.method ?? 'GET').toUpperCase(),
    )
  ) {
    const token = await csrf({
      baseURL: request.baseURL,
      signal: request.signal,
    });
    request.headers = axios.mergeConfig(request, {
      headers: { [token.headerName]: token.token },
    }).headers;
  }
  return http.request<T>(request).then(
    ({ data }) => data,
    (error: unknown) => {
      if (axios.isAxiosError(error) && error.response?.status === 401)
        onUnauthorized?.();
      throw error;
    },
  );
}

export type ErrorType<T> = AxiosError<T>;

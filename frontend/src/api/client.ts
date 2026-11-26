import { clearApiKey, getApiKey } from '../auth/keyStore';

export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly errors: string[];
  readonly properties: Record<string, unknown>;

  constructor(
    status: number,
    title: string,
    detail: string,
    errors: string[] = [],
    properties: Record<string, unknown> = {},
  ) {
    super(detail || title);
    this.name = 'ApiError';
    this.status = status;
    this.title = title;
    this.detail = detail;
    this.errors = errors;
    this.properties = properties;
  }

  get reason(): string | undefined {
    const value = this.properties.reason;
    return typeof value === 'string' ? value : undefined;
  }

  get orderId(): string | undefined {
    const value = this.properties.orderId;
    return typeof value === 'string' ? value : undefined;
  }
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  auth?: boolean;
  apiKey?: string;
  signal?: AbortSignal;
}

const KNOWN_PROBLEM_FIELDS = new Set(['type', 'title', 'status', 'detail', 'instance', 'errors']);

async function readProblem(response: Response): Promise<ApiError> {
  let parsed: unknown;
  try {
    const text = await response.text();
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = null;
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return new ApiError(response.status, response.statusText || 'Request failed', '');
  }
  const body = parsed as Record<string, unknown>;
  const errors = Array.isArray(body.errors) ? body.errors.filter((entry): entry is string => typeof entry === 'string') : [];
  const properties: Record<string, unknown> = {};
  for (const [name, value] of Object.entries(body)) {
    if (!KNOWN_PROBLEM_FIELDS.has(name)) {
      properties[name] = value;
    }
  }
  return new ApiError(
    response.status,
    typeof body.title === 'string' ? body.title : response.statusText || 'Request failed',
    typeof body.detail === 'string' ? body.detail : '',
    errors,
    properties,
  );
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, headers = {}, auth = true, apiKey, signal } = options;
  const finalHeaders: Record<string, string> = { Accept: 'application/json', ...headers };
  const key = apiKey ?? (auth ? getApiKey() : null);
  if (key) {
    finalHeaders['X-API-Key'] = key;
  }
  const init: RequestInit = { method, headers: finalHeaders, signal };
  if (body !== undefined) {
    finalHeaders['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(path, init);
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    throw new ApiError(0, 'Network error', 'The server cannot be reached. Check the connection and try again.');
  }

  if (!response.ok) {
    const problem = await readProblem(response);
    if (response.status === 401 && auth && apiKey === undefined) {
      clearApiKey();
    }
    throw problem;
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  for (const [name, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(name, String(value));
    }
  }
  const text = search.toString();
  return text ? `?${text}` : '';
}

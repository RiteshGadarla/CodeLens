export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message)
  }
}

// relative: vite proxy in dev, nginx in docker
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const json = init.body != null && !(init.body instanceof FormData)
  const res = await fetch(`/api${path}`, {
    ...init,
    headers: { Accept: 'application/json', ...(json ? { 'Content-Type': 'application/json' } : {}), ...init.headers },
  })
  if (!res.ok) {
    let message = res.statusText || `HTTP ${res.status}`
    try {
      const body = await res.json()
      message = body.detail ?? body.message ?? message
    } catch {
      // not json
    }
    throw new ApiError(res.status, message)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export const qs = (params: Record<string, string | number | boolean | null | undefined>) => {
  const p = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => v != null && v !== '' && p.set(k, String(v)))
  const s = p.toString()
  return s ? `?${s}` : ''
}

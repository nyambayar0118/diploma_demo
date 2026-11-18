// Small fetch wrapper: JSON in/out, JWT from localStorage, API errors as ApiError {code, message}.

const TOKEN_KEY = 'routeplanner.token'

export class ApiError extends Error {
  status: number
  code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* storage unavailable: the user just has to log in again next time */
  }
}

/** Fired when the server rejects the token (expired / invalid), so the app can log out. */
export const UNAUTHORIZED_EVENT = 'routeplanner:unauthorized'

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (res.status === 401 && token) window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  if (!res.ok) {
    let code = `HTTP_${res.status}`
    let message = res.statusText || 'Алдаа гарлаа'
    try {
      const body = await res.json()
      code = body.code ?? code
      message = body.message ?? message
    } catch {
      /* body was not JSON */
    }
    throw new ApiError(res.status, code, message)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

// ---- Types shared with the backend ----

export type User = {
  id: number
  email: string
  username: string
  displayName: string
  role: 'USER' | 'MODERATOR' | 'ADMIN'
  rewardPoints: number
}

export type AuthResponse = { token: string; user: User }

export type Category = {
  code: string
  nameMn: string
  nameEn: string
  recommendable?: boolean
  children?: Category[]
}

export type ConsentType = 'INTERESTS' | 'LOCATION' | 'LOCATION_SHARING' | 'INTERACTION_HISTORY'
export type Consent = { dataType: ConsentType; purpose: string; retentionDays: number; granted: boolean }

export type TransportMode = 'WALKING' | 'DRIVING' | 'BICYCLE' | 'PUBLIC_TRANSPORT'
export type PriceLevel = 'FREE' | 'LOW' | 'MEDIUM' | 'HIGH'
export type Preferences = {
  transportModes: TransportMode[]
  budgetLevel: PriceLevel
  searchRadiusM: number
  timeBudgetMin: number
  stopCount: number
}

export type OnboardingStatus = { completed: boolean; categoryCodes: string[] }
export type InterestWeight = { category: string; mainCategory: string; weight: number }

export type PlaceSummary = {
  id: number
  name: string
  nameLatin?: string | null
  category: string
  categoryName: string
  mainCategory: string
  lat: number
  lng: number
  distanceM?: number | null
}

export type PlaceDetails = PlaceSummary & {
  nameTraditional?: string | null
  mainCategoryName: string
  recommendable: boolean
  address?: string | null
  building?: string | null
  phone?: string | null
  website?: string | null
  facebook?: string | null
  instagram?: string | null
  openingHours?: string | null
  priceLevel?: PriceLevel | null
  averageRating: number
  reviewCount: number
}

export type MarkType = 'SAVED' | 'HIDDEN'

export function googleMapsUrl(lat: number, lng: number) {
  return `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`
}

/** Fire-and-forget interaction log (only for logged-in users). */
export function logInteraction(placeId: number, type: 'VIEW' | 'OPEN_IN_MAPS' | 'ACCEPT' | 'REJECT') {
  if (!getToken()) return
  api('/api/interactions', { method: 'POST', body: { placeId, type } }).catch(() => undefined)
}

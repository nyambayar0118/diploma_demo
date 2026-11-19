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
  links?: { url: string; type: string; title: string | null; platform: string | null }[]
}

export type MarkType = 'SAVED' | 'HIDDEN'

export function googleMapsUrl(lat: number, lng: number) {
  return `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`
}

/** Fire-and-forget interaction log (only for logged-in users). */
export function logInteraction(placeId: number, type: 'VIEW' | 'OPEN_IN_MAPS' | 'ACCEPT' | 'REJECT',
                               recommendationId?: number) {
  if (!getToken()) return
  api('/api/interactions', { method: 'POST', body: { placeId, type, recommendationId } }).catch(() => undefined)
}

export type RecommendationItem = {
  recommendationId: number
  rank: number
  placeId: number
  name: string
  nameLatin?: string | null
  category: string
  categoryName: string
  mainCategory: string
  lat: number
  lng: number
  distanceM: number
  travelMinutes: number
  score: number
  exploration: boolean
  reason: string
}

export type RecommendationBatch = {
  batchId: number
  ranker: string
  at: string
  availableMinutes: number
  transportMode: TransportMode
  items: RecommendationItem[]
}

// ---- Routes ----
export type RouteStatus = 'CREATED' | 'OPENED_IN_MAPS' | 'COMPLETED' | 'CANCELLED'
export type MapsLink = { url: string; mode: TransportMode; legIndex: number }

export type RouteStop = {
  stopId: number
  order: number
  placeId: number
  name: string
  nameLatin?: string | null
  category: string
  categoryName: string
  mainCategory: string
  lat: number
  lng: number
  travelMinutes: number
  estimatedArrival: string
  stayMinutes: number
  openAtArrival: 'OPEN' | 'CLOSED' | 'UNKNOWN'
  visited: boolean
  skipped: boolean
}

export type RouteView = {
  id: number
  status: RouteStatus
  transportMode: TransportMode
  startLat: number
  startLng: number
  plannedStart: string
  timeBudgetMinutes: number
  estimatedDurationMinutes: number
  overBudget: boolean
  createdAt: string
  stops: RouteStop[]
  mapsLinks: MapsLink[]
  feedbackGiven: boolean
  groupSessionId: number | null
}

export type PendingVisit = {
  stopId: number
  routeId: number
  stopOrder: number
  stopCount: number
  placeId: number
  name: string
  nearby: boolean
}

export type VisitResult = { stopId: number; visited: boolean; routeId: number; routeStatus: RouteStatus }

export function isActiveRoute(r: RouteView | null | undefined): r is RouteView {
  return !!r && (r.status === 'CREATED' || r.status === 'OPENED_IN_MAPS')
}

/** "2026-11-18T15:30:00" -> "15:30" */
export function clockTime(localDateTime: string) {
  return localDateTime.slice(11, 16)
}

export function durationText(minutes: number) {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h === 0 ? `${m} мин` : m === 0 ? `${h} цаг` : `${h} цаг ${m} мин`
}

// ---- Friends and location sharing ----
export type UserRef = { id: number; username: string; displayName: string }
export type Friendship = { id: number; direction: 'FRIEND' | 'INCOMING' | 'OUTGOING'; user: UserRef; since: string }
export type MyLocation = { shared: boolean; lat: number | null; lng: number | null; updatedAt: string | null }
export type FriendLocation = { userId: number; username: string; displayName: string; lat: number; lng: number; updatedAt: string }

/** "5 мин өмнө" */
export function agoText(iso: string) {
  const min = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000))
  return min < 1 ? 'дөнгөж сая' : min < 60 ? `${min} мин өмнө` : `${Math.round(min / 60)} цагийн өмнө`
}

// ---- Where to go after login / onboarding (e.g. back to an invite link) ----
const NEXT_KEY = 'routeplanner:next'
/** Remembers ?next=/path from the login / register URL (only same-site paths). */
export function rememberNext(search: string) {
  const next = new URLSearchParams(search).get('next')
  if (next && next.startsWith('/') && !next.startsWith('//')) sessionStorage.setItem(NEXT_KEY, next)
}
export function takeNext(): string {
  const next = sessionStorage.getItem(NEXT_KEY)
  sessionStorage.removeItem(NEXT_KEY)
  return next ?? '/'
}

// ---- Group planning ----
export type GroupStatus = 'RECOMMENDED' | 'SHARED' | 'COMPLETED' | 'CANCELLED'
export type PlaceRef = { id: number; name: string; categoryName: string; mainCategory: string; lat: number; lng: number }
export type GroupMember = {
  id: number
  role: 'INITIATOR' | 'MEMBER'
  userId: number | null
  name: string
  guest: boolean
  startSource: 'SHARED_LOCATION' | 'PINNED' | 'SAME_AS_INITIATOR' | null
  startLat: number | null
  startLng: number | null
  transportMode: TransportMode
  usesInterests: boolean
  travelMinutes: number | null
  joinedViaLink: boolean
  mapsUrl: string | null
  me: boolean
}
export type GroupView = {
  id: number
  status: GroupStatus
  timeBudgetMinutes: number
  budgetLevel: PriceLevel
  createdAt: string
  meetingPlace: PlaceRef | null
  members: GroupMember[]
  initiator: boolean
  inviteToken: string | null
  inviteExpiresAt: string | null
  myRouteId: number | null
}
export type GroupOption = {
  recommendationId: number
  place: PlaceRef
  score: number
  travelMinutesByMember: Record<string, number>
  maxTravelMinutes: number
}
export type GroupOptions = { batchId: number; aggregator: string; options: GroupOption[] }
export type JoinInfo = { status: GroupStatus; initiatorName: string; memberCount: number; meetingPlace: PlaceRef | null; expiresAt: string | null; valid: boolean }
export type JoinResult = { memberId: number; name: string; meetingPlace: PlaceRef; mapsUrl: string; travelMinutes: number | null; routeId: number | null }

/** Device position (needs https or localhost); null when unavailable or refused. */
export function currentPosition(timeout = 10000) {
  return new Promise<{ lat: number; lng: number } | null>((resolve) => {
    if (!navigator.geolocation) return resolve(null)
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => resolve(null),
      { enableHighAccuracy: true, timeout, maximumAge: 30000 },
    )
  })
}

// ---- Contributions ----
export type RequestType = 'CREATE_PLACE' | 'UPDATE_PLACE' | 'ADD_LINK'
export type LinkType = 'WEBSITE' | 'FACEBOOK' | 'INSTAGRAM' | 'REVIEW_ARTICLE' | 'OTHER'
export type ProposedData = Partial<{
  nameCyrillic: string
  nameLatin: string
  categoryCode: string
  lat: number
  lng: number
  phone: string
  openingHours: string
  street: string
  priceLevel: PriceLevel
  permanentlyClosed: boolean
  url: string
  linkType: LinkType
  linkTitle: string
}>
export type PlaceSnapshot = {
  id: number; name: string; nameLatin: string | null; category: string; categoryName: string; lat: number; lng: number
  phone: string | null; openingHours: string | null; street: string | null; priceLevel: string | null; active: boolean
}
export type ChangeRequest = {
  id: number
  type: RequestType
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  data: ProposedData
  target: PlaceSnapshot | null
  submitterId: number | null
  submitterName: string | null
  moderatorComment: string | null
  submittedAt: string
  reviewedAt: string | null
  pointsGranted: number | null
}
export type Rewards = { total: number; items: { id: number; points: number; reason: string; grantedAt: string }[] }

export const LINK_TYPE_LABELS: Record<LinkType, string> = {
  WEBSITE: 'Вэбсайт', FACEBOOK: 'Facebook', INSTAGRAM: 'Instagram', REVIEW_ARTICLE: 'Сэтгэгдлийн нийтлэл', OTHER: 'Бусад',
}
export const REQUEST_TYPE_LABELS: Record<RequestType, string> = {
  CREATE_PLACE: 'Шинэ газар', UPDATE_PLACE: 'Мэдээлэл засах', ADD_LINK: 'Холбоос нэмэх',
}
export const REQUEST_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Хянагдаж байна', APPROVED: 'Батлагдсан', REJECTED: 'Татгалзсан',
}

import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, clockTime, type PlaceSummary, type Rewards, type RouteView } from '../api'
import { useAuth } from '../auth'
import { MAIN_CATEGORY_COLORS } from '../places'
import Icon from './Icon'
import { useTheme } from '../theme'
import { platform } from '../pwa'

type View = 'menu' | 'saved' | 'hidden' | 'routes'

type Props = {
  open: boolean
  onClose: () => void
  onSelectPlace: (p: PlaceSummary) => void
  onOpenRoute: (r: RouteView) => void
  onAddPlace: () => void
  onInstall: () => void
}

const ROUTE_STATUS: Record<RouteView['status'], string> = {
  CREATED: 'Төлөвлөсөн', OPENED_IN_MAPS: 'Явж байна', COMPLETED: 'Дууссан', CANCELLED: 'Цуцалсан',
}

/** Side menu: the user's own lists (saved, hidden, routes) and every page outside the map. */
export default function SideMenu({ open, onClose, onSelectPlace, onOpenRoute, onAddPlace, onInstall }: Props) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [view, setView] = useState<View>('menu')
  const [theme, setTheme] = useTheme()
  const [saved, setSaved] = useState<PlaceSummary[] | null>(null)
  const [hidden, setHidden] = useState<PlaceSummary[] | null>(null)
  const [routes, setRoutes] = useState<RouteView[] | null>(null)
  const [points, setPoints] = useState<number | null>(null)

  // Fresh numbers every time the menu opens
  useEffect(() => {
    if (!open) {
      setView('menu')
      return
    }
    if (!user) return
    api<PlaceSummary[]>('/api/users/me/saved').then(setSaved).catch(() => setSaved([]))
    api<PlaceSummary[]>('/api/users/me/hidden').then(setHidden).catch(() => setHidden([]))
    api<RouteView[]>('/api/routes').then(setRoutes).catch(() => setRoutes([]))
    api<Rewards>('/api/users/me/rewards').then((r) => setPoints(r.total)).catch(() => undefined)
  }, [open, user])

  // Escape closes the menu
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  const unmark = async (p: PlaceSummary, list: 'saved' | 'hidden') => {
    await api(`/api/places/${p.id}/mark`, { method: 'DELETE' })
    if (list === 'saved') setSaved((s) => s?.filter((x) => x.id !== p.id) ?? null)
    else setHidden((s) => s?.filter((x) => x.id !== p.id) ?? null)
  }

  const go = (path: string) => {
    onClose()
    navigate(path)
  }

  const moderator = user?.role === 'MODERATOR' || user?.role === 'ADMIN'
  const activeRoutes = routes?.filter((r) => r.status === 'CREATED' || r.status === 'OPENED_IN_MAPS').length ?? 0

  return (
    <>
      <div className={`drawer-backdrop ${open ? 'open' : ''}`} onClick={onClose} />
      <nav className={`drawer ${open ? 'open' : ''}`} aria-hidden={!open} aria-label="Цэс">
        {view === 'menu' ? (
          <>
            <div className="drawer-head">
              {user ? (
                <Link to="/settings" className="drawer-user" onClick={onClose}>
                  <span className="avatar">{user.displayName.trim().charAt(0).toUpperCase()}</span>
                  <span className="r-main">
                    <span className="r-name">{user.displayName}</span>
                    <span className="r-sub">@{user.username}{points != null ? ` · ${points} оноо` : ''}</span>
                  </span>
                </Link>
              ) : (
                <div className="drawer-guest">
                  <div className="drawer-brand">Ухаалаг маршрут</div>
                  <p className="muted" style={{ margin: '4px 0 12px' }}>
                    Нэвтэрч сонирхолд тань тохирсон газар, маршрутын санал аваарай.
                  </p>
                  <div className="row" style={{ justifyContent: 'flex-start', gap: 8 }}>
                    <button className="btn" onClick={() => go('/login')}>Нэвтрэх</button>
                    <button className="btn secondary" onClick={() => go('/register')}>Бүртгүүлэх</button>
                  </div>
                </div>
              )}
              <button className="drawer-close" onClick={onClose} aria-label="Хаах">×</button>
            </div>

            {user && (
              <>
                <div className="drawer-section">
                  <Item icon={<Icon name="star" />} label="Хадгалсан газрууд" count={saved?.length} onClick={() => setView('saved')} />
                  <Item icon={<Icon name="eyeOff" />} label="Нуусан газрууд" count={hidden?.length} onClick={() => setView('hidden')} />
                  <Item icon={<Icon name="route" />} label="Миний маршрутууд" count={activeRoutes || undefined} highlight={activeRoutes > 0}
                        onClick={() => setView('routes')} />
                </div>
                <div className="drawer-section">
                  <Item icon={<Icon name="users" />} label="Найзууд" onClick={() => go('/friends')} />
                  <Item icon={<Icon name="meet" />} label="Бүлгээр төлөвлөх" onClick={() => go('/groups')} />
                  <Item icon={<Icon name="plus" />} label="Газар нэмэх" onClick={() => { onClose(); onAddPlace() }} />
                </div>
                {moderator && (
                  <div className="drawer-section">
                    <Item icon={<Icon name="check" />} label="Хүсэлт хянах" onClick={() => go('/moderation')} />
                    {user.role === 'ADMIN' && <Item icon={<Icon name="chart" />} label="Хяналтын самбар" onClick={() => go('/admin')} />}
                  </div>
                )}
                <div className="drawer-section">
                  <Item icon={<Icon name="settings" />} label="Тохиргоо" onClick={() => go('/settings')} />
                  <Item icon={<Icon name="logout" />} label="Гарах" onClick={() => { onClose(); logout() }} />
                </div>
              </>
            )}
            {platform() !== 'installed' && (
              <div className="drawer-section">
                <Item icon={<Icon name="download" />} label="Утсандаа суулгах" onClick={() => { onClose(); onInstall() }} />
              </div>
            )}
            <div className="drawer-foot">
              <label className="theme-toggle">
                <span className="drawer-icon"><Icon name={theme === 'dark' ? 'moon' : 'sun'} /></span>
                <span className="drawer-label">Харанхуй горим</span>
                <input type="checkbox" className="switch" checked={theme === 'dark'}
                       onChange={(e) => setTheme(e.target.checked ? 'dark' : 'light')} />
              </label>
              <div className="drawer-credit">Газрын мэдээлэл © OpenStreetMap-ийн хувь нэмэрлэгчид, © Minu Map</div>
            </div>
          </>
        ) : (
          <>
            <div className="drawer-head sub">
              <button className="drawer-back" onClick={() => setView('menu')} aria-label="Буцах">←</button>
              <strong>{view === 'saved' ? 'Хадгалсан газрууд' : view === 'hidden' ? 'Нуусан газрууд' : 'Миний маршрутууд'}</strong>
              <button className="drawer-close" onClick={onClose} aria-label="Хаах">×</button>
            </div>
            <div className="drawer-list">
              {view === 'saved' && (
                <PlaceList places={saved} empty="Газрын мэдээллээс “Хадгалах” дарж нэмнэ."
                           actionLabel="Хасах" onAction={(p) => unmark(p, 'saved')}
                           onOpen={(p) => { onClose(); onSelectPlace(p) }} />
              )}
              {view === 'hidden' && (
                <>
                  <p className="muted drawer-note">Эдгээр газрыг танд санал болгохгүй. “Буцаах” дарвал дахин санал болгоно.</p>
                  <PlaceList places={hidden} empty="Нуусан газар алга."
                             actionLabel="Буцаах" onAction={(p) => unmark(p, 'hidden')}
                             onOpen={(p) => { onClose(); onSelectPlace(p) }} />
                </>
              )}
              {view === 'routes' && (
                routes == null ? <p className="muted drawer-note">Ачаалж байна…</p>
                  : routes.length === 0 ? <p className="muted drawer-note">“Санал болгох”-оос газар сонгоод маршрут үүсгэнэ.</p>
                  : (
                    <ul className="drawer-items">
                      {routes.map((r) => (
                        <li key={r.id}>
                          <button className="drawer-row" onClick={() => { onClose(); onOpenRoute(r) }}>
                            <span className="r-main">
                              <span className="r-name">{r.stops.map((s) => s.name).join(' → ')}</span>
                              <span className="r-sub">
                                {new Date(r.createdAt).toLocaleDateString('mn-MN')} · {clockTime(r.plannedStart)} ·{' '}
                                {r.stops.length} зогсоол{r.groupSessionId ? ' · бүлэг' : ''}
                              </span>
                            </span>
                            <span className={`badge status-${r.status.toLowerCase()}`}>{ROUTE_STATUS[r.status]}</span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )
              )}
            </div>
          </>
        )}
      </nav>
    </>
  )
}

function Item({ icon, label, count, highlight, onClick }:
  { icon: ReactNode; label: string; count?: number; highlight?: boolean; onClick: () => void }) {
  return (
    <button className="drawer-item" onClick={onClick}>
      <span className="drawer-icon" aria-hidden>{icon}</span>
      <span className="drawer-label">{label}</span>
      {count != null && count > 0 && <span className={`drawer-count ${highlight ? 'hl' : ''}`}>{count}</span>}
    </button>
  )
}

function PlaceList({ places, empty, actionLabel, onAction, onOpen }: {
  places: PlaceSummary[] | null
  empty: string
  actionLabel: string
  onAction: (p: PlaceSummary) => void
  onOpen: (p: PlaceSummary) => void
}) {
  if (places == null) return <p className="muted drawer-note">Ачаалж байна…</p>
  if (places.length === 0) return <p className="muted drawer-note">{empty}</p>
  return (
    <ul className="drawer-items">
      {places.map((p) => (
        <li key={p.id}>
          <button className="drawer-row" onClick={() => onOpen(p)}>
            <span className="dot" style={{ background: MAIN_CATEGORY_COLORS[p.mainCategory] ?? '#888' }} />
            <span className="r-main">
              <span className="r-name">{p.name}</span>
              <span className="r-sub">{p.categoryName}</span>
            </span>
          </button>
          <button className="link drawer-action" onClick={() => onAction(p)}>{actionLabel}</button>
        </li>
      ))}
    </ul>
  )
}

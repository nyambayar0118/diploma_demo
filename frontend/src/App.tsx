// Аппликейшний хуудсуудын замчлал: URL хаяг бүрд харгалзах хуудас.
import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider, useAuth } from './auth'
import MapPage from './pages/MapPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import OnboardingPage from './pages/OnboardingPage'
import ProfilePage from './pages/ProfilePage'
import FriendsPage from './pages/FriendsPage'
import FriendInvitePage from './pages/FriendInvitePage'
import GroupsPage from './pages/GroupsPage'
import GroupPage from './pages/GroupPage'
import JoinPage from './pages/JoinPage'
import ModerationPage from './pages/ModerationPage'
import AdminPage from './pages/AdminPage'

/** Нэвтрээгүй хэрэглэгчийг нэвтрэх хуудас руу шилжүүлнэ. */
function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return null
  const next = encodeURIComponent(window.location.pathname + window.location.search)
  return user ? children : <Navigate to={`/login?next=${next}`} replace />
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<MapPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/onboarding" element={<RequireAuth><OnboardingPage /></RequireAuth>} />
          <Route path="/profile" element={<RequireAuth><ProfilePage /></RequireAuth>} />

          <Route path="/settings" element={<RequireAuth><ProfilePage /></RequireAuth>} />
          <Route path="/friends" element={<RequireAuth><FriendsPage /></RequireAuth>} />
          <Route path="/invite/friend/:token" element={<FriendInvitePage />} />
          <Route path="/groups" element={<RequireAuth><GroupsPage /></RequireAuth>} />
          <Route path="/groups/:id" element={<RequireAuth><GroupPage /></RequireAuth>} />
          <Route path="/join/:token" element={<JoinPage />} />
          <Route path="/moderation" element={<RequireAuth><ModerationPage /></RequireAuth>} />
          <Route path="/admin" element={<RequireAuth><AdminPage /></RequireAuth>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

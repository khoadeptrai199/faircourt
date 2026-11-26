import { Route, Routes } from 'react-router'
import BackHomeLink from '../components/BackHomeLink'
import EmptyState from '../components/EmptyState'
import Logo from '../components/Logo'
import { copy } from '../copy/en'
import HomePage from '../features/home/HomePage'
import HostBoardPage from '../features/host-board/HostBoardPage'
import JoinPage from '../features/join/JoinPage'
import LoginPage from '../features/login/LoginPage'
import ForgotPasswordPage from '../features/password-reset/ForgotPasswordPage'
import ResetPasswordPage from '../features/password-reset/ResetPasswordPage'
import MyGamePage from '../features/my-game/MyGamePage'
import PublicGamePage from '../features/public-game/PublicGamePage'

// Route table (blueprint section 16, with "session" renamed to "game", OD-22). More routes come with later features.
export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/g/:publicCode" element={<PublicGamePage />} />
      <Route path="/g/:publicCode/join" element={<JoinPage />} />
      <Route path="/my/games/:gameId" element={<MyGamePage />} />
      <Route path="/host/games/:gameId" element={<HostBoardPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="*" element={<PageNotFound />} />
    </Routes>
  )
}

function PageNotFound() {
  return (
    <div className="min-h-dvh bg-background text-ink">
      <header className="mx-auto flex max-w-[1080px] items-center px-5 pt-4 pb-2 md:px-6 md:pt-6">
        <Logo />
      </header>
      <main className="mx-auto max-w-[720px] px-4 py-6 md:py-10">
        <EmptyState
          title={copy.common.pageNotFoundTitle}
          action={<BackHomeLink />}
        />
      </main>
    </div>
  )
}

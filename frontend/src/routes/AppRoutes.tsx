import { Route, Routes } from 'react-router'
import BackHomeLink from '../components/BackHomeLink'
import EmptyState from '../components/EmptyState'
import Logo from '../components/Logo'
import { copy } from '../copy/en'
import HomePage from '../features/home/HomePage'
import LoginPage from '../features/login/LoginPage'
import PublicGamePage from '../features/public-game/PublicGamePage'

// Route table (blueprint section 16, with "session" renamed to "game", OD-22). More routes come with later features.
export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/g/:publicCode" element={<PublicGamePage />} />
      <Route path="/login" element={<LoginPage />} />
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

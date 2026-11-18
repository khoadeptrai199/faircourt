import { Route, Routes } from 'react-router'
import BackHomeLink from '../components/BackHomeLink'
import EmptyState from '../components/EmptyState'
import { copy } from '../copy/en'
import HomePage from '../features/home/HomePage'
import PublicGamePage from '../features/public-game/PublicGamePage'

// Route table (blueprint section 16, with "session" renamed to "game", OD-22). More routes come with later features.
export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/g/:publicCode" element={<PublicGamePage />} />
      <Route path="*" element={<PageNotFound />} />
    </Routes>
  )
}

function PageNotFound() {
  return (
    <main className="mx-auto max-w-[720px] px-4 py-6 md:py-10">
      <EmptyState
        title={copy.common.pageNotFoundTitle}
        action={<BackHomeLink />}
      />
    </main>
  )
}

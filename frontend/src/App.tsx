import { BrowserRouter } from 'react-router'
import AppRoutes from './routes/AppRoutes'

// Router: react-router, basic declarative use only (owner decision 2026-11-18, OD-01).
export default function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  )
}

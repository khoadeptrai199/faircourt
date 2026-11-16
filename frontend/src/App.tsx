// Real routes (/s/:publicCode, /my/..., /host/...) arrive in later slices (blueprint section 16).
// Until a router is approved, the app renders the home page only.
import HomePage from './features/home/HomePage'

export default function App() {
  return <HomePage />
}

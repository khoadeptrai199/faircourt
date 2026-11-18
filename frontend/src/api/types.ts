// API data types. Names mirror the backend records without the "Response" suffix (conventions).

export type GameState = 'OPEN' | 'CLOSED'

export type MatchFormat = 'SINGLES' | 'DOUBLES'

export type PriceRange = {
  minVnd: number
  maxVnd: number
}

export type PricePerPerson = {
  male: PriceRange | null
  female: PriceRange | null
}

// GET /api/v1/public/games/{publicCode}: public summary, no names or private data.
export type PublicGame = {
  gameId: string
  title: string
  venue: string
  startsAt: string
  endsAt: string
  courtCount: number
  state: GameState
  matchFormat: MatchFormat
  minSets: number
  pricePerPerson: PricePerPerson | null
}

// POST /api/v1/auth/login and GET /api/v1/me: the signed-in person's own identity.
export type Account = {
  userId: string
  email: string
}

export type ApiErrorBody = {
  code: string
  message: string
  requestId: string
}

// POST /api/v1/public/games/{publicCode}/guests: guest check-in (Guest check-in, design section 3).
// Each answer is 0-3 (Still learning ... Consistently); "answers: null" means "not sure about my level".
export type SkillAnswers = {
  serve: number
  rally: number
  movement: number
  positioning: number
}

export type GuestCheckInRequest = {
  displayName: string
  answers: SkillAnswers | null
}

export type AttendanceState =
  'PENDING' | 'READY' | 'ASSIGNED' | 'PLAYING' | 'RESTING' | 'LEFT'

export type GuestCheckIn = {
  attendanceState: AttendanceState
  message: string | null
}

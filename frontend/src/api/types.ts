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

export type ApiErrorBody = {
  code: string
  message: string
  requestId: string
}

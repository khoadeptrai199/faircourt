import type {
  CreateGameInput,
  CreateGamePriceLine,
  MatchFormat,
} from '../../api/types'

// Form state, field checks and Vietnam-time conversion for the create-game page (design section 7).
// The checks mirror the server rules (design section 3.1, OD-26); the server still validates.

const ZONE = 'Asia/Ho_Chi_Minh'
const OFFSET = '+07:00'
const MAX_HOURS = 12
const MAX_PRICE = 10_000_000

export type PriceText = { min: string; max: string }

export type FormValues = {
  title: string
  venue: string
  day: string
  starts: string
  ends: string
  courtCount: number
  matchFormat: MatchFormat
  minSets: string
  male: PriceText
  female: PriceText
}

export type FieldKey =
  'title' | 'venue' | 'day' | 'starts' | 'ends' | 'minSets' | 'male' | 'female'

export type FieldError =
  | { kind: 'required' }
  | { kind: 'tooLong'; max: number }
  | { kind: 'timeWindow' }
  | { kind: 'minSets' }
  | { kind: 'price' }

export type FieldErrors = Partial<Record<FieldKey, FieldError>>

// Order on the page; the first invalid one gets the focus.
export const FIELD_ORDER: FieldKey[] = [
  'title',
  'venue',
  'day',
  'starts',
  'ends',
  'minSets',
  'male',
  'female',
]

const dayParts = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

export function todayInVietnam(now: Date = new Date()): string {
  return dayParts.format(now)
}

export function initialValues(): FormValues {
  return {
    title: '',
    venue: '',
    day: todayInVietnam(),
    starts: '18:00',
    ends: '21:00',
    courtCount: 1,
    matchFormat: 'DOUBLES',
    minSets: '6',
    male: { min: '', max: '' },
    female: { min: '', max: '' },
  }
}

// The end is on the next day when Ends is earlier than or equal to Starts (design D-2).
export function endsNextDay(values: FormValues): boolean {
  return (
    values.starts !== '' && values.ends !== '' && values.ends <= values.starts
  )
}

function addOneDay(day: string): string {
  const [year, month, date] = day.split('-').map(Number)
  const next = new Date(Date.UTC(year, month - 1, date + 1))
  return next.toISOString().slice(0, 10)
}

const DAY_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const TIME_PATTERN = /^\d{2}:\d{2}$/

// { startsAt, endsAt } as ISO strings with +07:00, or null while day or times are not filled in.
export function toInstants(
  values: FormValues,
): { startsAt: string; endsAt: string } | null {
  if (
    !DAY_PATTERN.test(values.day) ||
    !TIME_PATTERN.test(values.starts) ||
    !TIME_PATTERN.test(values.ends)
  ) {
    return null
  }
  const endDay = endsNextDay(values) ? addOneDay(values.day) : values.day
  const startsAt = `${values.day}T${values.starts}:00${OFFSET}`
  const endsAt = `${endDay}T${values.ends}:00${OFFSET}`
  if (Number.isNaN(Date.parse(startsAt)) || Number.isNaN(Date.parse(endsAt))) {
    return null
  }
  return { startsAt, endsAt }
}

type ParsedPrice =
  { ok: true; line: CreateGamePriceLine | null } | { ok: false }

function parseBound(text: string): number | null | 'invalid' {
  const trimmed = text.trim()
  if (trimmed === '') return null
  if (!/^\d+$/.test(trimmed)) return 'invalid'
  const value = Number(trimmed)
  return value <= MAX_PRICE ? value : 'invalid'
}

function parsePrice(price: PriceText): ParsedPrice {
  const min = parseBound(price.min)
  const max = parseBound(price.max)
  if (min === 'invalid' || max === 'invalid') return { ok: false }
  if (min === null && max === null) return { ok: true, line: null }
  if (min !== null && max !== null && min > max) return { ok: false }
  return {
    ok: true,
    line: {
      ...(min !== null ? { minVnd: min } : {}),
      ...(max !== null ? { maxVnd: max } : {}),
    },
  }
}

function parseMinSets(text: string): number | null {
  const trimmed = text.trim()
  if (!/^\d+$/.test(trimmed)) return null
  const value = Number(trimmed)
  return value >= 1 && value <= 20 ? value : null
}

export function validate(
  values: FormValues,
  now: Date = new Date(),
): FieldErrors {
  const errors: FieldErrors = {}

  const title = values.title.trim()
  if (title === '') errors.title = { kind: 'required' }
  else if (title.length > 120) errors.title = { kind: 'tooLong', max: 120 }

  const venue = values.venue.trim()
  if (venue === '') errors.venue = { kind: 'required' }
  else if (venue.length > 160) errors.venue = { kind: 'tooLong', max: 160 }

  if (values.day === '') errors.day = { kind: 'required' }
  if (values.starts === '') errors.starts = { kind: 'required' }
  if (values.ends === '') errors.ends = { kind: 'required' }
  if (!errors.day && !errors.starts && !errors.ends) {
    const instants = toInstants(values)
    if (!instants) {
      errors.ends = { kind: 'timeWindow' }
    } else {
      const start = Date.parse(instants.startsAt)
      const end = Date.parse(instants.endsAt)
      if (end <= now.getTime() || end - start > MAX_HOURS * 3_600_000) {
        errors.ends = { kind: 'timeWindow' }
      }
    }
  }

  if (parseMinSets(values.minSets) === null) {
    errors.minSets = { kind: 'minSets' }
  }
  if (!parsePrice(values.male).ok) errors.male = { kind: 'price' }
  if (!parsePrice(values.female).ok) errors.female = { kind: 'price' }

  return errors
}

// Request body from valid values (call after validate returned no errors).
export function toInput(values: FormValues): CreateGameInput | null {
  const instants = toInstants(values)
  const minSets = parseMinSets(values.minSets)
  const male = parsePrice(values.male)
  const female = parsePrice(values.female)
  if (!instants || minSets === null || !male.ok || !female.ok) return null
  const hasPrice = male.line !== null || female.line !== null
  return {
    title: values.title.trim(),
    venue: values.venue.trim(),
    startsAt: instants.startsAt,
    endsAt: instants.endsAt,
    courtCount: values.courtCount,
    matchFormat: values.matchFormat,
    minSets,
    pricePerPerson: hasPrice
      ? {
          ...(male.line ? { male: male.line } : {}),
          ...(female.line ? { female: female.line } : {}),
        }
      : null,
  }
}

// Price lines for the preview line (both bounds known; one bound alone shows as one number).
export function previewPrice(
  price: PriceText,
): { minVnd: number; maxVnd: number } | null {
  const parsed = parsePrice(price)
  if (!parsed.ok || !parsed.line) return null
  const min = parsed.line.minVnd ?? parsed.line.maxVnd
  const max = parsed.line.maxVnd ?? parsed.line.minVnd
  return min === undefined || max === undefined
    ? null
    : { minVnd: min, maxVnd: max }
}

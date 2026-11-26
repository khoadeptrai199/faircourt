import { useState, type FormEvent, type ReactNode } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { createGame } from '../../api/games'
import Button from '../../components/Button'
import ErrorBanner from '../../components/ErrorBanner'
import Logo from '../../components/Logo'
import TextField from '../../components/TextField'
import { copy } from '../../copy/en'
import { useAccount } from '../../hooks/useAccount'
import { useMutation } from '../../hooks/useMutation'
import { formatVndRange } from '../../lib/money'
import { formatGameTime } from '../../lib/time'
import {
  FIELD_ORDER,
  endsNextDay,
  initialValues,
  previewPrice,
  toInput,
  toInstants,
  validate,
  type FieldError,
  type FieldErrors,
  type FieldKey,
  type FormValues,
  type PriceText,
} from './gameForm'

const t = copy.createGame
const LOGIN_PATH = `/login?next=${encodeURIComponent('/host/games/new')}`

// Where the focus goes for each field (price lines start at their "From" input).
const FOCUS_ID: Record<FieldKey, string> = {
  title: 'cg-title',
  venue: 'cg-venue',
  day: 'cg-day',
  starts: 'cg-starts',
  ends: 'cg-ends',
  minSets: 'cg-minSets',
  male: 'cg-male-min',
  female: 'cg-female-min',
}

function errorText(error: FieldError): string {
  switch (error.kind) {
    case 'required':
      return t.errorRequired
    case 'tooLong':
      return t.errorTooLong(error.max)
    case 'timeWindow':
      return t.errorTimeWindow
    case 'minSets':
      return t.errorMinSets
    case 'price':
      return t.errorPrice
    default: {
      const unreachable: never = error
      return unreachable
    }
  }
}

// /host/games/new: any signed-in account posts a game and becomes its host (blueprint section 13; design section 7).
export default function CreateGamePage() {
  const account = useAccount()
  return (
    <div className="min-h-dvh bg-background text-ink">
      <header className="mx-auto flex max-w-[720px] items-center px-5 pt-4 pb-2 md:px-6 md:pt-6 md:pb-3">
        <Logo />
      </header>
      <main className="mx-auto max-w-[720px] px-4 pt-2 pb-12 md:px-6">
        {(() => {
          switch (account.status) {
            case 'loading':
              return (
                <p role="status" className="text-base">
                  {copy.common.loading}
                </p>
              )
            case 'signedOut':
              return <Navigate to={LOGIN_PATH} replace />
            case 'signedIn':
              return <CreateGameForm />
            case 'error':
              return <ErrorBanner onRetry={account.reload} />
            default: {
              const unreachable: never = account
              return unreachable
            }
          }
        })()}
      </main>
    </div>
  )
}

function FieldMessage({ id, error }: { id: string; error?: FieldError }) {
  if (!error) return null
  return (
    <p id={id} className="mt-1 text-sm font-bold text-error">
      {errorText(error)}
    </p>
  )
}

function CreateGameForm() {
  const navigate = useNavigate()
  const [values, setValues] = useState<FormValues>(initialValues)
  const [touched, setTouched] = useState<Partial<Record<FieldKey, boolean>>>({})
  const [submitted, setSubmitted] = useState(false)
  const create = useMutation(createGame)

  const errors: FieldErrors = validate(values)
  const shown = (key: FieldKey): FieldError | undefined =>
    submitted || touched[key] ? errors[key] : undefined
  const saving = create.status === 'saving'

  const set = <K extends keyof FormValues>(key: K, value: FormValues[K]) =>
    setValues((current) => ({ ...current, [key]: value }))
  const blur = (key: FieldKey) =>
    setTouched((current) => ({ ...current, [key]: true }))
  const setPrice = (
    line: 'male' | 'female',
    part: keyof PriceText,
    v: string,
  ) =>
    setValues((current) => ({
      ...current,
      [line]: { ...current[line], [part]: v },
    }))

  const submit = async () => {
    setSubmitted(true)
    const firstInvalid = FIELD_ORDER.find((key) => errors[key])
    if (firstInvalid) {
      document.getElementById(FOCUS_ID[firstInvalid])?.focus()
      return
    }
    const input = toInput(values)
    if (!input) return
    const created = await create.run(input)
    if (created) navigate(`/host/games/${created.gameId}`)
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    void submit()
  }

  const describe = (key: FieldKey, extra?: string) => {
    const ids = [extra, shown(key) ? `cg-${key}-error` : undefined].filter(
      Boolean,
    )
    return ids.length > 0 ? ids.join(' ') : undefined
  }

  const nextDay = endsNextDay(values)
  const instants = toInstants(values)

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
      <fieldset disabled={saving} className="flex min-w-0 flex-col gap-5">
        <div>
          <h1 className="text-[28px] font-black tracking-tight md:text-4xl">
            {t.title}
          </h1>
          <p className="mt-2 text-base text-muted">{t.intro}</p>
        </div>

        <div>
          <TextField
            id="cg-title"
            label={t.titleLabel}
            value={values.title}
            autoComplete="off"
            aria-invalid={shown('title') ? true : undefined}
            aria-describedby={describe('title', 'cg-title-help')}
            onChange={(e) => set('title', e.target.value)}
            onBlur={() => blur('title')}
          />
          <p id="cg-title-help" className="mt-1 text-sm text-subtle">
            {t.titleHelp}
          </p>
          <FieldMessage id="cg-title-error" error={shown('title')} />
        </div>

        <div>
          <TextField
            id="cg-venue"
            label={t.venueLabel}
            value={values.venue}
            autoComplete="off"
            aria-invalid={shown('venue') ? true : undefined}
            aria-describedby={describe('venue')}
            onChange={(e) => set('venue', e.target.value)}
            onBlur={() => blur('venue')}
          />
          <FieldMessage id="cg-venue-error" error={shown('venue')} />
        </div>

        <div>
          <TextField
            id="cg-day"
            type="date"
            label={t.dayLabel}
            value={values.day}
            aria-invalid={shown('day') ? true : undefined}
            aria-describedby={describe('day')}
            onChange={(e) => set('day', e.target.value)}
            onBlur={() => blur('day')}
          />
          <FieldMessage id="cg-day-error" error={shown('day')} />
        </div>

        <div>
          <div className="grid grid-cols-2 gap-3">
            <TextField
              id="cg-starts"
              type="time"
              label={t.startsLabel}
              value={values.starts}
              aria-invalid={shown('starts') ? true : undefined}
              aria-describedby={describe('starts')}
              onChange={(e) => set('starts', e.target.value)}
              onBlur={() => blur('starts')}
            />
            <TextField
              id="cg-ends"
              type="time"
              label={t.endsLabel}
              value={values.ends}
              aria-invalid={shown('ends') ? true : undefined}
              aria-describedby={describe(
                'ends',
                nextDay ? 'cg-ends-next-day' : undefined,
              )}
              onChange={(e) => set('ends', e.target.value)}
              onBlur={() => blur('ends')}
            />
          </div>
          {nextDay && (
            <p id="cg-ends-next-day" className="mt-1 text-sm text-subtle">
              {t.endsNextDay}
            </p>
          )}
          <FieldMessage id="cg-starts-error" error={shown('starts')} />
          <FieldMessage id="cg-ends-error" error={shown('ends')} />
        </div>

        <RadioGroup legend={t.courtsLabel}>
          {[1, 2, 3, 4].map((n) => (
            <RadioButton
              key={n}
              name="cg-courts"
              label={String(n)}
              checked={values.courtCount === n}
              onSelect={() => set('courtCount', n)}
            />
          ))}
        </RadioGroup>

        <RadioGroup legend={t.formatLabel}>
          <RadioButton
            name="cg-format"
            label={copy.publicGame.formatDoubles}
            checked={values.matchFormat === 'DOUBLES'}
            onSelect={() => set('matchFormat', 'DOUBLES')}
          />
          <RadioButton
            name="cg-format"
            label={copy.publicGame.formatSingles}
            checked={values.matchFormat === 'SINGLES'}
            onSelect={() => set('matchFormat', 'SINGLES')}
          />
        </RadioGroup>

        <div>
          <TextField
            id="cg-minSets"
            type="number"
            inputMode="numeric"
            min={1}
            max={20}
            label={t.minSetsLabel}
            value={values.minSets}
            aria-invalid={shown('minSets') ? true : undefined}
            aria-describedby={describe('minSets', 'cg-minSets-help')}
            onChange={(e) => set('minSets', e.target.value)}
            onBlur={() => blur('minSets')}
          />
          <p id="cg-minSets-help" className="mt-1 text-sm text-subtle">
            {t.minSetsHelp}
          </p>
          <FieldMessage id="cg-minSets-error" error={shown('minSets')} />
        </div>

        <fieldset className="flex min-w-0 flex-col gap-3">
          <legend className="text-sm font-bold">{t.priceLabel}</legend>
          <p id="cg-price-help" className="text-sm text-subtle">
            {t.priceHelp}
          </p>
          {(['male', 'female'] as const).map((line) => (
            <div key={line}>
              <p className="mb-1.5 text-sm font-bold">
                {line === 'male' ? t.priceMen : t.priceWomen}
              </p>
              <div className="grid grid-cols-2 gap-3">
                <TextField
                  id={`cg-${line}-min`}
                  type="number"
                  min={0}
                  inputMode="numeric"
                  label={t.priceFrom}
                  value={values[line].min}
                  aria-invalid={shown(line) ? true : undefined}
                  aria-describedby={describe(line, 'cg-price-help')}
                  onChange={(e) => setPrice(line, 'min', e.target.value)}
                  onBlur={() => blur(line)}
                />
                <TextField
                  id={`cg-${line}-max`}
                  type="number"
                  min={0}
                  inputMode="numeric"
                  label={t.priceTo}
                  value={values[line].max}
                  aria-invalid={shown(line) ? true : undefined}
                  aria-describedby={describe(line, 'cg-price-help')}
                  onChange={(e) => setPrice(line, 'max', e.target.value)}
                  onBlur={() => blur(line)}
                />
              </div>
              <FieldMessage id={`cg-${line}-error`} error={shown(line)} />
            </div>
          ))}
        </fieldset>

        {instants && (
          <Preview
            values={values}
            startsAt={instants.startsAt}
            endsAt={instants.endsAt}
          />
        )}

        <SubmitResult status={create.status} />

        <Button type="submit" disabled={saving} className="min-h-12 w-full">
          {saving ? t.saving : t.submit}
        </Button>
      </fieldset>
    </form>
  )
}

function Preview({
  values,
  startsAt,
  endsAt,
}: {
  values: FormValues
  startsAt: string
  endsAt: string
}) {
  const time = formatGameTime(startsAt, endsAt)
  const format =
    values.matchFormat === 'DOUBLES'
      ? copy.publicGame.formatDoubles
      : copy.publicGame.formatSingles
  const parts = [
    time.day,
    `${time.start}–${time.end}`,
    `${values.courtCount} ${copy.publicGame.courtWord(values.courtCount)}`,
    format,
  ]
  const men = previewPrice(values.male)
  const women = previewPrice(values.female)
  if (men) parts.push(`${copy.publicGame.priceMale} ${formatVndRange(men)}`)
  if (women) {
    parts.push(`${copy.publicGame.priceFemale} ${formatVndRange(women)}`)
  }
  return (
    <p
      aria-live="polite"
      className="rounded-[18px] bg-highlight px-4 py-3 text-base font-bold"
    >
      {parts.join(' · ')}
    </p>
  )
}

function RadioGroup({
  legend,
  children,
}: {
  legend: string
  children: ReactNode
}) {
  return (
    <fieldset className="min-w-0">
      <legend className="mb-1.5 text-sm font-bold">{legend}</legend>
      <div className="flex flex-wrap gap-2">{children}</div>
    </fieldset>
  )
}

function RadioButton({
  name,
  label,
  checked,
  onSelect,
}: {
  name: string
  label: string
  checked: boolean
  onSelect: () => void
}) {
  return (
    <label className="relative flex-1">
      <input
        type="radio"
        name={name}
        checked={checked}
        onChange={onSelect}
        className="peer sr-only"
      />
      <span className="flex min-h-12 min-w-14 cursor-pointer items-center justify-center rounded-xl border-2 border-ink/25 bg-surface px-4 text-base font-extrabold peer-checked:border-action peer-checked:bg-action peer-checked:text-white peer-focus-visible:outline-3 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-action peer-disabled:cursor-not-allowed peer-disabled:opacity-60">
        {label}
      </span>
    </label>
  )
}

// Result of the last submit, rendered from the mutation status (conventions, section 16). The "Create game" button is
// the retry, so the banner has no Retry button (design section 7).
function SubmitResult({
  status,
}: {
  status: 'idle' | 'saving' | 'success' | 'conflict' | 'error'
}) {
  switch (status) {
    case 'idle':
    case 'saving':
    case 'success':
    case 'conflict':
      // saving: the button text says it; success: the page navigates away; conflict: the server sends no 409 here.
      return null
    case 'error':
      return (
        <div
          role="alert"
          className="flex items-center gap-3 rounded-[20px] border-2 border-error bg-surface p-4"
        >
          <span
            aria-hidden="true"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-error-soft text-error"
          >
            <svg
              viewBox="0 0 24 24"
              className="h-[22px] w-[22px]"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M12 3 2.5 20h19z" />
              <path d="M12 10v4" />
              <path d="M12 17.5v.01" />
            </svg>
          </span>
          <p className="text-base font-extrabold text-error">{t.errorFailed}</p>
        </div>
      )
    default: {
      const unreachable: never = status
      return unreachable
    }
  }
}

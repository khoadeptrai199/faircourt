import { useCallback, useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../api/errors'
import { getPublicGame } from '../../api/games'
import { checkInAsGuest } from '../../api/guests'
import type { GuestCheckInRequest, SkillAnswers } from '../../api/types'
import AuthPageShell from '../../components/AuthPageShell'
import BackHomeLink from '../../components/BackHomeLink'
import Button from '../../components/Button'
import ErrorBanner from '../../components/ErrorBanner'
import TextField from '../../components/TextField'
import { copy } from '../../copy/en'
import { useAsync } from '../../hooks/useAsync'
import { useMutation } from '../../hooks/useMutation'
import QuestionStep from './QuestionStep'

const t = copy.join

const QUESTION_COUNT = 4

type Send = { publicCode: string; request: GuestCheckInRequest }
const send = ({ publicCode, request }: Send) =>
  checkInAsGuest(publicCode, request)

// All four answered -> the four values as they are; otherwise "not sure" (null).
function toSkillAnswers(values: (number | null)[]): SkillAnswers | null {
  const [serve, rally, movement, positioning] = values
  if (
    serve === null ||
    rally === null ||
    movement === null ||
    positioning === null
  ) {
    return null
  }
  return { serve, rally, movement, positioning }
}

function statusOf(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null
}

// /g/:publicCode/join: guest check-in (Guest check-in, design section 7). Step 0 = name, steps 1-4 = the four
// skill questions, then the "waiting for host approval" panel. Joining again returns the existing entry.
export default function JoinPage() {
  const { publicCode = '' } = useParams()
  const [step, setStep] = useState(0)
  const [name, setName] = useState('')
  const [nameMissing, setNameMissing] = useState(false)
  const [answers, setAnswers] = useState<(number | null)[]>(
    Array(QUESTION_COUNT).fill(null),
  )
  const join = useMutation(send)
  // The public summary gives the game id for the "See my status" link (loaded once; the link shows when it arrives).
  const loadGame = useCallback(
    (signal: AbortSignal) => getPublicGame(publicCode, signal),
    [publicCode],
  )
  const game = useAsync(loadGame)
  const gameId = game.status === 'success' ? game.data.gameId : null
  const saving = join.status === 'saving'
  // A 400 means the server refused the name (the answers always come from the cards), so show the name step again.
  const refused = join.status === 'error' && statusOf(join.error) === 400

  // Back to the name step when the server refused it, so the form never loops on the last question.
  useEffect(() => {
    if (refused) setStep(0)
  }, [refused])

  function submit(skillAnswers: (number | null)[]) {
    const request: GuestCheckInRequest = {
      displayName: name.trim(),
      answers: toSkillAnswers(skillAnswers),
    }
    void join.run({ publicCode, request })
  }

  function notSure() {
    setAnswers(Array(QUESTION_COUNT).fill(null))
    submit(Array(QUESTION_COUNT).fill(null))
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (saving) return
    if (step === 0) {
      if (!name.trim()) {
        setNameMissing(true)
        return
      }
      setNameMissing(false)
      setStep(1)
    } else if (step < QUESTION_COUNT) {
      setStep(step + 1)
    } else {
      submit(answers)
    }
  }

  function chooseAnswer(value: number) {
    setAnswers(answers.map((a, i) => (i === step - 1 ? value : a)))
  }

  const form = (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      {step === 0 ? (
        <>
          <TextField
            id="join-name"
            label={t.nameLabel}
            name="displayName"
            autoComplete="nickname"
            maxLength={80}
            value={name}
            onChange={(event) => setName(event.target.value)}
            disabled={saving}
            aria-describedby="join-name-help"
          />
          <p id="join-name-help" className="text-sm text-subtle">
            {t.nameHelp}
          </p>
          {(nameMissing || refused) && (
            <Notice role="alert">{t.nameRequired}</Notice>
          )}
        </>
      ) : (
        <QuestionStep
          key={step}
          index={step - 1}
          value={answers[step - 1] ?? null}
          onChange={chooseAnswer}
          disabled={saving}
        />
      )}
      <Button
        type="submit"
        disabled={saving || (step > 0 && answers[step - 1] === null)}
        className="min-h-14 text-[17px]"
      >
        {step === QUESTION_COUNT ? t.submit : t.next}
      </Button>
      {step > 0 && (
        <Button
          variant="secondary"
          disabled={saving}
          onClick={() => setStep(step - 1)}
          className="min-h-14 text-[17px]"
        >
          {t.back}
        </Button>
      )}
      {step === 1 && (
        <Button
          variant="secondary"
          disabled={saving}
          onClick={notSure}
          className="min-h-14 text-[17px]"
        >
          {t.notSure}
        </Button>
      )}
    </form>
  )

  return (
    <AuthPageShell titleId="join-title" title={t.title}>
      {(() => {
        switch (join.status) {
          case 'idle':
          case 'saving':
            return form
          case 'success':
            return (
              <>
                <Notice role="status" strong>
                  {t.pending}
                </Notice>
                <p className="text-base">{t.pendingBody}</p>
                {gameId && (
                  <Link
                    to={`/my/games/${encodeURIComponent(gameId)}?code=${encodeURIComponent(publicCode)}`}
                    className="inline-flex min-h-[52px] items-center justify-center rounded-2xl bg-action px-6 text-[17px] font-extrabold text-white hover:bg-action-dark focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action"
                  >
                    {t.seeStatus}
                  </Link>
                )}
                <BackHomeLink />
              </>
            )
          case 'conflict':
            return (
              <>
                <Notice role="alert">{copy.publicGame.closedNote}</Notice>
                <BackHomeLink />
              </>
            )
          case 'error':
            if (refused) return form
            if (statusOf(join.error) === 404) {
              return (
                <>
                  <Notice role="alert">{copy.publicGame.notFoundTitle}</Notice>
                  <BackHomeLink />
                </>
              )
            }
            return <ErrorBanner onRetry={() => submit(answers)} />
          default: {
            const unreachable: never = join
            return unreachable
          }
        }
      })()}
    </AuthPageShell>
  )
}

function Notice({
  role,
  strong = false,
  children,
}: {
  role: 'alert' | 'status'
  strong?: boolean
  children: ReactNode
}) {
  return (
    <p
      role={role}
      className={
        strong
          ? 'rounded-xl bg-highlight px-3.5 py-3 text-lg font-extrabold'
          : 'rounded-xl bg-error-soft px-3.5 py-3 text-sm font-bold text-error'
      }
    >
      {children}
    </p>
  )
}

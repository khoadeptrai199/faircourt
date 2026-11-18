import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { ApiError } from '../../api/errors'
import { resetPassword } from '../../api/passwordReset'
import AuthPageShell from '../../components/AuthPageShell'
import Button from '../../components/Button'
import ErrorBanner from '../../components/ErrorBanner'
import TextField from '../../components/TextField'
import { copy } from '../../copy/en'
import type { MutationState } from '../../hooks/useMutation'
import { useMutation } from '../../hooks/useMutation'
import { checkNewPassword } from '../../lib/passwordRules'

const t = copy.reset

type Input = { email: string; code: string; password: string }

// What to show for each state of the request. Wrong, expired, used and unknown all look the same (401).
function describe(state: MutationState<unknown>): {
  message: string | null
  unavailable: boolean
  clearPasswords: boolean
} {
  switch (state.status) {
    case 'idle':
    case 'saving':
    case 'success':
      return { message: null, unavailable: false, clearPasswords: false }
    case 'conflict':
      return {
        message: t.invalidCode,
        unavailable: false,
        clearPasswords: true,
      }
    case 'error': {
      const status = state.error instanceof ApiError ? state.error.status : 0
      if (status === 401) {
        return {
          message: t.invalidCode,
          unavailable: false,
          clearPasswords: true,
        }
      }
      // 400 after the form's own checks passed: the server refused the password (a very common one).
      if (status === 400) {
        return {
          message: t.tooCommon,
          unavailable: false,
          clearPasswords: true,
        }
      }
      return { message: null, unavailable: true, clearPasswords: false }
    }
    default: {
      const unreachable: never = state
      return unreachable
    }
  }
}

// /reset-password: step 2, the code from the mail and the new password twice (password reset, design F21).
// No automatic login: after success the person signs in with the new password.
export default function ResetPasswordPage() {
  const location = useLocation()
  const fromForgot = (location.state as { email?: string } | null)?.email ?? ''
  const [email, setEmail] = useState(fromForgot)
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const [problem, setProblem] = useState<string | null>(null)
  const submit = useMutation((i: Input) =>
    resetPassword(i.email, i.code, i.password),
  )
  const shown = describe(submit)

  // A password does not stay in the page after an answer. Kept only when the service was down, so Try again works.
  useEffect(() => {
    if (shown.clearPasswords) {
      setPassword('')
      setRepeat('')
    }
  }, [shown.clearPasswords, submit.status])

  async function attempt() {
    if (!email.trim() || !/^\d{8}$/.test(code.trim())) {
      setProblem(t.invalidCode)
      return
    }
    const rule = checkNewPassword(email, password)
    if (rule === 'tooShort') return setProblem(t.tooShort)
    if (rule === 'tooLong') return setProblem(t.tooLong)
    if (rule === 'sameAsEmail') return setProblem(t.tooCommon)
    if (password !== repeat) return setProblem(t.mismatch)
    setProblem(null)
    await submit.run({ email: email.trim(), code: code.trim(), password })
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    void attempt()
  }

  const saving = submit.status === 'saving'

  if (submit.status === 'success') {
    return (
      <AuthPageShell titleId="reset-title" title={t.title}>
        <p
          role="status"
          className="rounded-xl bg-highlight px-3.5 py-3 text-base font-bold"
        >
          {t.success}
        </p>
        <Link
          to="/login"
          className="inline-flex min-h-[52px] items-center justify-center rounded-2xl bg-action px-6 text-[17px] font-extrabold text-white hover:bg-action-dark focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action"
        >
          {t.toLogin}
        </Link>
      </AuthPageShell>
    )
  }

  const message = problem ?? shown.message

  return (
    <AuthPageShell titleId="reset-title" title={t.title}>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <TextField
          id="reset-email"
          label={copy.login.email}
          type="email"
          name="email"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          inputMode="email"
          maxLength={254}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          disabled={saving}
        />
        <TextField
          id="reset-code"
          label={t.code}
          type="text"
          name="code"
          autoComplete="one-time-code"
          inputMode="numeric"
          pattern="[0-9]*"
          maxLength={8}
          value={code}
          onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
          disabled={saving}
        />
        <TextField
          id="reset-password"
          label={t.newPassword}
          type="password"
          name="new-password"
          autoComplete="new-password"
          maxLength={128}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          disabled={saving}
        />
        <TextField
          id="reset-repeat"
          label={t.repeat}
          type="password"
          name="repeat-password"
          autoComplete="new-password"
          maxLength={128}
          value={repeat}
          onChange={(event) => setRepeat(event.target.value)}
          disabled={saving}
        />

        {message && (
          <p
            role="alert"
            className="rounded-xl bg-error-soft px-3.5 py-3 text-sm font-bold text-error"
          >
            {message}
          </p>
        )}
        {shown.unavailable && <ErrorBanner onRetry={() => void attempt()} />}

        <Button
          type="submit"
          disabled={saving}
          className="min-h-[52px] text-[17px]"
        >
          {t.submit}
        </Button>
      </form>
    </AuthPageShell>
  )
}

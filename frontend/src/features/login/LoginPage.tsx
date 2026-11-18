import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { login } from '../../api/account'
import { ApiError } from '../../api/errors'
import AuthPageShell from '../../components/AuthPageShell'
import Button from '../../components/Button'
import ErrorBanner from '../../components/ErrorBanner'
import TextField from '../../components/TextField'
import { copy } from '../../copy/en'
import { useAccount } from '../../hooks/useAccount'
import type { MutationState } from '../../hooks/useMutation'
import { useMutation } from '../../hooks/useMutation'
import { safeNextPath } from '../../lib/redirect'

const t = copy.login

type Credentials = { email: string; password: string }

// What to show under the form for each state of the login request. Wrong email and wrong password look the same on
// purpose (no account enumeration). `unavailable` = the service did not answer: show the shared banner with Try again.
function describe(state: MutationState<unknown>): {
  message: string | null
  unavailable: boolean
  clearPassword: boolean
} {
  switch (state.status) {
    case 'idle':
    case 'saving':
    case 'success':
      return { message: null, unavailable: false, clearPassword: false }
    case 'conflict':
      return { message: t.invalid, unavailable: false, clearPassword: true }
    case 'error': {
      const status = state.error instanceof ApiError ? state.error.status : 0
      if (status === 429) {
        return {
          message: t.tooManyAttempts,
          unavailable: false,
          clearPassword: true,
        }
      }
      if (status === 400 || status === 401) {
        return { message: t.invalid, unavailable: false, clearPassword: true }
      }
      return { message: null, unavailable: true, clearPassword: false }
    }
    default: {
      const unreachable: never = state
      return unreachable
    }
  }
}

// /login?next=<path>: host login (owner decision 2026-11-18, design D-1). After a successful login goes to `next`
// (same-site paths only) or home.
export default function LoginPage() {
  const [params] = useSearchParams()
  const next = safeNextPath(params.get('next'))
  const navigate = useNavigate()
  const account = useAccount()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [missing, setMissing] = useState(false)
  const submit = useMutation((c: Credentials) => login(c.email, c.password))
  const shown = describe(submit)

  // A wrong password does not stay in the page. It is kept only when the service was down, so Try again works.
  useEffect(() => {
    if (shown.clearPassword) setPassword('')
  }, [shown.clearPassword, submit.status])

  // Already signed in: nothing to do here.
  if (account.status === 'signedIn') {
    return <Navigate to={next} replace />
  }

  async function attempt() {
    if (!email.trim() || !password) {
      setMissing(true)
      return
    }
    setMissing(false)
    const result = await submit.run({ email: email.trim(), password })
    if (result) {
      navigate(next, { replace: true })
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    void attempt()
  }

  const saving = submit.status === 'saving'
  const message = missing ? t.required : shown.message

  return (
    <AuthPageShell titleId="login-title" title={t.title}>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <TextField
          id="login-email"
          label={t.email}
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
          id="login-password"
          label={t.password}
          type="password"
          name="password"
          autoComplete="current-password"
          maxLength={128}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
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
          {saving ? t.saving : t.submit}
        </Button>
        <Link
          to="/forgot-password"
          className="self-start text-sm font-bold text-action underline-offset-2 hover:underline focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action"
        >
          {copy.forgot.link}
        </Link>
      </form>
    </AuthPageShell>
  )
}

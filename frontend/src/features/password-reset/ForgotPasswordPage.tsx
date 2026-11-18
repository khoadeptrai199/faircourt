import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { requestCode } from '../../api/passwordReset'
import AuthPageShell from '../../components/AuthPageShell'
import Button from '../../components/Button'
import ErrorBanner from '../../components/ErrorBanner'
import TextField from '../../components/TextField'
import { copy } from '../../copy/en'
import { useMutation } from '../../hooks/useMutation'

// /forgot-password: step 1, ask for a code (password reset, design F21). The answer is always the same, so the page
// never says whether the email has an account.
export default function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [missing, setMissing] = useState(false)
  const submit = useMutation((e: string) => requestCode(e))

  async function attempt() {
    if (!email.trim()) {
      setMissing(true)
      return
    }
    setMissing(false)
    await submit.run(email.trim())
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    void attempt()
  }

  const saving = submit.status === 'saving'

  return (
    <AuthPageShell titleId="forgot-title" title={copy.forgot.title}>
      {submit.status === 'success' ? (
        <>
          <p
            role="status"
            className="rounded-xl bg-highlight px-3.5 py-3 text-base font-bold"
          >
            {copy.forgot.sent}
          </p>
          <Button
            onClick={() =>
              navigate('/reset-password', { state: { email: email.trim() } })
            }
            className="min-h-[52px] text-[17px]"
          >
            {copy.forgot.haveCode}
          </Button>
        </>
      ) : (
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <TextField
            id="forgot-email"
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
          {missing && (
            <p
              role="alert"
              className="rounded-xl bg-error-soft px-3.5 py-3 text-sm font-bold text-error"
            >
              {copy.login.required}
            </p>
          )}
          {(submit.status === 'error' || submit.status === 'conflict') && (
            <ErrorBanner onRetry={() => void attempt()} />
          )}
          <Button
            type="submit"
            disabled={saving}
            className="min-h-[52px] text-[17px]"
          >
            {copy.forgot.submit}
          </Button>
          <Link
            to="/reset-password"
            className="self-start text-sm font-bold text-action underline-offset-2 hover:underline focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action"
          >
            {copy.forgot.haveCode}
          </Link>
        </form>
      )}
    </AuthPageShell>
  )
}

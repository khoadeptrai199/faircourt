// New-password rules checked in the form (design D-4). The server checks the same rules again and is the only one
// that knows the list of very common passwords: if it still answers 400 after these checks pass, the form says
// "choose a less common password".
export const MIN_PASSWORD_LENGTH = 12
export const MAX_PASSWORD_BYTES = 72

export type PasswordProblem = 'tooShort' | 'tooLong' | 'sameAsEmail' | null

export function checkNewPassword(
  email: string,
  password: string,
): PasswordProblem {
  if (password.length < MIN_PASSWORD_LENGTH) return 'tooShort'
  // BCrypt reads at most 72 bytes; the server refuses longer passwords instead of cutting them.
  if (new TextEncoder().encode(password).length > MAX_PASSWORD_BYTES) {
    return 'tooLong'
  }
  if (password.trim().toLowerCase() === email.trim().toLowerCase()) {
    return 'sameAsEmail'
  }
  return null
}

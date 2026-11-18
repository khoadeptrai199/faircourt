import { apiPost } from './client'

// Step 1: ask for a code. The answer is always the same (202), whether or not the email has an account.
export async function requestCode(email: string): Promise<void> {
  await apiPost('/auth/password/forgot', { email })
}

// Step 2: code + new password. Wrong, expired, used and unknown all look the same (401); 400 = the password
// or the code format is not acceptable. The password is sent once and not kept.
export async function resetPassword(
  email: string,
  code: string,
  newPassword: string,
): Promise<void> {
  await apiPost('/auth/password/reset', { email, code, newPassword })
}

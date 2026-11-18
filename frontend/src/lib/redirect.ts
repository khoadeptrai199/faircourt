// Where to go after login. The `next` value comes from the address bar, so it is untrusted: only a path on this
// same site is allowed (no other site, no "//host", no backslashes, no control characters); anything else goes home.
// Prevents an open redirect (OWASP Unvalidated Redirects). The result is checked after the browser has normalised
// it (a value like "/.//evil.com" becomes "//evil.com" when parsed), so the final path must start with a single "/".
export function safeNextPath(next: string | null): string {
  if (!next || !next.startsWith('/') || next.startsWith('//')) return '/'
  if (next.includes('\\') || /[\u0000-\u001f\u007f]/.test(next)) return '/'
  try {
    const url = new URL(next, window.location.origin)
    if (url.origin !== window.location.origin) return '/'
    const path = `${url.pathname}${url.search}${url.hash}`
    return /^\/(?![/\\])/.test(path) ? path : '/'
  } catch {
    return '/'
  }
}

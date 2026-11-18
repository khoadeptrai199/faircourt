// Every user-facing string of the new pages (conventions). Approved by the owner 2026-11-18 ("duyệt hết"),
// with "session" replaced by "game" (OD-22); common.pageNotFoundTitle approved after implementation.
// 2026-11-18 (Look and feel, owner approved the mockup): the facts are shown as tiles, so "2 courts" is a number plus
// the word, "At least 6 sets per player" is "6+" plus "sets per player", and "Men: 75.000 ₫–85.000 ₫" is a label
// and a value. Same words, split for layout.
// Design: public-game-page/design.md section 7 and look-and-feel/design.md.
export const copy = {
  publicGame: {
    join: 'Join game',
    courtWord: (n: number) => (n === 1 ? 'court' : 'courts'),
    formatSingles: 'Singles',
    formatDoubles: 'Doubles',
    minSetsWord: 'sets per player',
    priceTitle: 'Price per person',
    priceMale: 'Men',
    priceFemale: 'Women',
    priceNote: 'Pay the host directly.',
    statusOpen: 'Open',
    statusClosed: 'Closed',
    closedNote: 'This game is closed.',
    timeZoneNote: 'Vietnam time',
    notFoundTitle: 'Game not found',
    notFoundBody: 'Check the link from your host.',
  },
  login: {
    title: 'Host login',
    email: 'Email',
    password: 'Password',
    submit: 'Sign in',
    saving: 'Signing in…',
    invalid: 'Email or password is incorrect.',
    required: 'Enter your email and password.',
    // Added with the login throttle (OWASP, owner asked for secure login 2026-11-18); wording awaits owner approval.
    tooManyAttempts: 'Too many attempts. Try again later.',
  },
  account: {
    signOut: 'Sign out',
    signedInAs: (email: string) => `Signed in as ${email}`,
  },
  common: {
    backHome: 'Back to home',
    loading: 'Loading…',
    errorTitle: 'Something went wrong.',
    retry: 'Try again',
    comingSoon: 'Coming soon',
    pageNotFoundTitle: 'Page not found',
  },
} as const

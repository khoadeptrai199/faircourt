// Every user-facing string of the new pages (conventions). Approved by the owner 2026-11-18 ("duyệt hết"),
// with "session" replaced by "game" (OD-22); common.pageNotFoundTitle approved after implementation.
// Design: public-game-page/design.md section 7.
export const copy = {
  publicGame: {
    join: 'Join game',
    courts: (n: number) => (n === 1 ? '1 court' : `${n} courts`),
    formatSingles: 'Singles',
    formatDoubles: 'Doubles',
    minSets: (n: number) => `At least ${n} sets per player`,
    priceTitle: 'Price per person',
    priceMale: (range: string) => `Men: ${range}`,
    priceFemale: (range: string) => `Women: ${range}`,
    priceNote: 'Pay the host directly.',
    statusOpen: 'Open',
    statusClosed: 'Closed',
    closedNote: 'This game is closed.',
    timeZoneNote: 'Vietnam time',
    notFoundTitle: 'Game not found',
    notFoundBody: 'Check the link from your host.',
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

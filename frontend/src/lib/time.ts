// Times are stored in UTC and shown in Vietnam time (blueprint section 12).
const ZONE = 'Asia/Ho_Chi_Minh'

const dayFormat = new Intl.DateTimeFormat('en-GB', {
  timeZone: ZONE,
  weekday: 'short',
  day: 'numeric',
  month: 'short',
})

const clockFormat = new Intl.DateTimeFormat('en-GB', {
  timeZone: ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

// e.g. "Tue 17 Nov · 18:00–21:00"; the end date is added when the game ends on another day.
export function formatGameWindow(startsAt: string, endsAt: string): string {
  const start = new Date(startsAt)
  const end = new Date(endsAt)
  const startDay = dayFormat.format(start).replace(',', '')
  const endDay = dayFormat.format(end).replace(',', '')
  const endText =
    endDay === startDay
      ? clockFormat.format(end)
      : `${endDay} ${clockFormat.format(end)}`
  return `${startDay} · ${clockFormat.format(start)}–${endText}`
}

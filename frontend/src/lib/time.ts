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

// e.g. { day: "Wed 18 Nov", start: "18:00", end: "21:00" }; the end gets its day when the game ends on another day.
export function formatGameTime(
  startsAt: string,
  endsAt: string,
): { day: string; start: string; end: string } {
  const startDate = new Date(startsAt)
  const endDate = new Date(endsAt)
  const startDay = dayFormat.format(startDate).replace(',', '')
  const endDay = dayFormat.format(endDate).replace(',', '')
  const endClock = clockFormat.format(endDate)
  return {
    day: startDay,
    start: clockFormat.format(startDate),
    end: endDay === startDay ? endClock : `${endDay} ${endClock}`,
  }
}

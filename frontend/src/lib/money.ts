import type { PriceRange } from '../api/types'

const amount = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 })

// e.g. "75.000–85.000 ₫"; one number when min = max (OD-18).
export function formatVndRange(range: PriceRange): string {
  const numbers =
    range.minVnd === range.maxVnd
      ? amount.format(range.minVnd)
      : `${amount.format(range.minVnd)}–${amount.format(range.maxVnd)}`
  return `${numbers} ₫`
}

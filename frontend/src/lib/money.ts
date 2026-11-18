import type { PriceRange } from '../api/types'

const vnd = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
})

export function formatVnd(amount: number): string {
  return vnd.format(amount)
}

// One number when min = max (OD-18).
export function formatVndRange(range: PriceRange): string {
  return range.minVnd === range.maxVnd
    ? formatVnd(range.minVnd)
    : `${formatVnd(range.minVnd)}–${formatVnd(range.maxVnd)}`
}

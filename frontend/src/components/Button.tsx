import type { ButtonHTMLAttributes, ReactNode } from 'react'

type Variant = 'primary' | 'secondary'

const variants: Record<Variant, string> = {
  primary:
    'bg-action text-white hover:bg-action-dark disabled:bg-disabled disabled:text-muted',
  secondary:
    'border-2 border-ink bg-surface text-ink hover:bg-highlight disabled:border-ink/20 disabled:text-muted',
}

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant
  children: ReactNode
}

// Real <button>, at least 44px high, visible focus (blueprint section 16).
export default function Button({
  variant = 'primary',
  type = 'button',
  className = '',
  children,
  ...rest
}: Props) {
  return (
    <button
      type={type}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-2xl px-4 text-base font-extrabold focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action disabled:cursor-not-allowed ${variants[variant]} ${className}`}
      {...rest}
    >
      {children}
    </button>
  )
}

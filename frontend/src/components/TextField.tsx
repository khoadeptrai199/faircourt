import type { InputHTMLAttributes } from 'react'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> & {
  id: string
  label: string
}

// Real <label> + <input>, at least 44px high, visible focus (blueprint section 16).
export default function TextField({
  id,
  label,
  className = '',
  ...rest
}: Props) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-bold">
        {label}
      </label>
      <input
        id={id}
        className={`min-h-12 rounded-xl border-2 border-ink/25 bg-surface px-3.5 text-base text-ink focus-visible:border-action focus-visible:outline-3 focus-visible:outline-offset-1 focus-visible:outline-action/40 ${className}`}
        {...rest}
      />
    </div>
  )
}

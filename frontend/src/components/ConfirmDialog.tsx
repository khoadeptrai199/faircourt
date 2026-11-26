import { useEffect, useId, useRef } from 'react'
import Button from './Button'

// Modal confirmation (blueprint section 16). Native <dialog> + showModal(): focus moves into the dialog, Esc
// cancels, and the browser returns focus to the button that opened it.
export default function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  body: string
  confirmLabel: string
  cancelLabel: string
  onConfirm: () => void
  onCancel: () => void
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClose={onCancel}
      className="m-auto w-[min(92vw,420px)] rounded-[20px] border-2 border-ink bg-surface p-5 text-ink backdrop:bg-ink/40"
    >
      <div className="flex flex-col gap-4">
        <h2 id={titleId} className="text-xl font-extrabold">
          {title}
        </h2>
        <p className="text-base">{body}</p>
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={onCancel}>
            {cancelLabel}
          </Button>
          <Button onClick={onConfirm}>{confirmLabel}</Button>
        </div>
      </div>
    </dialog>
  )
}

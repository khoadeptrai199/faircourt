import { useState } from 'react'
import type { CourtMatchState } from '../../api/types'
import Button from '../../components/Button'
import ConfirmDialog from '../../components/ConfirmDialog'
import { copy } from '../../copy/en'

const t = copy.hostBoard

export type MatchAction = 'start' | 'finish' | 'cancel'

// Buttons under one court's match. Start acts at once; Finish and Cancel match ask first (design section 7).
// The page hides this for a closed game.
export default function CourtActions({
  matchNo,
  state,
  saving,
  savingAction,
  onAction,
}: {
  matchNo: number
  state: CourtMatchState
  saving: boolean
  // The action being saved for this court's match, or null when none is.
  savingAction: MatchAction | null
  onAction: (action: MatchAction) => void
}) {
  const [asking, setAsking] = useState<'finish' | 'cancel' | null>(null)
  const primary: MatchAction = state === 'PLANNED' ? 'start' : 'finish'
  const primaryLabel = state === 'PLANNED' ? t.startMatch : t.finish
  const texts =
    asking === 'finish'
      ? { title: t.finishTitle(matchNo), body: t.finishBody }
      : { title: t.cancelTitle(matchNo), body: t.cancelBody }

  return (
    <div className="mt-auto flex flex-col gap-2 md:flex-row">
      <Button
        variant="primary"
        className="min-h-11 w-full md:flex-1"
        disabled={saving}
        onClick={() =>
          primary === 'start' ? onAction('start') : setAsking('finish')
        }
      >
        {savingAction === primary ? t.saving : primaryLabel}
      </Button>
      <Button
        variant="secondary"
        className="min-h-11 w-full md:flex-1"
        disabled={saving}
        onClick={() => setAsking('cancel')}
      >
        {savingAction === 'cancel' ? t.saving : t.cancelMatch}
      </Button>
      <ConfirmDialog
        open={asking !== null}
        title={texts.title}
        body={texts.body}
        confirmLabel={asking === 'finish' ? t.finish : t.cancelMatch}
        cancelLabel={t.cancelAction}
        onConfirm={() => {
          const action = asking
          setAsking(null)
          if (action) onAction(action)
        }}
        onCancel={() => setAsking(null)}
      />
    </div>
  )
}

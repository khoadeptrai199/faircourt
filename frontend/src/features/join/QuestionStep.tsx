import { copy } from '../../copy/en'

const t = copy.join

// One skill question with four large answer cards (radio group: arrow keys work, no drag), blueprint section 16.
export default function QuestionStep({
  index,
  value,
  onChange,
  disabled,
}: {
  index: number
  value: number | null
  onChange: (answer: number) => void
  disabled: boolean
}) {
  return (
    <fieldset className="flex min-w-0 flex-col gap-3" disabled={disabled}>
      <legend className="text-xl leading-snug font-extrabold">
        {t.questions[index]}
      </legend>
      <p className="text-sm font-bold text-subtle">{t.progress(index + 1)}</p>
      <div className="flex flex-col gap-2.5">
        {t.answers.map((label, answer) => (
          <label
            key={label}
            className="flex min-h-14 cursor-pointer items-center rounded-2xl border-2 border-ink/25 bg-surface px-4 text-base font-bold has-checked:border-action has-checked:bg-highlight has-focus-visible:outline-3 has-focus-visible:outline-offset-2 has-focus-visible:outline-action has-disabled:cursor-not-allowed has-disabled:text-muted"
          >
            <input
              type="radio"
              name={`question-${index}`}
              value={answer}
              checked={value === answer}
              onChange={() => onChange(answer)}
              className="sr-only"
            />
            {label}
          </label>
        ))}
      </div>
    </fieldset>
  )
}

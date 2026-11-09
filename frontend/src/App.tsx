// Slice 1 placeholder shell. Real routes (/s/:publicCode, /my/..., /host/...) arrive in later slices (blueprint section 16).
export default function App() {
  return (
    <main className="min-h-dvh bg-background text-ink">
      <div className="mx-auto max-w-[720px] px-4 py-8 md:px-6">
        <div className="rounded-2xl bg-surface p-4 shadow-sm md:p-6">
          <h1 className="text-[28px] font-bold leading-tight">FairCourt</h1>
          <p className="mt-2 text-base">
            Join a session. Find a fair game. Keep playing.
          </p>
        </div>
      </div>
    </main>
  )
}

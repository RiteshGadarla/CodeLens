export function Logo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden>
      <rect width="32" height="32" rx="8" className="fill-indigo-600" />
      <circle cx="14" cy="14" r="6.5" fill="none" stroke="white" strokeWidth="2.5" />
      <path d="M19 19l6 6" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
      <circle cx="14" cy="14" r="2" className="fill-indigo-200" />
    </svg>
  )
}

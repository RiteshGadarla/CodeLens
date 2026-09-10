import avatar from '@/assets/brand/lyra.webp'
import { cn } from '@/lib/cn'

export const LYRA = 'Lyra'

// avatar for the assistant; inline size so flex rows can't stretch it
export function LyraMark({ size = 28, className }: { size?: number; className?: string }) {
  return (
    <img
      src={avatar}
      alt=""
      aria-hidden
      draggable={false}
      style={{ width: size, height: size }}
      className={cn('shrink-0 rounded-full bg-violet-50 ring-1 ring-violet-200 select-none', className)}
    />
  )
}

// drop-in for icon slots; a face needs a little more room than a glyph
export const LyraIcon = ({ className }: { className?: string }) => <LyraMark size={20} className={className} />

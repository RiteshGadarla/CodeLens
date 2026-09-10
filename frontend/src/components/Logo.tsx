import logo from '@/assets/brand/codelens.webp'

export function Logo({ size = 28 }: { size?: number }) {
  return <img src={logo} alt="" aria-hidden draggable={false} style={{ width: size, height: size }} className="shrink-0 select-none" />
}

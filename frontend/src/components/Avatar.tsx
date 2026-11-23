// Ангиллын өнгөтэй ижил өнгөний сан; газрын зургийн тэмдэгттэй зохицно
const COLORS = ['#e8590c', '#1c7ed6', '#2f9e44', '#ae3ec9', '#f08c00', '#0c8599', '#d6336c', '#5f3dc4', '#74b816', '#c2255c']

/** Хүн бүрийн тогтмол өнгө: ижил нэр үргэлж ижил өнгөтэй. */
export function avatarColor(name: string) {
  let h = 0
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return COLORS[h % COLORS.length]
}

/** Хүний нэрийн эхний үсгийг дугуй дотор харуулна. */
export default function Avatar({ name, size = 40, ring = false }: { name: string; size?: number; ring?: boolean }) {
  const initial = name.trim().charAt(0).toUpperCase() || '?'
  return (
    <span className={`av ${ring ? 'ring' : ''}`} aria-hidden
          style={{ width: size, height: size, fontSize: Math.round(size * 0.42), background: avatarColor(name) }}>
      {initial}
    </span>
  )
}

/** Бүлгийн давхцсан дүрсүүд, олон бол "+N". */
export function AvatarStack({ names, max = 4, size = 26 }: { names: string[]; max?: number; size?: number }) {
  const shown = names.slice(0, max)
  return (
    <span className="av-stack">
      {shown.map((n, i) => <Avatar key={i} name={n} size={size} ring />)}
      {names.length > max && (
        <span className="av ring more" style={{ width: size, height: size, fontSize: Math.round(size * 0.38) }}>
          +{names.length - max}
        </span>
      )}
    </span>
  )
}

// Same palette as the category colours, so avatars sit well next to the map markers
const COLORS = ['#e8590c', '#1c7ed6', '#2f9e44', '#ae3ec9', '#f08c00', '#0c8599', '#d6336c', '#5f3dc4', '#74b816', '#c2255c']

/** Stable colour for a person: the same name always gets the same colour. */
export function avatarColor(name: string) {
  let h = 0
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return COLORS[h % COLORS.length]
}

/** Round initial of a person's name. */
export default function Avatar({ name, size = 40, ring = false }: { name: string; size?: number; ring?: boolean }) {
  const initial = name.trim().charAt(0).toUpperCase() || '?'
  return (
    <span className={`av ${ring ? 'ring' : ''}`} aria-hidden
          style={{ width: size, height: size, fontSize: Math.round(size * 0.42), background: avatarColor(name) }}>
      {initial}
    </span>
  )
}

/** Overlapping avatars of a group, with "+N" when there are many. */
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

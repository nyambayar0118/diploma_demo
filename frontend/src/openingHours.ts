// OSM opening_hours strings ("Mo-Fr 09:00-18:00; Sa 10:00-16:00") -> open now, next change, readable week.
// The parser is large, so it is loaded only when a place sheet is opened.

export type OpeningInfo = {
  openNow: boolean | null
  /** "18:00 цагт хаагдана", "Маргааш 09:00 цагт нээгдэнэ" */
  next: string | null
  /** One line per rule, in Mongolian ("Да–Ба 09:00–18:00") */
  lines: string[]
  /** Kept for callers that only want plain text */
  text: string
}

const DAYS: Record<string, string> = { Mo: 'Да', Tu: 'Мя', We: 'Лх', Th: 'Пү', Fr: 'Ба', Sa: 'Бя', Su: 'Ня' }
const WEEKDAY = ['Ням', 'Даваа', 'Мягмар', 'Лхагва', 'Пүрэв', 'Баасан', 'Бямба']

/** Mongolian day names and dashes for one prettified rule. */
function localise(rule: string) {
  return rule
    .replace(/\b(Mo|Tu|We|Th|Fr|Sa|Su)\b/g, (d) => DAYS[d])
    .replace(/\bPH\b/g, 'Баяр')
    .replace(/\boff\b|\bclosed\b/g, 'амарна')
    .replace(/(\d)-(\d)/g, '$1–$2')
    .replace(/(Да|Мя|Лх|Пү|Ба|Бя|Ня)-(Да|Мя|Лх|Пү|Ба|Бя|Ня)/g, '$1–$2')
    .replace(/^24\/7$/, 'Өдөр бүр 24 цаг')
}

function hhmm(d: Date) {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

export async function describeOpeningHours(raw: string): Promise<OpeningInfo> {
  try {
    const { default: OpeningHours } = await import('opening_hours')
    // No country data: public-holiday rules (PH) are ignored rather than failing
    const oh = new OpeningHours(raw, null, { mode: 0, warnings_severity: 0 } as never)
    const pretty = oh.prettifyValue()
    const lines = pretty.split(/;\s*/).filter(Boolean).map(localise)
    const unknown = oh.getUnknown()
    let next: string | null = null
    if (!unknown) {
      const change = oh.getNextChange()
      if (change) {
        const now = new Date()
        const days = Math.round((new Date(change).setHours(0, 0, 0, 0) - new Date(now).setHours(0, 0, 0, 0)) / 86400000)
        const when = days === 0 ? '' : days === 1 ? 'Маргааш ' : `${WEEKDAY[change.getDay()]} `
        next = `${when}${hhmm(change)} цагт ${oh.getState() ? 'хаагдана' : 'нээгдэнэ'}`
      }
    }
    return { openNow: unknown ? null : oh.getState(), next, lines, text: pretty }
  } catch {
    return { openNow: null, next: null, lines: [raw], text: raw }
  }
}

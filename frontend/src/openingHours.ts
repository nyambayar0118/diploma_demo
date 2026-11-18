// OSM opening_hours strings ("Mo-Fr 09:00-18:00; Sa 10:00-16:00") -> open now + readable text.
// The parser is large, so it is loaded only when a place sheet is opened.

export type OpeningInfo = { openNow: boolean | null; text: string }

export async function describeOpeningHours(raw: string): Promise<OpeningInfo> {
  try {
    const { default: OpeningHours } = await import('opening_hours')
    // No country data: public-holiday rules (PH) are ignored rather than failing
    const oh = new OpeningHours(raw, null, { mode: 0, warnings_severity: 0 } as never)
    const unknown = oh.getUnknown()
    return { openNow: unknown ? null : oh.getState(), text: oh.prettifyValue() }
  } catch {
    return { openNow: null, text: raw }
  }
}

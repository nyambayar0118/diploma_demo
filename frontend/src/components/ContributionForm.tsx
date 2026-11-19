import { useEffect, useState } from 'react'
import { api, ApiError, LINK_TYPE_LABELS, type Category, type LinkType, type PlaceDetails, type PriceLevel,
  type ProposedData, type RequestType } from '../api'
import { BUDGET_LABELS } from '../categoryLabels'

type Props = {
  /** Edit / link a place, or create a new one at {lat, lng}. */
  place?: PlaceDetails
  position?: { lat: number; lng: number }
  onDone: (message: string) => void
  onCancel: () => void
}

/**
 * Contribution forms (АШ03 new place, АШ04 corrections, АШ18 links). Only changed fields are sent;
 * a moderator reviews the request before anything changes on the map.
 */
export default function ContributionForm({ place, position, onDone, onCancel }: Props) {
  const creating = !place
  const [type, setType] = useState<RequestType>(creating ? 'CREATE_PLACE' : 'UPDATE_PLACE')
  const [categories, setCategories] = useState<Category[]>([])
  const [form, setForm] = useState<ProposedData>(() => creating ? { lat: position?.lat, lng: position?.lng } : {
    nameCyrillic: place.name, nameLatin: place.nameLatin ?? '', categoryCode: place.category, phone: place.phone ?? '',
    openingHours: place.openingHours ?? '', priceLevel: place.priceLevel ?? undefined,
  })
  const [link, setLink] = useState<{ url: string; linkType: LinkType; linkTitle: string }>({ url: '', linkType: 'WEBSITE', linkTitle: '' })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<Category[]>('/api/categories').then(setCategories).catch(() => undefined)
  }, [])

  const set = (key: keyof ProposedData) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm({ ...form, [key]: e.target.value })

  /** For an edit, only the fields that differ from the current values. */
  const changes = (): ProposedData => {
    if (creating || !place) return form
    const d: ProposedData = {}
    if (form.nameCyrillic && form.nameCyrillic !== place.name) d.nameCyrillic = form.nameCyrillic
    if ((form.nameLatin ?? '') !== (place.nameLatin ?? '') && form.nameLatin) d.nameLatin = form.nameLatin
    if (form.categoryCode && form.categoryCode !== place.category) d.categoryCode = form.categoryCode
    if (form.phone && form.phone !== (place.phone ?? '')) d.phone = form.phone
    if (form.openingHours && form.openingHours !== (place.openingHours ?? '')) d.openingHours = form.openingHours
    if (form.street) d.street = form.street
    if (form.priceLevel && form.priceLevel !== place.priceLevel) d.priceLevel = form.priceLevel
    if (form.permanentlyClosed) d.permanentlyClosed = true
    return d
  }

  const submit = async () => {
    setBusy(true)
    setError(null)
    try {
      const data = type === 'ADD_LINK' ? { url: link.url.trim(), linkType: link.linkType, linkTitle: link.linkTitle || undefined } : changes()
      if (type === 'UPDATE_PLACE' && Object.keys(data).length === 0) throw new ApiError(0, 'NO_CHANGES', 'Өөрчлөлт алга')
      await api('/api/change-requests', { method: 'POST', body: { type, targetPlaceId: place?.id, data } })
      onDone('Баярлалаа! Модератор хянаж баталсны дараа газрын зураг дээр харагдана, та урамшууллын оноо авна.')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="contribution">
      <h3>{creating ? 'Шинэ газар нэмэх' : 'Мэдээлэл сайжруулах'}</h3>
      {!creating && (
        <div className="chips" style={{ marginBottom: 12 }}>
          <button type="button" className={`chip ${type === 'UPDATE_PLACE' ? 'on' : ''}`} onClick={() => setType('UPDATE_PLACE')}>Засах</button>
          <button type="button" className={`chip ${type === 'ADD_LINK' ? 'on' : ''}`} onClick={() => setType('ADD_LINK')}>Холбоос нэмэх</button>
        </div>
      )}
      {error && <div className="error">{error}</div>}

      {type === 'ADD_LINK' ? (
        <>
          <div className="field">
            <label>Холбоосын төрөл</label>
            <select value={link.linkType} onChange={(e) => setLink({ ...link, linkType: e.target.value as LinkType })}>
              {Object.entries(LINK_TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </div>
          <div className="field">
            <label>Хаяг (https://…)</label>
            <input type="url" value={link.url} onChange={(e) => setLink({ ...link, url: e.target.value })} />
          </div>
          {(link.linkType === 'REVIEW_ARTICLE' || link.linkType === 'OTHER') && (
            <div className="field">
              <label>Гарчиг</label>
              <input maxLength={200} value={link.linkTitle} onChange={(e) => setLink({ ...link, linkTitle: e.target.value })} />
            </div>
          )}
        </>
      ) : (
        <>
          <div className="field">
            <label>Нэр (кирилл)</label>
            <input maxLength={255} value={form.nameCyrillic ?? ''} onChange={set('nameCyrillic')} />
          </div>
          <div className="field">
            <label>Нэр (латин)</label>
            <input maxLength={255} value={form.nameLatin ?? ''} onChange={set('nameLatin')} />
          </div>
          <div className="field">
            <label>Ангилал</label>
            <select value={form.categoryCode ?? ''} onChange={set('categoryCode')}>
              <option value="">— сонгох —</option>
              {categories.map((main) => (
                <optgroup key={main.code} label={main.nameMn}>
                  {main.children?.map((c) => <option key={c.code} value={c.code}>{c.nameMn}</option>)}
                </optgroup>
              ))}
            </select>
          </div>
          <div className="field">
            <label>Утас</label>
            <input maxLength={100} value={form.phone ?? ''} onChange={set('phone')} />
          </div>
          <div className="field">
            <label>Ажиллах цаг</label>
            <input maxLength={500} placeholder="Mo-Fr 09:00-18:00; Sa 10:00-16:00" value={form.openingHours ?? ''}
                   onChange={set('openingHours')} />
            <span className="hint">OpenStreetMap-ийн бичлэг: Mo Tu We Th Fr Sa Su, “24/7”</span>
          </div>
          <div className="field">
            <label>Хаяг (гудамж, байр)</label>
            <input maxLength={255} value={form.street ?? ''} onChange={set('street')} />
          </div>
          <div className="field">
            <label>Үнийн түвшин</label>
            <select value={form.priceLevel ?? ''} onChange={(e) => setForm({ ...form, priceLevel: (e.target.value || undefined) as PriceLevel })}>
              <option value="">— мэдэхгүй —</option>
              {Object.entries(BUDGET_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </div>
          {!creating && (
            <label className="toggle">
              <input type="checkbox" checked={!!form.permanentlyClosed}
                     onChange={(e) => setForm({ ...form, permanentlyClosed: e.target.checked })} />
              <span><div className="t-title">Энэ газар хаагдсан / байхгүй болсон</div></span>
            </label>
          )}
          {creating && position && (
            <p className="hint" style={{ fontSize: 12, color: 'var(--c-muted)' }}>
              Байршил: {position.lat.toFixed(5)}, {position.lng.toFixed(5)}
            </p>
          )}
        </>
      )}
      <div className="row" style={{ justifyContent: 'flex-start' }}>
        <button className="btn" disabled={busy} onClick={submit}>{busy ? 'Илгээж байна…' : 'Илгээх'}</button>
        <button className="btn secondary" onClick={onCancel}>Болих</button>
      </div>
    </div>
  )
}

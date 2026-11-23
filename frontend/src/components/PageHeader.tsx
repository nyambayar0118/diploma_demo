import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'

/** Газрын зургаас бусад хуудасны наалттай толгой: буцах сум, гарчиг, нэмэлт үйлдэл. */
export default function PageHeader({ title, back = '/', children }: { title: string; back?: string; children?: ReactNode }) {
  const navigate = useNavigate()
  return (
    <header className="page-header">
      <button className="page-back" onClick={() => (window.history.length > 1 ? navigate(-1) : navigate(back))}
              aria-label="Буцах">←</button>
      <h1>{title}</h1>
      <span className="page-header-actions">{children}</span>
    </header>
  )
}

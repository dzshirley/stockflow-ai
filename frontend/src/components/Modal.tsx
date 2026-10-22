import { useEffect, type ReactNode } from 'react'

export function Modal({ title, children, onClose, wide = false }: { title: string; children: ReactNode; onClose: () => void; wide?: boolean }) {
  useEffect(() => {
    const close = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [onClose])
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <section className={`modal ${wide ? 'modal--wide' : ''}`} role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <header className="modal__header"><h2 id="modal-title">{title}</h2><button className="icon-button" aria-label="Close dialog" onClick={onClose} type="button">×</button></header>
      {children}
    </section>
  </div>
}

export function ConfirmDialog({ title, description, confirmLabel, pending, onConfirm, onClose }: { title: string; description: string; confirmLabel: string; pending: boolean; onConfirm: () => void; onClose: () => void }) {
  return <Modal title={title} onClose={onClose}>
    <div className="modal__body"><p className="muted-copy">{description}</p></div>
    <footer className="modal__footer"><button className="button button--secondary" onClick={onClose} type="button">Keep it</button><button className="button button--danger" disabled={pending} onClick={onConfirm} type="button">{pending ? 'Working…' : confirmLabel}</button></footer>
  </Modal>
}

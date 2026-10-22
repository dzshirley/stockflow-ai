import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

interface ToastItem { id: number; message: string; tone: 'success' | 'error' }
interface ToastContextValue { notify: (message: string, tone?: ToastItem['tone']) => void }
const ToastContext = createContext<ToastContextValue | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const notify = useCallback((message: string, tone: ToastItem['tone'] = 'success') => {
    const id = Date.now() + Math.random()
    setItems((current) => [...current, { id, message, tone }])
    window.setTimeout(() => setItems((current) => current.filter((item) => item.id !== id)), 4500)
  }, [])
  const value = useMemo(() => ({ notify }), [notify])
  return <ToastContext.Provider value={value}>{children}<div className="toast-region" aria-live="polite">{items.map((item) => <div className={`toast toast--${item.tone}`} key={item.id}>{item.message}</div>)}</div></ToastContext.Provider>
}

// Context hooks intentionally share the component module with their provider.
// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  const value = useContext(ToastContext)
  if (!value) throw new Error('useToast must be used inside ToastProvider')
  return value
}

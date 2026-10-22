export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return <nav className="pagination" aria-label="Pagination">
    <button type="button" className="button button--secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>Previous</button>
    <span>Page <strong>{page + 1}</strong> of {totalPages}</span>
    <button type="button" className="button button--secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
  </nav>
}

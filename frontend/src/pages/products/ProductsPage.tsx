import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '../../components/AppShell'
import { EmptyState, ErrorPanel, PageSkeleton } from '../../components/Feedback'
import { ConfirmDialog } from '../../components/Modal'
import { Pagination } from '../../components/Pagination'
import { StatusBadge } from '../../components/StatusBadge'
import { useToast } from '../../components/Toast'
import { ProductForm } from '../../features/products/ProductForm'
import { useAuth } from '../../features/auth/AuthProvider'
import { archiveProduct, createProduct, listProducts, updateProduct } from '../../services/products'
import { getStore } from '../../services/store'
import type { Product, ProductInput, ProductStatus, ProductUpdate } from '../../types/api'
import { formatMoney } from '../../utils/format'

export function ProductsPage() {
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const { user } = useAuth()
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [status, setStatus] = useState<ProductStatus>('ACTIVE')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Product | 'new' | null>(null)
  const [archiving, setArchiving] = useState<Product | null>(null)
  const products = useQuery({ queryKey: ['products', { search, category, status, page }], queryFn: () => listProducts({ q: search, category: category.trim(), status, page, size: 12, sort: 'name', direction: 'ASC' }) })
  const store = useQuery({ queryKey: ['store'], queryFn: getStore })
  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['products'] }),
      queryClient.invalidateQueries({ queryKey: ['inventory'] }),
      queryClient.invalidateQueries({ queryKey: ['analytics'] }),
    ])
  }
  const save = useMutation({
    mutationFn: (input: ProductInput | ProductUpdate) => editing === 'new' ? createProduct(input as ProductInput) : updateProduct((editing as Product).id, input),
    onSuccess: async () => { await refresh(); notify(editing === 'new' ? 'Product added.' : 'Product updated.'); setEditing(null) },
  })
  const archive = useMutation({ mutationFn: archiveProduct, onSuccess: async () => { await refresh(); notify('Product archived.'); setArchiving(null) } })

  function submitSearch(event: React.FormEvent) { event.preventDefault(); setPage(0); setSearch(query.trim()) }

  return <>
    <PageHeader eyebrow="Catalog" title="Products" description="Manage the sellable catalog, pricing, and product lifecycle." action={<button className="button button--primary" onClick={() => setEditing('new')}>Add product</button>} />
    <form className="toolbar" role="search" onSubmit={submitSearch}>
      <label className="search-field"><span className="sr-only">Search products</span><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search name or SKU" /><button type="submit">Search</button></label>
      <label className="category-field"><span>Category</span><input value={category} onChange={(e) => { setCategory(e.target.value); setPage(0) }} placeholder="Exact category" /></label>
      <label className="select-field"><span>Status</span><select value={status} onChange={(e) => { setStatus(e.target.value as ProductStatus); setPage(0) }}><option value="ACTIVE">Active</option><option value="ARCHIVED">Archived</option></select></label>
    </form>
    {products.isPending || store.isPending ? <PageSkeleton rows={6} /> : products.error || store.error ? <ErrorPanel error={products.error ?? store.error} onRetry={() => { products.refetch(); store.refetch() }} /> : !products.data.items.length ? <EmptyState title="No matching products" description={search || category ? 'Try different search, category, or status filters.' : 'Build your catalog by adding the first product.'} action={status === 'ACTIVE' ? <button className="button button--secondary" onClick={() => setEditing('new')}>Add product</button> : undefined} /> : <>
      <div className="table-wrap"><table><thead><tr><th>Product</th><th>Category</th><th>Price</th><th>Cost</th><th>Status</th><th><span className="sr-only">Actions</span></th></tr></thead><tbody>{products.data.items.map((product) => <tr key={product.id}>
        <td><Link className="table-primary" to={`/products/${product.id}`}>{product.name}<small>{product.sku}</small></Link></td><td>{product.category}</td><td>{formatMoney(product.price, store.data.currencyCode)}</td><td>{formatMoney(product.unitCost, store.data.currencyCode)}</td><td><StatusBadge status={product.status} /></td><td><div className="table-actions"><button className="button-link" onClick={() => setEditing(product)}>Edit</button>{user?.role === 'ADMIN' && product.status === 'ACTIVE' && <button className="button-link button-link--danger" onClick={() => setArchiving(product)}>Archive</button>}</div></td>
      </tr>)}</tbody></table></div>
      <Pagination page={products.data.page} totalPages={products.data.totalPages} onChange={setPage} />
    </>}
    {editing && <ProductForm product={editing === 'new' ? undefined : editing} pending={save.isPending} onSubmit={async (input) => { await save.mutateAsync(input) }} onClose={() => setEditing(null)} />}
    {archiving && <ConfirmDialog title="Archive product?" description={`${archiving.name} will disappear from active catalog and new order selection. Historical records remain intact.`} confirmLabel="Archive product" pending={archive.isPending} onClose={() => setArchiving(null)} onConfirm={() => archive.mutate(archiving.id)} />}
  </>
}

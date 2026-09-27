import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { useHousehold } from '../context/HouseholdContext'
import { useNetworkStatus } from '../hooks/useNetworkStatus'

type Item = {
  id: string
  name: string
  quantity?: number | null
  unit?: string | null
  category?: string | null
  recurring: boolean
  done: boolean
}

type ShoppingList = {
  id: string
  householdId: string
  name: string
  items: Item[]
}

const COMMON_CATEGORIES = [
  'Fruita i verdura',
  'Lactis i ous',
  'Carnisseria',
  'Peixateria',
  'Forn i pa',
  'Begudes',
  'Neteja',
  'Higiene',
  'Congelats',
  'Rebost',
]

export function Shopping() {
  const { t } = useTranslation()
  const online = useNetworkStatus()
  const { household } = useHousehold()
  const queryClient = useQueryClient()

  const [selectedListId, setSelectedListId] = useState<string | null>(null)
  const [groupByCategory, setGroupByCategory] = useState(true)

  // Item form state
  const [name, setName] = useState('')
  const [quantity, setQuantity] = useState<string>('')
  const [unit, setUnit] = useState('')
  const [category, setCategory] = useState('')
  const [recurring, setRecurring] = useState(false)

  // List management state
  const [isCreatingList, setIsCreatingList] = useState(false)
  const [newListName, setNewListName] = useState('')
  const [editingListId, setEditingListId] = useState<string | null>(null)
  const [editListName, setEditListName] = useState('')

  const listsQuery = useQuery({
    queryKey: ['shopping', 'lists', household?.id],
    queryFn: async () => {
      return api<ShoppingList[]>(`/households/${household!.id}/shopping/lists`)
    },
    enabled: !!household,
  })

  const lists = useMemo(() => listsQuery.data ?? [], [listsQuery.data])
  const activeList = useMemo(() => {
    if (!lists.length) return null
    if (selectedListId) {
      const found = lists.find((l) => l.id === selectedListId)
      if (found) return found
    }
    return lists[0]
  }, [lists, selectedListId])

  const invalidate = {
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['shopping'] }),
  }

  // Mutations
  const createListMutation = useMutation({
    mutationFn: (listName: string) =>
      api<ShoppingList>(`/households/${household!.id}/shopping/lists`, {
        method: 'POST',
        body: JSON.stringify({ name: listName }),
      }),
    onSuccess: async (created) => {
      setNewListName('')
      setIsCreatingList(false)
      setSelectedListId(created.id)
      await queryClient.invalidateQueries({ queryKey: ['shopping'] })
    },
  })

  const updateListMutation = useMutation({
    mutationFn: ({ listId, listName }: { listId: string; listName: string }) =>
      api<ShoppingList>(`/households/${household!.id}/shopping/lists/${listId}`, {
        method: 'PUT',
        body: JSON.stringify({ name: listName }),
      }),
    onSuccess: async () => {
      setEditingListId(null)
      await queryClient.invalidateQueries({ queryKey: ['shopping'] })
    },
  })

  const deleteListMutation = useMutation({
    mutationFn: (listId: string) =>
      api<void>(`/households/${household!.id}/shopping/lists/${listId}`, {
        method: 'DELETE',
      }),
    onSuccess: async () => {
      setSelectedListId(null)
      await queryClient.invalidateQueries({ queryKey: ['shopping'] })
    },
  })

  const addItemMutation = useMutation({
    mutationFn: () => {
      const parsedQty = quantity.trim() ? Number(quantity) : null
      return api<Item>(`/households/${household!.id}/shopping/lists/${activeList!.id}/items`, {
        method: 'POST',
        body: JSON.stringify({
          name: name.trim(),
          quantity: parsedQty,
          unit: unit.trim() || null,
          category: category.trim() || null,
          recurring,
        }),
      })
    },
    onSuccess: async () => {
      setName('')
      setQuantity('')
      setUnit('')
      setRecurring(false)
      await queryClient.invalidateQueries({ queryKey: ['shopping'] })
    },
  })

  const updateItemMutation = useMutation({
    mutationFn: ({ itemId, patch }: { itemId: string; patch: Partial<Item> }) =>
      api<Item>(`/households/${household!.id}/shopping/items/${itemId}`, {
        method: 'PATCH',
        body: JSON.stringify(patch),
      }),
    ...invalidate,
  })

  const removeItemMutation = useMutation({
    mutationFn: (itemId: string) =>
      api<void>(`/households/${household!.id}/shopping/items/${itemId}`, { method: 'DELETE' }),
    ...invalidate,
  })

  const clearPurchasedMutation = useMutation({
    mutationFn: () =>
      api<{ removed: number }>(`/households/${household!.id}/shopping/lists/${activeList!.id}/clear-purchased`, {
        method: 'POST',
      }),
    ...invalidate,
  })

  // Grouped items by category
  const groupedCategories = useMemo(() => {
    if (!activeList) return []
    const map = new Map<string, Item[]>()
    for (const item of activeList.items) {
      const cat = item.category?.trim() || t('shopping.uncategorized')
      if (!map.has(cat)) map.set(cat, [])
      map.get(cat)!.push(item)
    }
    return Array.from(map.entries()).sort(([a], [b]) => {
      if (a === t('shopping.uncategorized')) return 1
      if (b === t('shopping.uncategorized')) return -1
      return a.localeCompare(b)
    })
  }, [activeList, t])

  const hasPurchased = activeList?.items.some((i) => i.done) ?? false

  return (
    <section className="panel">
      {!online && (
        <div role="status" style={{ padding: '0.5rem 0.75rem', background: 'var(--warning-bg)', borderRadius: '0.375rem', marginBottom: '0.75rem' }}>
          {t('offline')}
        </div>
      )}
      <div className="shopping-header-row">
        <h2>{t('shopping.title')}</h2>
        <div className="shopping-toolbar">
          <button
            type="button"
            className="link"
            onClick={() => setGroupByCategory((prev) => !prev)}
          >
            {groupByCategory ? '📋 ' + t('shopping.groupByCategory') : '📑 ' + t('shopping.groupByCategory')}
          </button>
          <button
            type="button"
            onClick={() => clearPurchasedMutation.mutate()}
            disabled={!hasPurchased || clearPurchasedMutation.isPending}
          >
            {t('shopping.clearPurchased')}
          </button>
        </div>
      </div>

      {/* Shopping lists selector & management */}
      <div className="shopping-header-row">
        <div className="shopping-tabs">
          {lists.map((l) => {
            const isActive = activeList?.id === l.id
            const pendingCount = l.items.filter((i) => !i.done).length
            return (
              <button
                key={l.id}
                type="button"
                className={`shopping-tab ${isActive ? 'active' : ''}`}
                onClick={() => {
                  setSelectedListId(l.id)
                  setEditingListId(null)
                }}
              >
                <span>{l.name}</span>
                <span className="shopping-tab-count">({pendingCount})</span>
              </button>
            )
          })}
          {!isCreatingList ? (
            <button
              type="button"
              className="shopping-tab"
              onClick={() => {
                setIsCreatingList(true)
                setNewListName('')
              }}
            >
              + {t('shopping.newList')}
            </button>
          ) : (
            <form
              className="create-form"
              style={{ margin: 0 }}
              onSubmit={(e) => {
                e.preventDefault()
                if (newListName.trim()) createListMutation.mutate(newListName.trim())
              }}
            >
              <input
                autoFocus
                value={newListName}
                onChange={(e) => setNewListName(e.target.value)}
                placeholder={t('shopping.listNamePlaceholder')}
                style={{ padding: '0.25rem 0.5rem', fontSize: '0.85rem' }}
              />
              <button type="submit" disabled={createListMutation.isPending}>
                {t('shopping.createList')}
              </button>
              <button
                type="button"
                className="link"
                onClick={() => setIsCreatingList(false)}
              >
                ✕
              </button>
            </form>
          )}
        </div>

        {/* Active list options (rename / delete) */}
        {activeList && lists.length > 1 && (
          <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center' }}>
            {editingListId === activeList.id ? (
              <form
                className="create-form"
                style={{ margin: 0 }}
                onSubmit={(e) => {
                  e.preventDefault()
                  if (editListName.trim()) {
                    updateListMutation.mutate({
                      listId: activeList.id,
                      listName: editListName.trim(),
                    })
                  }
                }}
              >
                <input
                  autoFocus
                  value={editListName}
                  onChange={(e) => setEditListName(e.target.value)}
                  style={{ padding: '0.25rem 0.5rem', fontSize: '0.85rem' }}
                />
                <button type="submit" disabled={updateListMutation.isPending}>
                  {t('account.save')}
                </button>
                <button
                  type="button"
                  className="link"
                  onClick={() => setEditingListId(null)}
                >
                  ✕
                </button>
              </form>
            ) : (
              <>
                <button
                  type="button"
                  className="icon-btn"
                  title={t('shopping.renameList')}
                  onClick={() => {
                    setEditingListId(activeList.id)
                    setEditListName(activeList.name)
                  }}
                >
                  ✏️
                </button>
                <button
                  type="button"
                  className="icon-btn"
                  title={t('shopping.deleteList')}
                  onClick={() => {
                    if (window.confirm(t('shopping.confirmDeleteList', { name: activeList.name }))) {
                      deleteListMutation.mutate(activeList.id)
                    }
                  }}
                >
                  🗑️
                </button>
              </>
            )}
          </div>
        )}
      </div>

      {/* Add Item form */}
      {activeList && (
        <form
          className="create-form shopping-form"
          onSubmit={(e) => {
            e.preventDefault()
            if (name.trim()) addItemMutation.mutate()
          }}
        >
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t('shopping.addPlaceholder')}
            required
          />
          <input
            type="number"
            step="any"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            placeholder={t('shopping.quantity')}
          />
          <input
            value={unit}
            onChange={(e) => setUnit(e.target.value)}
            placeholder={t('shopping.unit')}
          />
          <input
            list="shopping-categories"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            placeholder={t('shopping.categoryPlaceholder')}
          />
          <datalist id="shopping-categories">
            {COMMON_CATEGORIES.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>

          <label className="inline" title={t('shopping.recurringHelp')}>
            <input
              type="checkbox"
              checked={recurring}
              onChange={(e) => setRecurring(e.target.checked)}
            />
            <span style={{ fontSize: '0.82rem', whiteSpace: 'nowrap' }}>
              🔁 {t('shopping.recurring')}
            </span>
          </label>

          <button type="submit" disabled={addItemMutation.isPending}>
            {t('shopping.add')}
          </button>
        </form>
      )}

      {listsQuery.isPending && <p>{t('loading')}</p>}
      {listsQuery.isError && <p role="alert">{t('error')}</p>}

      {/* Items list */}
      {activeList && activeList.items.length === 0 && (
        <p style={{ color: 'var(--muted)', marginTop: '1.5rem' }}>{t('shopping.empty')}</p>
      )}

      {activeList && activeList.items.length > 0 && (
        <>
          {groupByCategory ? (
            groupedCategories.map(([catName, categoryItems]) => (
              <div key={catName} className="shopping-category-group">
                <div className="shopping-category-header">
                  <span>{catName}</span>
                  <span style={{ fontSize: '0.75rem', fontWeight: 500, opacity: 0.75 }}>
                    {categoryItems.filter((i) => !i.done).length} / {categoryItems.length}
                  </span>
                </div>
                <ul className="item-list">
                  {categoryItems.map((item) => (
                    <ShoppingItemRow
                      key={item.id}
                      item={item}
                      showCategoryBadge={false}
                      onToggleDone={() =>
                        updateItemMutation.mutate({
                          itemId: item.id,
                          patch: { done: !item.done },
                        })
                      }
                      onToggleRecurring={() =>
                        updateItemMutation.mutate({
                          itemId: item.id,
                          patch: { recurring: !item.recurring },
                        })
                      }
                      onRemove={() => removeItemMutation.mutate(item.id)}
                    />
                  ))}
                </ul>
              </div>
            ))
          ) : (
            <ul className="item-list">
              {activeList.items.map((item) => (
                <ShoppingItemRow
                  key={item.id}
                  item={item}
                  showCategoryBadge={true}
                  onToggleDone={() =>
                    updateItemMutation.mutate({
                      itemId: item.id,
                      patch: { done: !item.done },
                    })
                  }
                  onToggleRecurring={() =>
                    updateItemMutation.mutate({
                      itemId: item.id,
                      patch: { recurring: !item.recurring },
                    })
                  }
                  onRemove={() => removeItemMutation.mutate(item.id)}
                />
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  )
}

function ShoppingItemRow({
  item,
  showCategoryBadge,
  onToggleDone,
  onToggleRecurring,
  onRemove,
}: {
  item: Item
  showCategoryBadge: boolean
  onToggleDone: () => void
  onToggleRecurring: () => void
  onRemove: () => void
}) {
  const { t } = useTranslation()

  const quantityLabel = useMemo(() => {
    if (item.quantity == null && !item.unit) return null
    if (item.quantity != null && item.unit) return `${item.quantity} ${item.unit}`
    if (item.quantity != null) return `${item.quantity}`
    return item.unit
  }, [item.quantity, item.unit])

  return (
    <li className={item.done ? 'done' : ''}>
      <label className="inline shopping-item-content">
        <input type="checkbox" checked={item.done} onChange={onToggleDone} />
        <span style={{ fontWeight: 500 }}>{item.name}</span>

        {quantityLabel && (
          <span className="shopping-badge shopping-badge-qty">{quantityLabel}</span>
        )}

        {showCategoryBadge && item.category && (
          <span className="shopping-badge">{item.category}</span>
        )}

        {item.recurring && (
          <span className="shopping-badge shopping-badge-recurring" title={t('shopping.recurringHelp')}>
            🔁 {t('shopping.recurringBadge')}
          </span>
        )}
      </label>

      <div className="shopping-item-actions">
        <button
          type="button"
          className={`icon-btn ${item.recurring ? 'active' : ''}`}
          title={t('shopping.toggleRecurring')}
          onClick={onToggleRecurring}
        >
          🔁
        </button>
        <button type="button" className="link" onClick={onRemove}>
          ✕
        </button>
      </div>
    </li>
  )
}

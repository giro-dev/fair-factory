import { useEffect, useMemo, useState } from 'react'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { api } from '../api/client'
import type { Recipe, RecipePage, RecipeSearchBody } from '../api/types'
import { useHousehold } from '../context/HouseholdContext'
import { RecipeForm, type RecipePayload } from '../components/RecipeForm'
import { RecipeImportDialog } from '../components/RecipeImportDialog'

const PAGE_SIZE = 24
const SORTS = ['name', 'createdAt', 'servings'] as const
type Sort = (typeof SORTS)[number]

function useDebounced<T>(value: T, delay = 350): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(id)
  }, [value, delay])
  return debounced
}

function searchBody(query: string): RecipeSearchBody {
  const q = query.trim()
  if (!q) return { filter: { and: [] } }
  return {
    filter: {
      or: [
        { field: 'name', operator: 'LIKE', value: q },
        { field: 'description', operator: 'LIKE', value: q },
        { field: 'notes', operator: 'LIKE', value: q },
      ],
    },
  }
}

function RecipeImage({ recipe, className }: { recipe: Recipe; className?: string }) {
  if (!recipe.imageMimeType || !recipe.imageData) return null
  return (
    <img
      src={`data:${recipe.imageMimeType};base64,${recipe.imageData}`}
      alt={recipe.name ?? ''}
      className={className}
    />
  )
}

export function Recipes() {
  const { t } = useTranslation()
  const { household } = useHousehold()
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [ingredient, setIngredient] = useState('')
  const [tag, setTag] = useState<string | null>(null)
  const [sort, setSort] = useState<Sort>('name')
  const [direction, setDirection] = useState<'asc' | 'desc'>('asc')
  const [view, setView] = useState<'list' | 'grid'>('grid')
  const [isCreating, setIsCreating] = useState(false)
  const [isImporting, setIsImporting] = useState(false)

  const debouncedQuery = useDebounced(query)
  const debouncedIngredient = useDebounced(ingredient)

  // Full list only feeds the tag chip suggestions (names/ingredients stay server-filtered).
  const recipes = useQuery({
    queryKey: ['recipes', household?.id],
    queryFn: () => api<Recipe[]>(`/households/${household!.id}/recipes`),
    enabled: !!household,
  })

  const tags = useMemo(() => {
    const all = (recipes.data ?? []).flatMap((r) => r.tags ?? [])
    return [...new Set(all)].sort((a, b) => a.localeCompare(b))
  }, [recipes.data])

  const search = useInfiniteQuery({
    queryKey: [
      'recipe-search',
      household?.id,
      debouncedQuery,
      debouncedIngredient,
      tag,
      sort,
      direction,
    ],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({
        page: String(pageParam),
        size: String(PAGE_SIZE),
        sort,
        direction,
      })
      if (tag) params.set('tag', tag)
      if (debouncedIngredient.trim()) params.set('ingredient', debouncedIngredient.trim())
      return api<RecipePage>(`/households/${household!.id}/recipes/search?${params}`, {
        method: 'POST',
        body: JSON.stringify(searchBody(debouncedQuery)),
      })
    },
    initialPageParam: 0,
    getNextPageParam: (last, pages) => (last.hasMore ? pages.length : undefined),
    enabled: !!household,
  })

  const results = search.data?.pages.flatMap((p) => p.content ?? []) ?? []
  const totalHits = search.data?.pages[0]?.totalHits ?? 0
  const hasActiveFilter =
    debouncedQuery.trim() !== '' || debouncedIngredient.trim() !== '' || tag !== null

  const create = useMutation({
    mutationFn: (payload: RecipePayload) =>
      api<Recipe>(`/households/${household!.id}/recipes`, {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: async () => {
      setIsCreating(false)
      await queryClient.invalidateQueries({ queryKey: ['recipe-search', household?.id] })
      await queryClient.invalidateQueries({ queryKey: ['recipes', household?.id] })
    },
  })

  const RecipeInfo = ({ recipe }: { recipe: Recipe }) => (
    <>
      <h4>{recipe.name}</h4>
      <span className="meta">{t('recipes.servingsCount', { count: recipe.servings ?? 0 })}</span>
      {recipe.tags && recipe.tags.length > 0 && (
        <span className="recipe-tag-chips">
          {recipe.tags.map((recipeTag) => (
            <span key={recipeTag} className="member-badge">{recipeTag}</span>
          ))}
        </span>
      )}
      {recipe.description && <p className="recipe-description">{recipe.description}</p>}
      {recipe.ingredients && recipe.ingredients.length > 0 && (
        <p className="ingredient-list">
          {recipe.ingredients.map((i) => i.name).filter(Boolean).join(', ')}
        </p>
      )}
    </>
  )

  return (
    <section className="panel">
      <div className="recipes-toolbar">
        <div className="recipes-toolbar-header">
          <h2>{t('recipes.title')}</h2>
          <span className="meta">{t('recipes.totalCount', { count: totalHits })}</span>
          <button type="button" className="ghost" onClick={() => setIsImporting(true)}>
            {t('recipes.importBook')}
          </button>
        </div>
        <div className="recipes-search-bar">
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('recipes.searchPlaceholder')}
            aria-label={t('recipes.searchPlaceholder')}
          />
          <input
            type="search"
            value={ingredient}
            onChange={(e) => setIngredient(e.target.value)}
            placeholder={t('recipes.ingredientPlaceholder')}
            aria-label={t('recipes.ingredientPlaceholder')}
            className="ingredient-search"
          />
          <select
            value={sort}
            onChange={(e) => setSort(e.target.value as Sort)}
            aria-label={t('recipes.sort')}
          >
            {SORTS.map((option) => (
              <option key={option} value={option}>
                {t(`recipes.sortBy.${option}`)}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="ghost sort-direction"
            aria-label={t('recipes.sortDirection')}
            title={t('recipes.sortDirection')}
            onClick={() => setDirection(direction === 'asc' ? 'desc' : 'asc')}
          >
            {direction === 'asc' ? '↑' : '↓'}
          </button>
          <div className="view-toggle" role="group" aria-label={t('recipes.view')}>
            <button
              type="button"
              className={view === 'list' ? 'active' : ''}
              onClick={() => setView('list')}
            >
              {t('recipes.list')}
            </button>
            <button
              type="button"
              className={view === 'grid' ? 'active' : ''}
              onClick={() => setView('grid')}
            >
              {t('recipes.grid')}
            </button>
          </div>
        </div>
        {tags.length > 0 && (
          <div className="tag-filter-bar" role="group" aria-label={t('recipes.tagFilter')}>
            {tags.map((option) => (
              <button
                key={option}
                type="button"
                className={`tag-chip${tag === option ? ' active' : ''}`}
                onClick={() => setTag(tag === option ? null : option)}
              >
                {option}
              </button>
            ))}
          </div>
        )}
      </div>

      {search.isPending && <p>{t('loading')}</p>}
      {search.isError && <p role="alert">{t('error')}</p>}

      {view === 'grid' ? (
        <div className="recipe-grid">
          {results.map((recipe) => (
            <Link key={recipe.id} to={`/receptes/${recipe.id}`} className="recipe-card">
              <RecipeImage recipe={recipe} className="recipe-card-image" />
              <div className="recipe-card-body">
                <RecipeInfo recipe={recipe} />
              </div>
            </Link>
          ))}
        </div>
      ) : (
        <ul className="item-list recipe-list">
          {results.map((recipe) => (
            <li key={recipe.id} className="recipe-list-item">
              <Link to={`/receptes/${recipe.id}`} className="recipe-list-link">
                <RecipeImage recipe={recipe} className="recipe-list-thumb" />
                <div className="recipe-list-body">
                  <RecipeInfo recipe={recipe} />
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {search.hasNextPage && (
        <div className="footer-actions left">
          <button type="button" className="ghost" onClick={() => search.fetchNextPage()}>
            {t('recipes.loadMore')}
          </button>
        </div>
      )}

      {results.length === 0 && !search.isPending && !search.isError && (
        <p className="meta">
          {hasActiveFilter ? t('recipes.noResults') : t('recipes.empty')}
        </p>
      )}

      <button
        type="button"
        className="fab"
        aria-label={t('recipes.newRecipe')}
        onClick={() => setIsCreating(true)}
      >
        +
      </button>

      {isCreating && (
        <RecipeForm
          title={t('recipes.newRecipe')}
          pending={create.isPending}
          onClose={() => setIsCreating(false)}
          onSubmit={(payload) => create.mutate(payload)}
        />
      )}

      {isImporting && household && (
        <RecipeImportDialog householdId={household.id} onClose={() => setIsImporting(false)} />
      )}
    </section>
  )
}

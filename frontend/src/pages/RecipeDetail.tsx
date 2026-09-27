import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/client'
import type { Recipe } from '../api/types'
import { useHousehold } from '../context/HouseholdContext'
import { RecipeForm, type RecipePayload } from '../components/RecipeForm'

export function RecipeDetail() {
  const { t } = useTranslation()
  const { household } = useHousehold()
  const { recipeId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [isEditing, setIsEditing] = useState(false)

  const recipe = useQuery({
    queryKey: ['recipe', household?.id, recipeId],
    queryFn: () => api<Recipe>(`/households/${household!.id}/recipes/${recipeId}`),
    enabled: !!household && !!recipeId,
  })

  const update = useMutation({
    mutationFn: (payload: RecipePayload) =>
      api<Recipe>(`/households/${household!.id}/recipes/${recipeId}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      }),
    onSuccess: async () => {
      setIsEditing(false)
      await queryClient.invalidateQueries({ queryKey: ['recipe', household?.id, recipeId] })
      await queryClient.invalidateQueries({ queryKey: ['recipe-search', household?.id] })
      await queryClient.invalidateQueries({ queryKey: ['recipes', household?.id] })
    },
  })

  const remove = useMutation({
    mutationFn: () =>
      api<void>(`/households/${household!.id}/recipes/${recipeId}`, { method: 'DELETE' }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['recipe-search', household?.id] })
      await queryClient.invalidateQueries({ queryKey: ['recipes', household?.id] })
      navigate('/receptes')
    },
  })

  if (recipe.isPending) return <section className="panel"><p>{t('loading')}</p></section>
  if (recipe.isError || !recipe.data)
    return <section className="panel"><p role="alert">{t('error')}</p></section>

  const r = recipe.data

  return (
    <section className="panel recipe-detail">
      <div className="recipe-detail-header">
        <Link to="/receptes" className="link">
          ← {t('recipes.title')}
        </Link>
        <div className="footer-actions left">
          <button type="button" className="ghost" onClick={() => setIsEditing(true)}>
            {t('recipes.edit')}
          </button>
          <button
            type="button"
            className="ghost danger"
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm(t('recipes.deleteConfirm', { name: r.name }))) remove.mutate()
            }}
          >
            {t('recipes.delete')}
          </button>
        </div>
      </div>

      <div className="recipe-detail-body">
        {r.imageMimeType && r.imageData && (
          <img
            src={`data:${r.imageMimeType};base64,${r.imageData}`}
            alt={r.name ?? ''}
            className="recipe-detail-image"
          />
        )}
        <div className="recipe-detail-main">
          <h2>{r.name}</h2>
          <span className="meta">{t('recipes.servingsCount', { count: r.servings ?? 0 })}</span>
          {r.tags && r.tags.length > 0 && (
            <span className="recipe-tag-chips">
              {r.tags.map((tag) => (
                <span key={tag} className="member-badge">{tag}</span>
              ))}
            </span>
          )}
          {r.description && <p className="recipe-description">{r.description}</p>}
          {r.notes && <p className="recipe-notes">{r.notes}</p>}
        </div>
      </div>

      {r.ingredients && r.ingredients.length > 0 && (
        <section className="recipe-detail-section">
          <h3>{t('recipes.ingredients')}</h3>
          <table className="ingredient-table">
            <thead>
              <tr>
                <th>{t('recipes.ingredientName')}</th>
                <th>{t('recipes.quantity')}</th>
                <th>{t('recipes.unit')}</th>
                <th>{t('recipes.category')}</th>
              </tr>
            </thead>
            <tbody>
              {r.ingredients.map((ingredient, i) => (
                <tr key={ingredient.id ?? i}>
                  <td>{ingredient.name}</td>
                  <td>{ingredient.quantity ?? ''}</td>
                  <td>{ingredient.unit ?? ''}</td>
                  <td>{ingredient.category ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {r.steps && r.steps.length > 0 && (
        <section className="recipe-detail-section">
          <h3>{t('recipes.steps')}</h3>
          <ol className="steps-list detail">
            {r.steps.map((step, i) => (
              <li key={i}>{step}</li>
            ))}
          </ol>
        </section>
      )}

      {isEditing && (
        <RecipeForm
          title={t('recipes.edit')}
          initial={r}
          pending={update.isPending}
          onClose={() => setIsEditing(false)}
          onSubmit={(payload) => update.mutate(payload)}
        />
      )}
    </section>
  )
}

import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { useHousehold } from '../context/HouseholdContext'

type MealType = 'BREAKFAST' | 'LUNCH' | 'SNACK' | 'DINNER'
type MealSlot = { id: string; dayOfWeek: number; mealType: MealType; text?: string; recipeId?: string }
type MealPlan = { id?: string; householdId: string; isoYear: number; isoWeek: number; slots: MealSlot[] }
type Ingredient = { id?: string; name: string; quantity?: number; unit?: string; category?: string }
type Recipe = {
  id: string
  name: string
  servings: number
  notes?: string
  description?: string
  steps?: string[]
  tags?: string[]
  imageMimeType?: string
  imageData?: string
  ingredients?: Ingredient[]
}

const MEAL_TYPES: MealType[] = ['BREAKFAST', 'LUNCH', 'SNACK', 'DINNER']

function isoWeek(date: Date): { year: number; week: number } {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const day = d.getUTCDay() || 7
  d.setUTCDate(d.getUTCDate() + 4 - day)
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1))
  return { year: d.getUTCFullYear(), week: Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7) }
}

type SlotDetailProps = {
  title: string
  slot: MealSlot
  recipe?: Recipe
  onEdit: () => void
  onClose: () => void
}

function SlotDetail({ title, slot, recipe, onEdit, onClose }: SlotDetailProps) {
  const { t } = useTranslation()
  return (
    <div className="menu-detail">
      <div className="menu-detail-header">
        <h3>{title}</h3>
        <div className="menu-detail-actions">
          <button type="button" className="link" onClick={onEdit}>{t('menu.edit')}</button>
          <button type="button" className="link" onClick={onClose} aria-label={t('menu.close')}>×</button>
        </div>
      </div>
      {recipe && (
        <>
          <h4>{recipe.name}</h4>
          <span className="meta">{t('recipes.servingsCount', { count: recipe.servings })}</span>
          {recipe.imageMimeType && recipe.imageData && (
            <img
              src={`data:${recipe.imageMimeType};base64,${recipe.imageData}`}
              alt={recipe.name}
              className="menu-detail-image"
            />
          )}
          {recipe.description && <p className="recipe-description">{recipe.description}</p>}
          {recipe.steps && recipe.steps.length > 0 && (
            <ol className="steps-list">
              {recipe.steps.map((s, i) => <li key={i}>{s}</li>)}
            </ol>
          )}
          {recipe.ingredients && recipe.ingredients.length > 0 && (
            <p className="ingredient-list">{recipe.ingredients.map((i) => i.name).join(', ')}</p>
          )}
          {recipe.notes && (
            <>
              <span className="meta">{t('menu.notes')}</span>
              <p>{recipe.notes}</p>
            </>
          )}
        </>
      )}
      {slot.text && (
        <>
          {recipe && <span className="meta">{t('menu.notes')}</span>}
          <p>{slot.text}</p>
        </>
      )}
    </div>
  )
}

export function Menu() {
  const { t, i18n } = useTranslation()
  const { household } = useHousehold()
  const queryClient = useQueryClient()
  const [offset, setOffset] = useState(0)
  const [editing, setEditing] = useState<{ day: number; meal: MealType } | null>(null)
  const [selected, setSelected] = useState<{ day: number; meal: MealType } | null>(null)
  const [text, setText] = useState('')
  const [recipeId, setRecipeId] = useState('')

  const base = new Date()
  base.setDate(base.getDate() + offset * 7)
  const { year, week } = isoWeek(base)

  useEffect(() => {
    setEditing(null)
    setSelected(null)
  }, [year, week])

  const plan = useQuery({
    queryKey: ['menu', household?.id, year, week],
    queryFn: () => api<MealPlan>(`/households/${household!.id}/menu?year=${year}&week=${week}`),
    enabled: !!household,
  })

  const recipes = useQuery({
    queryKey: ['recipes', household?.id],
    queryFn: () => api<Recipe[]>(`/households/${household!.id}/recipes`),
    enabled: !!household,
  })

  const setSlot = useMutation({
    mutationFn: (slot: { dayOfWeek: number; mealType: MealType; text: string; recipeId: string | null }) =>
      api<MealSlot>(`/households/${household!.id}/menu/slots?year=${year}&week=${week}`, {
        method: 'PUT', body: JSON.stringify(slot),
      }),
    onSuccess: async (_data, slot) => {
      setEditing((cur) =>
        cur && cur.day === slot.dayOfWeek && cur.meal === slot.mealType ? null : cur)
      await queryClient.invalidateQueries({ queryKey: ['menu'] })
    },
  })

  const dayNames = Array.from({ length: 7 }, (_, i) =>
    new Intl.DateTimeFormat(i18n.language, { weekday: 'long' }).format(new Date(2024, 0, 1 + i)))

  const slotFor = (day: number, meal: MealType) =>
    plan.data?.slots.find((s) => s.dayOfWeek === day && s.mealType === meal)

  const recipeName = (id?: string) => recipes.data?.find((r) => r.id === id)?.name

  const selectedSlot = selected ? slotFor(selected.day, selected.meal) : undefined
  const selectedRecipe = recipes.data?.find((r) => r.id === selectedSlot?.recipeId)

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>{t('menu.title')}</h2>
        <div className="week-nav">
          <button type="button" onClick={() => setOffset(offset - 1)}>←</button>
          <span>{t('menu.week', { week, year })}</span>
          <button type="button" onClick={() => setOffset(offset + 1)}>→</button>
        </div>
      </div>
      {plan.isPending && <p>{t('loading')}</p>}
      {plan.isError && <p role="alert">{t('error')}</p>}
      {plan.data && (
        <table className="menu-grid">
          <thead>
            <tr><th></th>{dayNames.map((d) => <th key={d}>{d}</th>)}</tr>
          </thead>
          <tbody>
            {MEAL_TYPES.map((meal) => (
              <tr key={meal}>
                <th>{t(`menu.meals.${meal}`)}</th>
                {dayNames.map((_, i) => {
                  const day = i + 1
                  const slot = slotFor(day, meal)
                  const isEditing = editing?.day === day && editing.meal === meal
                  const isSelected = selected?.day === day && selected.meal === meal
                  const hasContent = !!slot?.recipeId || !!slot?.text
                  const save = () => setSlot.mutate({
                    dayOfWeek: day, mealType: meal, text, recipeId: recipeId || null,
                  })
                  return (
                    <td key={day} className={isSelected ? 'selected' : undefined} onClick={() => {
                      if (isEditing) return
                      if (hasContent) {
                        setSelected(isSelected ? null : { day, meal })
                      } else {
                        setEditing({ day, meal }); setText(''); setRecipeId('')
                      }
                    }}>
                      {isEditing ? (
                        <form
                          onSubmit={(e) => { e.preventDefault(); save() }}
                          onBlur={(e) => {
                            const form = e.currentTarget
                            setTimeout(() => {
                              if (!form.contains(document.activeElement)) save()
                            }, 0)
                          }}
                        >
                          <select value={recipeId} onChange={(e) => setRecipeId(e.target.value)}>
                            <option value="">{t('menu.noRecipe')}</option>
                            {recipes.data?.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
                          </select>
                          <input value={text} onChange={(e) => setText(e.target.value)} autoFocus
                                 placeholder={t('menu.notesPlaceholder')} />
                        </form>
                      ) : (recipeName(slot?.recipeId) || slot?.text) ? (
                        <>
                          {recipeName(slot?.recipeId) && <strong>{recipeName(slot?.recipeId)}</strong>}
                          {slot?.text && <span className="slot-note">{slot.text}</span>}
                        </>
                      ) : <span className="empty-slot">·</span>}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {selected && selectedSlot && (selectedSlot.recipeId || selectedSlot.text) && (
        <SlotDetail
          title={`${dayNames[selected.day - 1]} · ${t(`menu.meals.${selected.meal}`)}`}
          slot={selectedSlot}
          recipe={selectedRecipe}
          onEdit={() => {
            setEditing({ day: selected.day, meal: selected.meal })
            setText(selectedSlot.text ?? '')
            setRecipeId(selectedSlot.recipeId ?? '')
            setSelected(null)
          }}
          onClose={() => setSelected(null)}
        />
      )}
    </section>
  )
}

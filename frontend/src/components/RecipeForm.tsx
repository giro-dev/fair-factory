import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Ingredient, Recipe } from '../api/types'
import { Modal } from './Modal'

export type RecipePayload = {
  name: string
  servings: number
  notes?: string
  description?: string
  steps: string[]
  tags: string[]
  ingredients: Ingredient[]
  imageMimeType?: string
  imageData?: string
}

type Image = { mimeType: string; data: string }

function fileToBase64(file: File): Promise<Image> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result as string
      const [prefix, data] = result.split(',')
      const mimeType = prefix.split(':')[1]?.split(';')[0] ?? file.type
      resolve({ mimeType, data: data ?? '' })
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

const emptyIngredient: Ingredient = { name: '' }

type RecipeFormProps = {
  title: string
  initial?: Recipe
  pending: boolean
  onClose: () => void
  onSubmit: (payload: RecipePayload) => void
}

export function RecipeForm({ title, initial, pending, onClose, onSubmit }: RecipeFormProps) {
  const { t } = useTranslation()
  const [name, setName] = useState(initial?.name ?? '')
  const [servings, setServings] = useState(initial?.servings ?? 4)
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [steps, setSteps] = useState<string[]>(initial?.steps?.length ? [...initial.steps] : [''])
  const [tags, setTags] = useState((initial?.tags ?? []).join(', '))
  const [image, setImage] = useState<Image | null>(
    initial?.imageMimeType && initial?.imageData
      ? { mimeType: initial.imageMimeType, data: initial.imageData }
      : null,
  )
  const [ingredients, setIngredients] = useState<Ingredient[]>(
    initial?.ingredients?.length ? initial.ingredients.map((i) => ({ ...i })) : [{ ...emptyIngredient }],
  )

  const updateIngredient = (index: number, patch: Partial<Ingredient>) => {
    setIngredients(ingredients.map((ing, i) => (i === index ? { ...ing, ...patch } : ing)))
  }

  const updateStep = (index: number, value: string) => {
    setSteps(steps.map((s, i) => (i === index ? value : s)))
  }

  const handleImage = async (file: File | undefined) => {
    setImage(file ? await fileToBase64(file) : null)
  }

  const submit = () => {
    onSubmit({
      name: name.trim(),
      servings,
      notes: notes.trim() || undefined,
      description: description.trim() || undefined,
      steps: steps.map((s) => s.trim()).filter(Boolean),
      tags: tags.split(',').map((tag) => tag.trim()).filter(Boolean),
      ingredients: ingredients.filter((i) => i.name?.trim()),
      imageMimeType: image?.mimeType,
      imageData: image?.data,
    })
  }

  return (
    <Modal title={title} onClose={onClose}>
      <form
        className="create-form modal-form"
        onSubmit={(e) => {
          e.preventDefault()
          if (name.trim()) submit()
        }}
      >
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={t('recipes.namePlaceholder')}
          required
          autoFocus
        />
        <label className="inline">
          {t('recipes.servings')}
          <input
            type="number"
            min={1}
            value={servings}
            onChange={(e) => setServings(Number(e.target.value))}
          />
        </label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder={t('recipes.descriptionPlaceholder')}
          rows={3}
        />
        <input
          value={tags}
          onChange={(e) => setTags(e.target.value)}
          placeholder={t('recipes.tagsPlaceholder')}
        />
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder={t('recipes.notesPlaceholder')}
          rows={2}
        />
        <fieldset>
          <legend>{t('recipes.steps')}</legend>
          <ol className="steps-list">
            {steps.map((step, i) => (
              <li key={i}>
                <input
                  value={step}
                  placeholder={t('recipes.stepPlaceholder', { number: i + 1 })}
                  onChange={(e) => updateStep(i, e.target.value)}
                />
              </li>
            ))}
          </ol>
          <button type="button" className="link" onClick={() => setSteps([...steps, ''])}>
            {t('recipes.addStep')}
          </button>
        </fieldset>
        <fieldset>
          <legend>{t('recipes.ingredients')}</legend>
          {ingredients.map((ingredient, i) => (
            <div key={i} className="ingredient-row">
              <input
                value={ingredient.name ?? ''}
                placeholder={t('recipes.ingredientName')}
                onChange={(e) => updateIngredient(i, { name: e.target.value })}
              />
              <input
                type="number"
                value={ingredient.quantity ?? ''}
                placeholder={t('recipes.quantity')}
                onChange={(e) =>
                  updateIngredient(i, { quantity: e.target.value ? Number(e.target.value) : undefined })
                }
              />
              <input
                value={ingredient.unit ?? ''}
                placeholder={t('recipes.unit')}
                onChange={(e) => updateIngredient(i, { unit: e.target.value })}
              />
              <input
                value={ingredient.category ?? ''}
                placeholder={t('recipes.category')}
                onChange={(e) => updateIngredient(i, { category: e.target.value })}
              />
            </div>
          ))}
          <button
            type="button"
            className="link"
            onClick={() => setIngredients([...ingredients, { ...emptyIngredient }])}
          >
            {t('recipes.addIngredient')}
          </button>
        </fieldset>
        <label className="inline file-label">
          {t('recipes.image')}
          <input type="file" accept="image/*" onChange={(e) => handleImage(e.target.files?.[0])} />
        </label>
        {image && (
          <img
            src={`data:${image.mimeType};base64,${image.data}`}
            alt={t('recipes.preview')}
            className="recipe-thumb"
          />
        )}
        <div className="footer-actions">
          <button type="submit" disabled={pending}>
            {t('recipes.save')}
          </button>
        </div>
      </form>
    </Modal>
  )
}

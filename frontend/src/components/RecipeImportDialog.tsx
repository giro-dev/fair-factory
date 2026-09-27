import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api, apiUpload } from '../api/client'
import type { RecipeImportOutcome, RecipeImportSession } from '../api/types'
import { Modal } from './Modal'

type ImportPhase =
  | { step: 'source' }
  | { step: 'candidates'; session: RecipeImportSession }
  | { step: 'result'; session: RecipeImportSession; outcome: RecipeImportOutcome }

type RecipeImportDialogProps = {
  householdId: string
  onClose: () => void
}

export function RecipeImportDialog({ householdId, onClose }: RecipeImportDialogProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [phase, setPhase] = useState<ImportPhase>({ step: 'source' })
  const [file, setFile] = useState<File | null>(null)
  const [url, setUrl] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [error, setError] = useState<string | null>(null)

  const importBase = `/households/${householdId}/recipes/import`

  const analyzeFile = useMutation({
    mutationFn: (book: File) => {
      const form = new FormData()
      form.append('file', book)
      return apiUpload<RecipeImportSession>(importBase, form)
    },
    onSuccess: (session) => {
      setError(null)
      setSelected(new Set(session.candidates?.map((c) => c.key) ?? []))
      setPhase({ step: 'candidates', session })
    },
    onError: (e) => setError(e.message),
  })

  const analyzeUrl = useMutation({
    mutationFn: (source: string) =>
      api<RecipeImportSession>(`${importBase}/url`, {
        method: 'POST',
        body: JSON.stringify({ url: source }),
      }),
    onSuccess: (session) => {
      setError(null)
      setSelected(new Set(session.candidates?.map((c) => c.key) ?? []))
      setPhase({ step: 'candidates', session })
    },
    onError: (e) => setError(e.message),
  })

  const confirm = useMutation({
    mutationFn: ({ importId, keys }: { importId: string; keys: string[] }) =>
      api<RecipeImportOutcome>(`${importBase}/${importId}/confirm`, {
        method: 'POST',
        body: JSON.stringify({ candidates: keys }),
      }),
    onSuccess: (outcome) => {
      queryClient.invalidateQueries({ queryKey: ['recipe-search', householdId] })
      queryClient.invalidateQueries({ queryKey: ['recipes', householdId] })
      if (phase.step === 'candidates') setPhase({ step: 'result', session: phase.session, outcome })
    },
    onError: (e) => setError(e.message),
  })

  const analyzing = analyzeFile.isPending || analyzeUrl.isPending

  const toggle = (key: string) => {
    const next = new Set(selected)
    if (next.has(key)) next.delete(key)
    else next.add(key)
    setSelected(next)
  }

  return (
    <Modal title={t('recipes.importTitle')} onClose={onClose} wide>
      {phase.step === 'source' && (
        <div className="import-wizard">
          <p className="assistant-hint">{t('recipes.importHint')}</p>
          <div className="import-source">
            <label className="file-label button ghost">
              {file ? file.name : t('recipes.importChooseFile')}
              <input
                type="file"
                accept=".pdf,.epub,application/pdf,application/epub+zip"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              />
            </label>
            <button
              disabled={!file || analyzing}
              onClick={() => file && analyzeFile.mutate(file)}
            >
              {analyzing ? t('recipes.importAnalyzing') : t('recipes.importAnalyzeFile')}
            </button>
          </div>
          <div className="import-source">
            <input
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder={t('recipes.importUrlPlaceholder')}
            />
            <button disabled={!url.trim() || analyzing} onClick={() => analyzeUrl.mutate(url.trim())}>
              {analyzing ? t('recipes.importAnalyzing') : t('recipes.importAnalyzeUrl')}
            </button>
          </div>
          {error && <p className="error">{error}</p>}
        </div>
      )}

      {phase.step === 'candidates' && (
        <div className="import-wizard">
          <p className="assistant-hint">
            {t('recipes.importFound', { count: phase.session.candidates?.length ?? 0 })}
            {phase.session.aiAvailable ? ` ${t('recipes.importAiOn')}` : ` ${t('recipes.importAiOff')}`}
          </p>
          <div className="footer-actions left">
            <button
              type="button"
              className="link"
              onClick={() =>
                setSelected(new Set(phase.session.candidates?.map((c) => c.key) ?? []))
              }
            >
              {t('recipes.importSelectAll')}
            </button>
            <button type="button" className="link" onClick={() => setSelected(new Set())}>
              {t('recipes.importSelectNone')}
            </button>
          </div>
          <ul className="import-candidates">
            {phase.session.candidates?.map((candidate) => (
              <li key={candidate.key}>
                <label>
                  <input
                    type="checkbox"
                    checked={selected.has(candidate.key)}
                    onChange={() => toggle(candidate.key)}
                  />
                  <span className="candidate-body">
                    <strong>{candidate.title}</strong>
                    {candidate.structured && (
                      <span className="member-badge">{t('recipes.importStructured')}</span>
                    )}
                    {candidate.snippet && <span className="candidate-snippet">{candidate.snippet}</span>}
                  </span>
                </label>
              </li>
            ))}
          </ul>
          {error && <p className="error">{error}</p>}
          <div className="footer-actions">
            <button
              className="ghost"
              onClick={() => {
                setPhase({ step: 'source' })
                setError(null)
              }}
            >
              {t('recipes.back')}
            </button>
            <button
              disabled={selected.size === 0 || confirm.isPending}
              onClick={() =>
                confirm.mutate({ importId: phase.session.id, keys: [...selected] })
              }
            >
              {confirm.isPending
                ? t('recipes.importImporting')
                : t('recipes.importConfirm', { count: selected.size })}
            </button>
          </div>
        </div>
      )}

      {phase.step === 'result' && (
        <div className="import-wizard">
          <p>
            {t('recipes.importDone', {
              count: phase.outcome.imported?.length ?? 0,
              source: phase.session.source,
            })}
          </p>
          <ul className="import-results">
            {phase.outcome.imported?.map((recipe) => (
              <li key={recipe.id} className="ok">
                {recipe.name}
              </li>
            ))}
            {phase.outcome.failures?.map((failure) => (
              <li key={failure.key} className="error">
                {t('recipes.importFailed', { title: failure.title, error: failure.reason })}
              </li>
            ))}
          </ul>
          <div className="footer-actions">
            <button onClick={onClose}>{t('recipes.close')}</button>
          </div>
        </div>
      )}
    </Modal>
  )
}

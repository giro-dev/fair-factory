import { useTranslation } from 'react-i18next'
import { useHousehold } from '../context/HouseholdContext'

/** Household picker, only rendered when the caller can actually see more than one household. */
export function HouseholdSwitcher() {
  const { t } = useTranslation()
  const { households, household, selectHousehold, canSwitchHouseholds } = useHousehold()
  if (!canSwitchHouseholds) return null
  return (
    <select
      className="household-switcher"
      aria-label={t('households.switch')}
      value={household?.id ?? ''}
      onChange={(event) => selectHousehold(event.target.value)}
    >
      {households.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
    </select>
  )
}

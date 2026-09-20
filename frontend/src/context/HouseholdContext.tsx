import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

export type Member = {
  id: string
  householdId: string
  name: string
  role: 'ADMIN' | 'ADULT' | 'CHILD'
  color?: string
  username?: string
  guardianIds?: string[]
  hasPin?: boolean
  hasCredentials?: boolean
}
export type Household = { id: string; name: string; locale: string; timezone: string }
export type Me = { kind: 'MEMBER' | 'ADMIN' | 'API'; member?: Member; household?: Household }

type HouseholdContextValue = {
  household?: Household
  member?: Member
  authenticatedMember?: Member
  members: Member[]
  households: Household[]
  canManageHouseholds: boolean
  canSwitchHouseholds: boolean
  selectHousehold: (id: string) => void
  selectMember: (member: Member, pin?: string) => Promise<boolean>
  clearMember: () => void
}

const HouseholdContext = createContext<HouseholdContextValue | null>(null)

const HOUSEHOLD_KEY = 'fanel.householdId'
const MEMBER_KEY = 'fanel.memberId'
const NO_MEMBER = 'none'

export function HouseholdProvider({ children }: { children: ReactNode }) {
  const [householdId, setHouseholdId] = useState(() => localStorage.getItem(HOUSEHOLD_KEY) ?? undefined)
  const [memberId, setMemberId] = useState(() => localStorage.getItem(MEMBER_KEY) ?? undefined)

  const me = useQuery({ queryKey: ['me'], queryFn: () => api<Me>('/me') })
  const isMember = me.data?.kind === 'MEMBER'

  // Members are bound to their own household; only the global admin/API token lists them all.
  const householdsQuery = useQuery({
    queryKey: ['households'],
    queryFn: () => api<Household[]>('/households'),
    enabled: me.data != null && !isMember,
  })
  const households = useMemo(() => isMember
    ? me.data?.household ? [me.data.household] : []
    : householdsQuery.data ?? [], [isMember, me.data, householdsQuery.data])

  const household = useMemo(() => {
    if (isMember) return me.data?.household
    return households.find((h) => h.id === householdId) ?? households[0]
  }, [isMember, me.data, households, householdId])

  const membersQuery = useQuery({
    queryKey: ['members', household?.id],
    queryFn: () => api<Member[]>(`/households/${household!.id}/members`),
    enabled: !!household,
  })
  const members = membersQuery.data ?? []

  // The active profile: an explicit "who am I" pick, or the logged-in member itself.
  const member = memberId === NO_MEMBER
    ? undefined
    : memberId
      ? members.find((m) => m.id === memberId)
      : isMember ? me.data?.member : undefined

  useEffect(() => {
    if (household && household.id !== householdId) {
      setHouseholdId(household.id)
      localStorage.setItem(HOUSEHOLD_KEY, household.id)
    }
  }, [household, householdId])

  const value: HouseholdContextValue = {
    household,
    member,
    authenticatedMember: me.data?.member,
    members,
    households,
    canManageHouseholds: me.data != null && !isMember,
    canSwitchHouseholds: me.data != null && !isMember && households.length > 1,
    selectHousehold: (id) => {
      setHouseholdId(id)
      localStorage.setItem(HOUSEHOLD_KEY, id)
      setMemberId(undefined)
      localStorage.removeItem(MEMBER_KEY)
    },
    selectMember: async (candidate, pin) => {
      if (!household) return false
      const result = await api<{ valid: boolean }>(
        `/households/${household.id}/members/${candidate.id}/verify-pin`,
        { method: 'POST', body: JSON.stringify({ pin: pin ?? '' }) },
      )
      if (!result.valid) return false
      setMemberId(candidate.id)
      localStorage.setItem(MEMBER_KEY, candidate.id)
      return true
    },
    // 'none' suppresses the default-to-self so the profile picker shows on shared devices.
    clearMember: () => {
      setMemberId(NO_MEMBER)
      localStorage.setItem(MEMBER_KEY, NO_MEMBER)
    },
  }

  return <HouseholdContext.Provider value={value}>{children}</HouseholdContext.Provider>
}

export function useHousehold() {
  const ctx = useContext(HouseholdContext)
  if (!ctx) throw new Error('useHousehold must be used within HouseholdProvider')
  return ctx
}

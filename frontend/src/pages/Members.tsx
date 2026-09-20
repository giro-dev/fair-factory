import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { useHousehold, type Member } from '../context/HouseholdContext'

const ROLES = ['ADMIN', 'ADULT', 'CHILD'] as const
const DEFAULT_COLOR = '#2c6e8e'

export function Members() {
  const { t } = useTranslation()
  const { household, members, member } = useHousehold()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [role, setRole] = useState<Member['role']>('CHILD')
  const [pin, setPin] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')
  const [editColor, setEditColor] = useState(DEFAULT_COLOR)
  const [editPin, setEditPin] = useState('')
  const [editUsername, setEditUsername] = useState('')
  const [editPassword, setEditPassword] = useState('')

  // The global bootstrap admin has no linked member; once someone picks "who am I" we defer to their role.
  const isAdmin = !member || member.role === 'ADMIN'
  const canAdd = isAdmin || member?.role === 'ADULT'
  const allowedRoles = isAdmin ? ROLES : (['CHILD'] as const)
  const canManage = (target: Member) =>
    isAdmin || target.id === member?.id || (member?.role === 'ADULT' && (target.guardianIds ?? []).includes(member.id))
  const invalidate = { onSuccess: () => queryClient.invalidateQueries({ queryKey: ['members'] }) }

  const create = useMutation({
    mutationFn: () => api<Member>(`/households/${household!.id}/members`, {
      method: 'POST',
      body: JSON.stringify({
        name,
        role,
        ...(pin.trim() ? { pin: pin.trim() } : {}),
        ...(username.trim() && password ? { username: username.trim(), password } : {}),
      }),
    }),
    onSuccess: async () => {
      setName(''); setPin(''); setUsername(''); setPassword('')
      await queryClient.invalidateQueries({ queryKey: ['members'] })
    },
  })

  const changeRole = useMutation({
    mutationFn: ({ memberId, role }: { memberId: string; role: string }) =>
      api(`/households/${household!.id}/members/${memberId}/role`, { method: 'PUT', body: JSON.stringify({ role }) }),
    ...invalidate,
  })

  const setGuardians = useMutation({
    mutationFn: ({ childId, guardianIds }: { childId: string; guardianIds: string[] }) =>
      api(`/households/${household!.id}/members/${childId}/guardians`, {
        method: 'PUT',
        body: JSON.stringify({ guardianIds }),
      }),
    ...invalidate,
  })

  const update = useMutation({
    mutationFn: async (memberId: string) => {
      await api(`/households/${household!.id}/members/${memberId}`, {
        method: 'PATCH',
        body: JSON.stringify({ name: editName, color: editColor }),
      })
      if (editPin.trim()) {
        await api(`/households/${household!.id}/members/${memberId}/pin`, {
          method: 'PUT',
          body: JSON.stringify({ pin: editPin.trim() }),
        })
      }
      if (editUsername.trim() && editPassword) {
        await api(`/households/${household!.id}/members/${memberId}/credentials`, {
          method: 'PUT',
          body: JSON.stringify({ username: editUsername.trim(), password: editPassword }),
        })
      }
    },
    onSuccess: async () => { setEditingId(null); await queryClient.invalidateQueries({ queryKey: ['members'] }) },
  })

  const remove = useMutation({
    mutationFn: (memberId: string) => api<void>(`/households/${household!.id}/members/${memberId}`, { method: 'DELETE' }),
    ...invalidate,
  })

  const startEdit = (m: Member) => {
    setEditingId(m.id)
    setEditName(m.name)
    setEditColor(m.color && /^#[0-9a-fA-F]{6}$/.test(m.color) ? m.color : DEFAULT_COLOR)
    setEditPin('')
    setEditUsername(m.username ?? '')
    setEditPassword('')
  }

  const potentialGuardians = members.filter((m) => m.role !== 'CHILD')

  return (
    <section className="panel">
      <h2>{t('members.title')}</h2>

      {canAdd && (
        <form className="create-form" onSubmit={(event) => { event.preventDefault(); if (name.trim()) create.mutate() }}>
          <label>
            {t('members.name')}
            <input value={name} onChange={(event) => setName(event.target.value)} required />
          </label>
          <label>
            {t('members.role')}
            <select value={role} onChange={(event) => setRole(event.target.value as Member['role'])}>
              {allowedRoles.map((r) => <option key={r} value={r}>{t(`members.roles.${r}`)}</option>)}
            </select>
          </label>
          <label>
            {t('members.pin')}
            <input inputMode="numeric" value={pin} onChange={(event) => setPin(event.target.value)} />
          </label>
          {role !== 'CHILD' && (
            <>
              <label>
                {t('members.username')}
                <input value={username} onChange={(event) => setUsername(event.target.value)} />
              </label>
              <label>
                {t('members.password')}
                <input type="password" value={password} onChange={(event) => setPassword(event.target.value)}
                       minLength={8} />
              </label>
            </>
          )}
          <button type="submit" disabled={create.isPending}>{t('members.add')}</button>
        </form>
      )}
      {create.isError && <p role="alert">{t('error')}</p>}

      <ul className="member-list">
        {members.map((m) => (
          <li key={m.id}>
            {editingId === m.id ? (
              <form className="create-form" onSubmit={(event) => {
                event.preventDefault()
                update.mutate(m.id)
              }}>
                <label>
                  {t('members.name')}
                  <input value={editName} onChange={(event) => setEditName(event.target.value)} required />
                </label>
                <label>
                  {t('members.color')}
                  <input type="color" value={editColor} onChange={(event) => setEditColor(event.target.value)} />
                </label>
                <label>
                  {t('members.newPin')}
                  <input inputMode="numeric" value={editPin} onChange={(event) => setEditPin(event.target.value)} />
                </label>
                {m.role !== 'CHILD' && (
                  <>
                    <label>
                      {t('members.username')}
                      <input value={editUsername} onChange={(event) => setEditUsername(event.target.value)} />
                    </label>
                    <label>
                      {t('members.newPassword')}
                      <input type="password" value={editPassword} onChange={(event) => setEditPassword(event.target.value)}
                             minLength={8} />
                    </label>
                  </>
                )}
                <button type="submit" disabled={update.isPending}>{t('members.save')}</button>
                <button type="button" className="link" onClick={() => setEditingId(null)}>{t('members.cancel')}</button>
              </form>
            ) : (
              <div className="footer-actions">
                <span className="member-chip" style={{ background: m.color ?? 'var(--tile)' }}>{m.name}</span>
                {m.hasPin && <span className="member-badge">{t('members.hasPin')}</span>}
                {m.hasCredentials && <span className="member-badge">{t('members.hasCredentials')}</span>}
                {isAdmin ? (
                  <select value={m.role} onChange={(event) => changeRole.mutate({ memberId: m.id, role: event.target.value })}>
                    {ROLES.map((r) => <option key={r} value={r}>{t(`members.roles.${r}`)}</option>)}
                  </select>
                ) : (
                  <span>{t(`members.roles.${m.role}`)}</span>
                )}
                {canManage(m) && (
                  <>
                    <button type="button" className="link" onClick={() => startEdit(m)}>{t('members.edit')}</button>
                    <button type="button" className="link" onClick={() => {
                      if (window.confirm(t('members.confirmDelete', { name: m.name }))) remove.mutate(m.id)
                    }}>{t('members.delete')}</button>
                  </>
                )}
              </div>
            )}
            {isAdmin && m.role === 'CHILD' && (
              <fieldset>
                <legend>{t('members.guardians')}</legend>
                {potentialGuardians.length === 0 && <p>{t('members.noGuardianCandidates')}</p>}
                {potentialGuardians.map((guardian) => {
                  const checked = m.guardianIds?.includes(guardian.id) ?? false
                  return (
                    <label className="inline" key={guardian.id}>
                      <input type="checkbox" checked={checked} onChange={(event) => {
                        const next = event.target.checked
                          ? [...(m.guardianIds ?? []), guardian.id]
                          : (m.guardianIds ?? []).filter((id) => id !== guardian.id)
                        setGuardians.mutate({ childId: m.id, guardianIds: next })
                      }} />
                      {guardian.name}
                    </label>
                  )
                })}
              </fieldset>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

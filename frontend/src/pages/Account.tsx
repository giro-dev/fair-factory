import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useHousehold } from '../context/HouseholdContext'
import { subscribeToNotifications, unsubscribeFromNotifications } from '../notifications/notifications'

export function Account() {
  const { t } = useTranslation()
  const { username, logout } = useAuth()
  const { member } = useHousehold()
  const queryClient = useQueryClient()
  const [newUsername, setNewUsername] = useState('')
  const [password, setPassword] = useState('')
  const [saved, setSaved] = useState(false)
  const [pushEnabled, setPushEnabled] = useState(false)

  const vapidQuery = useQuery({
    queryKey: ['vapidPublicKey'],
    queryFn: () => api<{ publicKey: string }>('/notifications/vapid-public-key'),
  })

  useEffect(() => {
    if (!('serviceWorker' in navigator) || !('PushManager' in window) || !member) return
    void navigator.serviceWorker.ready.then((reg) => reg.pushManager.getSubscription()).then((sub) => setPushEnabled(!!sub))
  }, [member])

  const togglePush = useMutation({
    mutationFn: async () => {
      if (!member || !vapidQuery.data) return
      const current = await navigator.serviceWorker.ready.then((r) => r.pushManager.getSubscription())
      if (pushEnabled) {
        await unsubscribeFromNotifications(member.householdId, current?.endpoint ?? '')
        await current?.unsubscribe()
        setPushEnabled(false)
      } else {
        await subscribeToNotifications(member.householdId, vapidQuery.data.publicKey)
        setPushEnabled(true)
      }
    },
  })

  const mutation = useMutation({
    mutationFn: () => api(`/households/${member!.householdId}/members/${member!.id}/credentials`, {
      method: 'PUT',
      body: JSON.stringify({ username: newUsername, password }),
    }),
    onSuccess: async () => {
      setSaved(true)
      setPassword('')
      await queryClient.invalidateQueries({ queryKey: ['members'] })
    },
  })

  return (
    <section className="panel">
      <h2>{t('account.title')}</h2>
      <p>{t('account.loggedInAs', { username })}</p>
      <button type="button" className="link" onClick={logout}>{t('account.logout')}</button>

      {'serviceWorker' in navigator && 'PushManager' in window && member && member.role !== 'CHILD' && (
        <div>
          <button
            type="button"
            onClick={() => togglePush.mutate()}
            disabled={vapidQuery.isPending || togglePush.isPending}
          >
            {pushEnabled ? t('account.disableNotifications') : t('account.enableNotifications')}
          </button>
          {togglePush.isError && <p role="alert">{t('error')}</p>}
        </div>
      )}

      {member && member.role !== 'CHILD' ? (
        <form className="create-form" onSubmit={(event) => { event.preventDefault(); setSaved(false); mutation.mutate() }}>
          <p>{t('account.setCredentialsFor', { name: member.name })}</p>
          <label>
            {t('account.username')}
            <input value={newUsername} onChange={(event) => setNewUsername(event.target.value)} required />
          </label>
          <label>
            {t('account.password')}
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)}
                   required minLength={8} />
          </label>
          <button type="submit" disabled={mutation.isPending}>{t('account.save')}</button>
          {mutation.isError && <p role="alert">{t('error')}</p>}
          {saved && <p>{t('account.saved')}</p>}
        </form>
      ) : (
        <p>{t('account.selectAdult')}</p>
      )}
    </section>
  )
}

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import listPlugin from '@fullcalendar/list'
import interactionPlugin from '@fullcalendar/interaction'
import type { DateClickArg } from '@fullcalendar/interaction'
import type { EventClickArg, EventContentArg } from '@fullcalendar/core'
import caLocale from '@fullcalendar/core/locales/ca'
import esLocale from '@fullcalendar/core/locales/es'
import { api } from '../api/client'
import { useHousehold } from '../context/HouseholdContext'
import { Modal } from '../components/Modal'

type RecurrenceFreq = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'

type CalEvent = {
  id: string
  title: string
  date: string
  anchorDate: string
  time?: string
  durationMinutes?: number | null
  addedBy?: string
  assigneeIds: string[]
  recurrenceFreq?: RecurrenceFreq | null
  recurrenceInterval?: number | null
  recurrenceUntil?: string | null
}

type EventForm = {
  editingId: string | null
  date: string
  time: string
  duration: string
  title: string
  assigneeIds: string[]
  recurrenceFreq: RecurrenceFreq | ''
  recurrenceInterval: number
  recurrenceUntil: string
}

const LOCALES = { ca: caLocale, es: esLocale }
const REPEAT_OPTIONS: RecurrenceFreq[] = ['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY']

const todayIso = () => new Date().toLocaleDateString('sv-SE') // yyyy-mm-dd in local time

const endIso = (date: string, time: string, minutes: number) => {
  const d = new Date(`${date}T${time}`)
  d.setMinutes(d.getMinutes() + minutes)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

const timeLabel = (e: CalEvent) =>
  e.time && e.durationMinutes
    ? `${e.time.slice(0, 5)}–${endIso(e.date, e.time, e.durationMinutes).slice(11)}`
    : e.time?.slice(0, 5) ?? ''

export function Calendar() {
  const { t, i18n } = useTranslation()
  const { household, member, members } = useHousehold()
  const queryClient = useQueryClient()

  const [daySheet, setDaySheet] = useState<string | null>(null)
  const [form, setForm] = useState<EventForm | null>(null)

  const events = useQuery({
    queryKey: ['calendar', household?.id],
    queryFn: () => api<CalEvent[]>(`/households/${household!.id}/calendar`),
    enabled: !!household,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['calendar'] })

  const toBody = (data: EventForm) => ({
    title: data.title,
    date: data.date,
    time: data.time || null,
    durationMinutes: data.time && Number(data.duration) > 0 ? Number(data.duration) : null,
    assigneeIds: data.assigneeIds,
    recurrenceFreq: data.recurrenceFreq || null,
    recurrenceInterval: data.recurrenceFreq ? data.recurrenceInterval : null,
    recurrenceUntil: data.recurrenceFreq && data.recurrenceUntil ? data.recurrenceUntil : null,
  })

  const create = useMutation({
    mutationFn: (data: EventForm) => api<CalEvent>(`/households/${household!.id}/calendar`, {
      method: 'POST',
      body: JSON.stringify({ ...toBody(data), addedBy: member?.id ?? null }),
    }),
    onSuccess: async () => { setForm(null); await invalidate() },
  })

  const update = useMutation({
    mutationFn: (data: EventForm) => api<CalEvent>(`/households/${household!.id}/calendar/${data.editingId}`, {
      method: 'PATCH',
      body: JSON.stringify(toBody(data)),
    }),
    onSuccess: async () => { setForm(null); await invalidate() },
  })

  const remove = useMutation({
    mutationFn: (id: string) => api<void>(`/households/${household!.id}/calendar/${id}`, { method: 'DELETE' }),
    onSuccess: async () => { setForm(null); await invalidate() },
  })

  const memberName = (id?: string) => members.find((m) => m.id === id)?.name
  const colorsFor = (ids: string[]) =>
    ids.map((id) => members.find((m) => m.id === id)?.color).filter((c): c is string => !!c)

  const eventsForDay = (date: string) =>
    (events.data ?? []).filter((e) => e.date === date).sort((a, b) => (a.time ?? '').localeCompare(b.time ?? ''))

  const openCreate = (date: string) =>
    setForm({
      editingId: null, date, time: '', duration: '', title: '', assigneeIds: [],
      recurrenceFreq: '', recurrenceInterval: 1, recurrenceUntil: '',
    })
  const openEdit = (event: CalEvent) =>
    setForm({
      editingId: event.id, date: event.anchorDate, time: event.time ?? '',
      duration: event.durationMinutes?.toString() ?? '', title: event.title,
      assigneeIds: event.assigneeIds,
      recurrenceFreq: event.recurrenceFreq ?? '',
      recurrenceInterval: event.recurrenceInterval ?? 1,
      recurrenceUntil: event.recurrenceUntil ?? '',
    })

  const toggleAssignee = (id: string) => {
    setForm((current) => current && ({
      ...current,
      assigneeIds: current.assigneeIds.includes(id)
        ? current.assigneeIds.filter((a) => a !== id)
        : [...current.assigneeIds, id],
    }))
  }

  const calendarEvents = (events.data ?? []).map((e) => ({
    id: `${e.id}::${e.date}`,
    title: e.addedBy ? `${e.title} (${memberName(e.addedBy)})` : e.title,
    start: e.time ? `${e.date}T${e.time}` : e.date,
    end: e.time && e.durationMinutes ? endIso(e.date, e.time, e.durationMinutes) : undefined,
    allDay: !e.time,
    extendedProps: { colors: colorsFor(e.assigneeIds), seriesId: e.id, date: e.date },
  }))

  const onDateClick = (arg: DateClickArg) => setDaySheet(arg.dateStr)
  const onEventClick = (arg: EventClickArg) => {
    const seriesId = arg.event.extendedProps.seriesId as string
    const date = arg.event.extendedProps.date as string
    const found = (events.data ?? []).find((e) => e.id === seriesId && e.date === date)
    if (found) openEdit(found)
  }

  const renderEventContent = (arg: EventContentArg) => {
    const colors = (arg.event.extendedProps.colors as string[]) ?? []
    return (
      <div className="event-content">
        {colors.length > 0 && (
          <span className="event-dots">
            {colors.map((c, i) => <i key={i} style={{ background: c }} />)}
          </span>
        )}
        <span className="fc-event-title">{arg.event.title}</span>
      </div>
    )
  }

  const dayTitle = (date: string) =>
    new Intl.DateTimeFormat(i18n.language, { weekday: 'long', day: 'numeric', month: 'long' })
      .format(new Date(`${date}T00:00:00`))

  return (
    <section className="panel">
      <h2>{t('calendar.title')}</h2>
      {events.isPending && <p>{t('loading')}</p>}
      {events.isError && <p role="alert">{t('error')}</p>}
      <div className="calendar-view">
        <FullCalendar
          plugins={[dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin]}
          initialView="dayGridMonth"
          headerToolbar={{ left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek,listWeek' }}
          locale={LOCALES[i18n.language as keyof typeof LOCALES] ?? LOCALES.ca}
          height="auto"
          events={calendarEvents}
          eventContent={renderEventContent}
          dateClick={onDateClick}
          eventClick={onEventClick}
        />
      </div>

      <button type="button" className="fab" aria-label={t('calendar.newEvent')}
              onClick={() => openCreate(daySheet ?? todayIso())}>
        +
      </button>

      {daySheet && !form && (
        <Modal title={dayTitle(daySheet)} onClose={() => setDaySheet(null)}>
          <ul className="item-list">
            {eventsForDay(daySheet).map((e) => (
              <li key={e.id}>
                <button type="button" className="link day-event" onClick={() => openEdit(e)}>
                  {e.time && <span className="meta">{timeLabel(e)}</span>}
                  <span>{e.title}</span>
                  {e.recurrenceFreq && <span className="meta" title={t('calendar.recurring')}>↻</span>}
                  {colorsFor(e.assigneeIds).length > 0 && (
                    <span className="event-dots">
                      {colorsFor(e.assigneeIds).map((c, i) => <i key={i} style={{ background: c }} />)}
                    </span>
                  )}
                </button>
              </li>
            ))}
            {eventsForDay(daySheet).length === 0 && <li className="meta">{t('calendar.noEvents')}</li>}
          </ul>
          <button type="button" onClick={() => openCreate(daySheet)}>{t('calendar.addEvent')}</button>
        </Modal>
      )}

      {form && (
        <Modal title={form.editingId ? t('calendar.editEvent') : t('calendar.newEvent')}
               onClose={() => setForm(null)}>
          <form className="create-form modal-form" onSubmit={(e) => {
            e.preventDefault()
            if (!form.title || !form.date) return
            if (form.editingId) update.mutate(form); else create.mutate(form)
          }}>
            <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })}
                   placeholder={t('calendar.titlePlaceholder')} required autoFocus />
            <input type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} required />
            <input type="time" value={form.time} onChange={(e) => setForm({ ...form, time: e.target.value })} />
            {form.time && (
              <label className="inline">
                {t('calendar.duration')}
                <input type="number" min={1} step={5} value={form.duration}
                       onChange={(e) => setForm({ ...form, duration: e.target.value })} />
                {t('calendar.minutes')}
              </label>
            )}
            <fieldset>
              <legend>{t('calendar.assignees')}</legend>
              {members.map((m) => (
                <label className="inline" key={m.id}>
                  <input type="checkbox" checked={form.assigneeIds.includes(m.id)} onChange={() => toggleAssignee(m.id)} />
                  <em className="member-chip small" style={{ background: m.color ?? 'var(--tile)' }}>{m.name}</em>
                </label>
              ))}
            </fieldset>
            <fieldset>
              <legend>{t('calendar.repeat')}</legend>
              <label>
                {t('calendar.repeat')}
                <select value={form.recurrenceFreq}
                        onChange={(e) => setForm({ ...form, recurrenceFreq: e.target.value as RecurrenceFreq | '' })}>
                  <option value="">{t('calendar.repeatOptions.none')}</option>
                  {REPEAT_OPTIONS.map((freq) => (
                    <option key={freq} value={freq}>{t(`calendar.repeatOptions.${freq}`)}</option>
                  ))}
                </select>
              </label>
              {form.recurrenceFreq && (
                <>
                  <label className="inline">
                    {t('calendar.every')}
                    <input type="number" min={1} value={form.recurrenceInterval}
                           onChange={(e) => setForm({ ...form, recurrenceInterval: Math.max(1, Number(e.target.value)) })} />
                    {t(`calendar.repeatUnit.${form.recurrenceFreq}`)}
                  </label>
                  <label>
                    {t('calendar.until')}
                    <input type="date" value={form.recurrenceUntil}
                           onChange={(e) => setForm({ ...form, recurrenceUntil: e.target.value })} />
                  </label>
                  {form.editingId && <p className="meta">{t('calendar.recurringNote')}</p>}
                </>
              )}
            </fieldset>
            <div className="footer-actions">
              <button type="submit" disabled={create.isPending || update.isPending}>{t('calendar.save')}</button>
              {form.editingId && (
                <button type="button" className="link" onClick={() => {
                  const confirmKey = form.recurrenceFreq ? 'calendar.confirmDeleteSeries' : 'calendar.confirmDelete'
                  if (window.confirm(t(confirmKey, { title: form.title }))) remove.mutate(form.editingId!)
                }}>
                  {t('calendar.delete')}
                </button>
              )}
            </div>
          </form>
        </Modal>
      )}
    </section>
  )
}

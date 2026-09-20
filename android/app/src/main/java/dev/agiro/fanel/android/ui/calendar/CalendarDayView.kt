package dev.agiro.fanel.android.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.agiro.fanel.android.R
import dev.agiro.fanel.android.data.local.CalendarEventEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun CalendarDayView(
    date: LocalDate,
    events: List<CalendarEventEntity>,
    onEventClick: (CalendarEventEntity) -> Unit,
    onEventLongClick: (CalendarEventEntity) -> Unit
) {
    val dayEvents = remember(date, events) {
        events.filter { it.date == date.toString() }
    }
    val allDay = remember(dayEvents) { dayEvents.filter { it.time == null } }
    val timed = remember(dayEvents) { dayEvents.filter { it.time != null } }

    Column(modifier = Modifier.fillMaxSize()) {
        if (allDay.isNotEmpty()) {
            Text(
                text = stringResource(R.string.all_day),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            allDay.forEach { event -> DayEventCard(event, onEventClick, onEventLongClick) }
        }
        if (allDay.isEmpty() && timed.isEmpty()) {
            Text(
                text = stringResource(R.string.no_events),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(24) { hour ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "%02d:00".format(hour),
                        modifier = Modifier
                            .width(48.dp)
                            .padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        timed.filter { it.time?.startsWith("%02d:".format(hour)) == true }
                            .forEach { event -> DayEventCard(event, onEventClick, onEventLongClick) }
                    }
                }
            }
        }
    }
}

private val HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayEventCard(
    event: CalendarEventEntity,
    onClick: (CalendarEventEntity) -> Unit,
    onLongClick: (CalendarEventEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .combinedClickable(onClick = { onClick(event) }, onLongClick = { onLongClick(event) }),
        colors = CardDefaults.cardColors(
            containerColor = if (event.pendingStatus != null) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            Column {
                Text(event.title, style = MaterialTheme.typography.bodyMedium)
                event.time?.let {
                    val start = runCatching { LocalTime.parse(it) }.getOrNull()
                    val end = event.durationMinutes?.let { d -> start?.plusMinutes(d.toLong()) }
                    Text(
                        text = when {
                            start == null -> it
                            end != null -> "${HOUR_MINUTE.format(start)}–${HOUR_MINUTE.format(end)}"
                            else -> HOUR_MINUTE.format(start)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

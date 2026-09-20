package dev.agiro.fanel.android.ui.calendar

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import dev.agiro.fanel.android.R
import dev.agiro.fanel.android.data.CalendarDraft
import dev.agiro.fanel.android.data.local.CalendarEventEntity
import dev.agiro.fanel.android.data.remote.MemberDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val RECURRENCE_OPTIONS = listOf<String?>(null, "DAILY", "WEEKLY", "MONTHLY", "YEARLY")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun EventEditorDialog(
    defaultDate: LocalDate,
    initial: CalendarEventEntity?,
    members: List<MemberDto>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (CalendarDraft) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember {
        mutableStateOf(
            initial?.let { runCatching { LocalDate.parse(it.anchorDate ?: it.date) }.getOrNull() }
                ?: defaultDate
        )
    }
    var time by remember {
        mutableStateOf(initial?.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() })
    }
    var durationText by remember { mutableStateOf(initial?.durationMinutes?.toString() ?: "") }
    var selectedMembers by remember {
        mutableStateOf(initial?.assigneeIds?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet())
    }
    var recurrenceFreq by remember { mutableStateOf(initial?.recurrenceFreq) }
    var intervalText by remember { mutableStateOf(initial?.recurrenceInterval?.toString() ?: "1") }
    var until by remember {
        mutableStateOf(initial?.recurrenceUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showUntilPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val dialogView = LocalView.current

    LaunchedEffect(Unit) {
        (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
            window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.create_event else R.string.edit_event))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.event_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.event_date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(dateFormatter.format(date))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.event_time_optional),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(time?.toString() ?: stringResource(R.string.all_day))
                    }
                    if (time != null) {
                        IconButton(onClick = { time = null }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.event_clear_time)
                            )
                        }
                    }
                }

                if (time != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter(Char::isDigit); error = false },
                        label = { Text(stringResource(R.string.event_duration_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (members.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.event_members),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        members.forEach { member ->
                            FilterChip(
                                selected = member.id in selectedMembers,
                                onClick = {
                                    selectedMembers = if (member.id in selectedMembers) {
                                        selectedMembers - member.id
                                    } else {
                                        selectedMembers + member.id
                                    }
                                },
                                label = { Text(member.name) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.event_recurrence),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    var freqExpanded by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { freqExpanded = true }) {
                        Text(stringResource(recurrenceLabelRes(recurrenceFreq)))
                    }
                    DropdownMenu(
                        expanded = freqExpanded,
                        onDismissRequest = { freqExpanded = false }
                    ) {
                        RECURRENCE_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(recurrenceLabelRes(option))) },
                                onClick = {
                                    recurrenceFreq = option
                                    freqExpanded = false
                                }
                            )
                        }
                    }
                }

                if (recurrenceFreq != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter(Char::isDigit); error = false },
                        label = { Text(stringResource(R.string.event_recurrence_interval)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.event_recurrence_until),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { showUntilPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(until?.let { dateFormatter.format(it) } ?: "—")
                        }
                        if (until != null) {
                            IconButton(onClick = { until = null }) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.event_clear_until)
                                )
                            }
                        }
                    }
                }

                if (error) {
                    Text(
                        text = stringResource(R.string.event_invalid_input),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val interval = intervalText.toIntOrNull()
                    val duration = durationText.toIntOrNull()
                    val invalid = title.isBlank() ||
                        (time != null && durationText.isNotBlank() && (duration == null || duration < 1)) ||
                        (recurrenceFreq != null && (interval == null || interval < 1))
                    if (invalid) {
                        error = true
                    } else {
                        onConfirm(
                            CalendarDraft(
                                title = title.trim(),
                                date = date.toString(),
                                time = time?.toString(),
                                durationMinutes = if (time == null) null else duration,
                                addedBy = initial?.addedBy,
                                assigneeIds = selectedMembers.toList(),
                                recurrenceFreq = recurrenceFreq,
                                recurrenceInterval = if (recurrenceFreq == null) null else interval,
                                recurrenceUntil = if (recurrenceFreq == null) null else until?.toString()
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialogContent(
            initial = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { date = it; showDatePicker = false }
        )
    }
    if (showUntilPicker) {
        DatePickerDialogContent(
            initial = until ?: date,
            onDismiss = { showUntilPicker = false },
            onConfirm = { until = it; showUntilPicker = false }
        )
    }
    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = time?.hour ?: 12,
            initialMinute = time?.minute ?: 0,
            is24Hour = true
        )
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.event_pick_time)) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            TimePicker(state = timeState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogContent(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        DatePicker(state = state)
    }
}

private fun recurrenceLabelRes(freq: String?): Int = when (freq) {
    "DAILY" -> R.string.recurrence_daily
    "WEEKLY" -> R.string.recurrence_weekly
    "MONTHLY" -> R.string.recurrence_monthly
    "YEARLY" -> R.string.recurrence_yearly
    else -> R.string.recurrence_none
}

package dev.agiro.fanel.android.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.agiro.fanel.android.MenuViewModel
import dev.agiro.fanel.android.R
import dev.agiro.fanel.android.data.remote.MealSlotDto
import dev.agiro.fanel.android.data.remote.MealType
import dev.agiro.fanel.android.ui.components.OfflineBanner
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private data class SlotTarget(val dayOfWeek: Int, val mealType: MealType)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(viewModel: MenuViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SlotTarget?>(null) }
    var viewing by remember { mutableStateOf<SlotTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_screen_title)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.sync_now))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OfflineBanner()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.goToPreviousWeek() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.previous_period)
                    )
                }
                Text(state.weekLabel, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.goToToday() }) {
                        Text(stringResource(R.string.today))
                    }
                    IconButton(onClick = { viewModel.goToNextWeek() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.next_period)
                        )
                    }
                }
            }

            if (state.householdId.isBlank()) {
                Text(
                    stringResource(R.string.household_unconfigured_status),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }

            state.errorRes?.let { errorRes ->
                Text(
                    stringResource(errorRes),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(7) { index ->
                    val day = index + 1
                    DayCard(
                        dayOfWeek = day,
                        slotFor = { meal -> state.slotFor(day, meal) },
                        recipeName = { state.recipeName(it) },
                        onSlotClick = { meal ->
                            val slot = state.slotFor(day, meal)
                            if (slot?.recipeId != null || !slot?.text.isNullOrBlank()) {
                                viewing = SlotTarget(day, meal)
                            } else {
                                editing = SlotTarget(day, meal)
                            }
                        }
                    )
                }
            }
        }
    }

    viewing?.let { target ->
        val slot = state.slotFor(target.dayOfWeek, target.mealType)
        MealSlotDetailDialog(
            dayLabel = dayName(target.dayOfWeek),
            mealType = target.mealType,
            slot = slot,
            recipe = state.recipes.firstOrNull { it.id == slot?.recipeId },
            onDismiss = { viewing = null },
            onEdit = {
                editing = target
                viewing = null
            }
        )
    }

    editing?.let { target ->
        val slot = state.slotFor(target.dayOfWeek, target.mealType)
        MealSlotDialog(
            dayLabel = dayName(target.dayOfWeek),
            mealType = target.mealType,
            slot = slot,
            recipes = state.recipes,
            onDismiss = { editing = null },
            onSave = { text, recipeId ->
                viewModel.setSlot(target.dayOfWeek, target.mealType, text, recipeId)
                editing = null
            },
            onClear = {
                viewModel.clearSlot(target.dayOfWeek, target.mealType)
                editing = null
            }
        )
    }
}

@Composable
private fun DayCard(
    dayOfWeek: Int,
    slotFor: (MealType) -> MealSlotDto?,
    recipeName: (String?) -> String?,
    onSlotClick: (MealType) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(dayName(dayOfWeek), style = MaterialTheme.typography.titleMedium)
            MealType.entries.forEach { mealType ->
                val slot = slotFor(mealType)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSlotClick(mealType) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(mealLabelRes(mealType)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    val content = recipeName(slot?.recipeId) ?: slot?.text
                    Text(
                        content ?: "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (content == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun dayName(dayOfWeek: Int): String =
    DayOfWeek.of(dayOfWeek)
        .getDisplayName(TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { it.uppercase(Locale.getDefault()) }

fun mealLabelRes(mealType: MealType): Int = when (mealType) {
    MealType.BREAKFAST -> R.string.meal_breakfast
    MealType.LUNCH -> R.string.meal_lunch
    MealType.SNACK -> R.string.meal_snack
    MealType.DINNER -> R.string.meal_dinner
}

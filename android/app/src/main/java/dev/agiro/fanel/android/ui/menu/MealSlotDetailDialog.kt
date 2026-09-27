package dev.agiro.fanel.android.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agiro.fanel.android.R
import dev.agiro.fanel.android.data.remote.MealSlotDto
import dev.agiro.fanel.android.data.remote.MealType
import dev.agiro.fanel.android.data.remote.RecipeDto
import dev.agiro.fanel.android.ui.recipes.RecipeDetailContent

/**
 * Read-only view of a filled meal slot: linked recipe details and/or the slot note.
 * Closing it never saves nor modifies the slot; [onEdit] opens the editor instead.
 */
@Composable
fun MealSlotDetailDialog(
    dayLabel: String,
    mealType: MealType,
    slot: MealSlotDto?,
    recipe: RecipeDto?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$dayLabel · ${stringResource(mealLabelRes(mealType))}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                recipe?.let {
                    Text(it.name, style = MaterialTheme.typography.titleMedium)
                    RecipeDetailContent(it)
                }
                slot?.text?.takeIf { it.isNotBlank() }?.let { note ->
                    if (recipe != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.recipe_notes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recipe_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onEdit) {
                Text(stringResource(R.string.edit))
            }
        }
    )
}

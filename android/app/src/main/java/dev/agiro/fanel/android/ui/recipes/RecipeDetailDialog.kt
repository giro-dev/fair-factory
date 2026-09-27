package dev.agiro.fanel.android.ui.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agiro.fanel.android.R
import dev.agiro.fanel.android.data.remote.IngredientDto
import dev.agiro.fanel.android.data.remote.RecipeDto
import java.util.Locale
import kotlin.math.floor

@Composable
fun RecipeDetailDialog(recipe: RecipeDto, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recipe.name) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RecipeDetailContent(recipe)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recipe_close))
            }
        }
    )
}

/**
 * Recipe details body (image, servings, description, notes, steps, ingredients).
 * Does not scroll itself; callers must wrap it in a scrollable container.
 */
@Composable
fun RecipeDetailContent(recipe: RecipeDto) {
    if (recipe.imageMimeType != null && recipe.imageData != null) {
        RecipeImage(
            recipe = recipe,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    Text(
        stringResource(R.string.recipe_servings, recipe.servings),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    recipe.description?.takeIf { it.isNotBlank() }?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
    recipe.notes?.takeIf { it.isNotBlank() }?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.recipe_notes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
    val steps = recipe.steps.orEmpty().filter { it.isNotBlank() }
    if (steps.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.recipe_steps),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        steps.forEachIndexed { index, step ->
            Text(
                "${index + 1}. $step",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
    val ingredients = recipe.ingredients.orEmpty()
    if (ingredients.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.recipe_ingredients),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ingredients.forEach { ingredient ->
            Text(
                formatIngredient(ingredient),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun formatIngredient(ingredient: IngredientDto): String {
    val amount = listOfNotNull(
        ingredient.quantity?.let { formatQuantity(it) },
        ingredient.unit?.takeIf { it.isNotBlank() }
    ).joinToString(" ")
    return if (amount.isBlank()) ingredient.name else "$amount ${ingredient.name}"
}

private fun formatQuantity(quantity: Double): String =
    if (quantity == floor(quantity)) {
        String.format(Locale.getDefault(), "%d", quantity.toLong())
    } else {
        String.format(Locale.getDefault(), "%.2f", quantity).trimEnd('0').trimEnd('.', ',')
    }

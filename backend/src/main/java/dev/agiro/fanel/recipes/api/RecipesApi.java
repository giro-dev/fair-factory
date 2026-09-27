package dev.agiro.fanel.recipes.api;

import dev.agiro.criteriafilter.model.FilterRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface RecipesApi {
    List<RecipeDto> list(UUID householdId);
    RecipePageDto search(UUID householdId, FilterRequest filter, String tag, String ingredient,
                         Pageable pageable);
    RecipeDto get(UUID householdId, UUID recipeId);
    RecipeDto create(UUID householdId, String name, int servings, String notes, String description,
                     List<String> steps, List<String> tags, List<IngredientDto> ingredients,
                     String imageMimeType, String imageData);
    RecipeDto update(UUID householdId, UUID recipeId, String name, int servings, String notes,
                     String description, List<String> steps, List<String> tags,
                     List<IngredientDto> ingredients, String imageMimeType, String imageData);
    void delete(UUID householdId, UUID recipeId);
}

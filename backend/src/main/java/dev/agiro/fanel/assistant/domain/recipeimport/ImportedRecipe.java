package dev.agiro.fanel.assistant.domain.recipeimport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Structured recipe payload produced either deterministically (e.g. schema.org JSON-LD)
 * or by the {@code recipe-extractor} agent. Every field but {@code name} is optional.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportedRecipe(String name, Integer servings, String description, String notes,
                             List<String> steps, List<String> tags,
                             List<IngredientPayload> ingredients,
                             String imageMimeType, String imageData) {
}

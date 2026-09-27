package dev.agiro.fanel.assistant.domain.recipeimport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IngredientPayload(String name, Double quantity, String unit, String category) {
}

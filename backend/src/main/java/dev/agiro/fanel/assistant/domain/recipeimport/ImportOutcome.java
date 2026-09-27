package dev.agiro.fanel.assistant.domain.recipeimport;

import dev.agiro.fanel.recipes.api.RecipeDto;

import java.util.List;

public record ImportOutcome(List<RecipeDto> imported, List<ImportFailure> failures) {

    public record ImportFailure(String key, String title, String reason) {
    }
}

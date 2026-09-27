package dev.agiro.fanel.recipes.api;

import java.util.List;

public record RecipePageDto(List<RecipeDto> content, long totalHits, boolean hasMore) {
}

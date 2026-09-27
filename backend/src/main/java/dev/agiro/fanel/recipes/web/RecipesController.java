package dev.agiro.fanel.recipes.web;

import dev.agiro.criteriafilter.model.FilterRequest;
import dev.agiro.fanel.recipes.api.IngredientDto;
import dev.agiro.fanel.recipes.api.RecipeDto;
import dev.agiro.fanel.recipes.api.RecipePageDto;
import dev.agiro.fanel.recipes.api.RecipesApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/households/{householdId}/recipes")
public class RecipesController {
    private static final Logger log = LoggerFactory.getLogger(RecipesController.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final RecipesApi recipes;

    public RecipesController(RecipesApi recipes) {
        this.recipes = recipes;
    }

    @GetMapping
    public List<RecipeDto> list(@PathVariable UUID householdId) {
        return recipes.list(householdId);
    }

    @PostMapping("/search")
    public RecipePageDto search(@PathVariable UUID householdId,
                                @RequestBody(required = false) FilterRequest request,
                                @RequestParam(required = false) String tag,
                                @RequestParam(required = false) String ingredient,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "24") int size,
                                @RequestParam(defaultValue = "name") String sort,
                                @RequestParam(defaultValue = "asc") String direction) {
        return recipes.search(householdId, request, tag, ingredient,
                pageable(page, size, sort, direction));
    }

    @GetMapping("/{recipeId}")
    public RecipeDto get(@PathVariable UUID householdId, @PathVariable UUID recipeId) {
        return recipes.get(householdId, recipeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeDto create(@PathVariable UUID householdId, @Valid @RequestBody CreateRecipe request) {
        log.debug("POST /recipes for household {}: name={}, servings={}, ingredients={}, steps={}, hasImage={}",
                householdId, request.name(), request.servings(),
                request.ingredients() == null ? 0 : request.ingredients().size(),
                request.steps() == null ? 0 : request.steps().size(),
                request.imageData() != null && !request.imageData().isBlank());
        return recipes.create(householdId, request.name(), request.servings(), request.notes(),
                request.description(), request.steps(), request.tags(), request.ingredients(),
                request.imageMimeType(), request.imageData());
    }

    @PutMapping("/{recipeId}")
    public RecipeDto update(@PathVariable UUID householdId, @PathVariable UUID recipeId,
                            @Valid @RequestBody CreateRecipe request) {
        return recipes.update(householdId, recipeId, request.name(), request.servings(), request.notes(),
                request.description(), request.steps(), request.tags(), request.ingredients(),
                request.imageMimeType(), request.imageData());
    }

    @DeleteMapping("/{recipeId}")
    public void delete(@PathVariable UUID householdId, @PathVariable UUID recipeId) {
        recipes.delete(householdId, recipeId);
    }

    private static Pageable pageable(int page, int size, String sort, String direction) {
        String property = switch (sort) {
            case "createdAt", "servings", "name" -> sort;
            default -> "name";
        };
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(dir, property));
    }

    public record CreateRecipe(@NotBlank String name, int servings, String notes, String description,
                               List<String> steps, List<String> tags, List<IngredientDto> ingredients,
                               String imageMimeType, String imageData) {
    }
}

package dev.agiro.fanel.recipes.domain;

import dev.agiro.criteriafilter.exception.FilterException;
import dev.agiro.criteriafilter.metamodel.EntityFilterMetadata;
import dev.agiro.criteriafilter.metamodel.FilterMetadataRegistry;
import dev.agiro.criteriafilter.model.FilterNode;
import dev.agiro.criteriafilter.model.FilterRequest;
import dev.agiro.criteriafilter.repository.jpa.JpaSpecificationTranslator;
import dev.agiro.criteriafilter.validation.FilterValidator;
import dev.agiro.fanel.recipes.api.IngredientDto;
import dev.agiro.fanel.recipes.api.RecipeDto;
import dev.agiro.fanel.recipes.api.RecipePageDto;
import dev.agiro.fanel.recipes.api.RecipesApi;
import dev.agiro.fanel.recipes.infra.RecipeRepository;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class RecipesService implements RecipesApi {
    private static final Logger log = LoggerFactory.getLogger(RecipesService.class);

    private final RecipeRepository recipes;
    private final FilterValidator filterValidator;
    private final FilterMetadataRegistry metadataRegistry;
    private final JpaSpecificationTranslator translator;

    public RecipesService(RecipeRepository recipes, FilterValidator filterValidator,
                          FilterMetadataRegistry metadataRegistry,
                          JpaSpecificationTranslator translator) {
        this.recipes = recipes;
        this.filterValidator = filterValidator;
        this.metadataRegistry = metadataRegistry;
        this.translator = translator;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeDto> list(UUID householdId) {
        return recipes.findAllByHouseholdIdOrderByName(householdId).stream().map(RecipesService::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RecipePageDto search(UUID householdId, FilterRequest request, String tag, String ingredient,
                                Pageable pageable) {
        EntityFilterMetadata metadata = metadataRegistry.require(Recipe.class);
        FilterNode node = request != null ? request.filter() : null;
        try {
            if (node != null) {
                filterValidator.validate(request, Recipe.class);
            }
            Specification<Recipe> spec = node != null
                    ? translator.<Recipe>toSpecification(node, metadata)
                    : Specification.unrestricted();
            spec = spec.and(inHousehold(householdId));
            if (tag != null && !tag.isBlank()) {
                spec = spec.and(withTag(tag.trim()));
            }
            if (ingredient != null && !ingredient.isBlank()) {
                spec = spec.and(withIngredient(ingredient.trim()));
            }
            Specification<Recipe> effective = spec;
            Page<Recipe> result = recipes.findAll(effective, pageable);
            return new RecipePageDto(result.getContent().stream().map(RecipesService::toDto).toList(),
                    result.getTotalElements(), result.hasNext());
        } catch (FilterException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static Specification<Recipe> inHousehold(UUID householdId) {
        return (root, query, cb) -> cb.equal(root.get("householdId"), householdId);
    }

    private static Specification<Recipe> withTag(String tag) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.equal(root.join("tags"), tag);
        };
    }

    private static Specification<Recipe> withIngredient(String ingredient) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.like(cb.lower(root.join("ingredients").get("name")),
                    "%" + ingredient.toLowerCase(Locale.ROOT) + "%");
        };
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeDto get(UUID householdId, UUID recipeId) {
        return toDto(find(householdId, recipeId));
    }

    @Override
    public RecipeDto create(UUID householdId, String name, int servings, String notes, String description,
                            List<String> steps, List<String> tags, List<IngredientDto> ingredients,
                            String imageMimeType, String imageData) {
        log.debug("Creating recipe for household {}: name={}, servings={}, ingredients={}, steps={}, hasImage={}",
                householdId, name, servings,
                ingredients == null ? 0 : ingredients.size(),
                steps == null ? 0 : steps.size(),
                imageData != null && !imageData.isBlank());
        Recipe recipe = new Recipe(householdId, name, servings, notes, description, steps,
                tags, imageMimeType, imageData);
        if (ingredients != null) {
            for (IngredientDto ingredient : ingredients) {
                recipe.getIngredients().add(new RecipeIngredient(recipe, ingredient.name(),
                        ingredient.quantity(), ingredient.unit(), ingredient.category()));
            }
        }
        return toDto(recipes.save(recipe));
    }

    @Override
    public RecipeDto update(UUID householdId, UUID recipeId, String name, int servings, String notes,
                            String description, List<String> steps, List<String> tags,
                            List<IngredientDto> ingredients, String imageMimeType, String imageData) {
        log.debug("Updating recipe {} for household {}: name={}, servings={}, ingredients={}, steps={}",
                recipeId, householdId, name, servings,
                ingredients == null ? 0 : ingredients.size(),
                steps == null ? 0 : steps.size());
        Recipe recipe = find(householdId, recipeId);
        recipe.update(name, servings, notes, description, steps, tags, imageMimeType, imageData);
        recipe.getIngredients().clear();
        if (ingredients != null) {
            for (IngredientDto ingredient : ingredients) {
                recipe.getIngredients().add(new RecipeIngredient(recipe, ingredient.name(),
                        ingredient.quantity(), ingredient.unit(), ingredient.category()));
            }
        }
        return toDto(recipes.save(recipe));
    }

    @Override
    public void delete(UUID householdId, UUID recipeId) {
        recipes.delete(find(householdId, recipeId));
    }

    private Recipe find(UUID householdId, UUID recipeId) {
        return recipes.findById(recipeId)
                .filter(r -> r.getHouseholdId().equals(householdId))
                .orElseThrow(() -> new EntityNotFoundException("Recipe not found: " + recipeId));
    }

    private static RecipeDto toDto(Recipe recipe) {
        List<IngredientDto> ingredients = recipe.getIngredients().stream()
                .map(i -> new IngredientDto(i.getId(), i.getName(), i.getQuantity(), i.getUnit(), i.getCategory()))
                .toList();
        // copy to detach from the Hibernate PersistentBag before serialization
        List<String> steps = recipe.getSteps() == null ? List.of() : new ArrayList<>(recipe.getSteps());
        List<String> tags = recipe.getTags() == null ? List.of() : new ArrayList<>(recipe.getTags());
        return new RecipeDto(recipe.getId(), recipe.getHouseholdId(), recipe.getName(), recipe.getServings(),
                recipe.getNotes(), recipe.getDescription(), steps, tags,
                recipe.getImageMimeType(), recipe.getImageData(), ingredients, recipe.getCreatedAt());
    }
}

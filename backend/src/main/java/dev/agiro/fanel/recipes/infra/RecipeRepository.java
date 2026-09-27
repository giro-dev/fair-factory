package dev.agiro.fanel.recipes.infra;

import dev.agiro.fanel.recipes.domain.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID>, JpaSpecificationExecutor<Recipe> {
    List<Recipe> findAllByHouseholdIdOrderByName(UUID householdId);
}

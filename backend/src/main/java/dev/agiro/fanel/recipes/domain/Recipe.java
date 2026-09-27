package dev.agiro.fanel.recipes.domain;

import dev.agiro.criteriafilter.annotation.CriteriaFilter;
import dev.agiro.criteriafilter.annotation.FilterField;
import dev.agiro.criteriafilter.model.Backend;
import dev.agiro.fanel.shared.domain.UuidEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recipe")
@CriteriaFilter(backend = Backend.JPA)
public class Recipe extends UuidEntity {
    @Column(name = "household_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID householdId;
    @FilterField
    private String name;
    @FilterField
    private int servings;
    @FilterField
    private String notes;
    @FilterField
    @Column(name = "description", length = 4000)
    private String description;
    @Column(name = "image_mime_type", length = 100)
    private String imageMimeType;
    @Column(name = "image_data", length = 4194304)
    private String imageData;
    @FilterField
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "recipe_tag", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "recipe_step", joinColumns = @JoinColumn(name = "recipe_id"))
    @OrderColumn(name = "step_order")
    @Column(name = "step")
    private List<String> steps = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    protected Recipe() {}

    public Recipe(UUID householdId, String name, int servings, String notes, String description,
                  List<String> steps, List<String> tags, String imageMimeType, String imageData) {
        this.householdId = householdId;
        this.name = name;
        this.servings = servings;
        this.notes = notes;
        this.description = description;
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        this.imageMimeType = imageMimeType;
        this.imageData = imageData;
        this.createdAt = Instant.now();
    }

    public void update(String name, int servings, String notes, String description,
                       List<String> steps, List<String> tags, String imageMimeType, String imageData) {
        this.name = name;
        this.servings = servings;
        this.notes = notes;
        this.description = description;
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        this.imageMimeType = imageMimeType;
        this.imageData = imageData;
    }

    public UUID getHouseholdId() { return householdId; }
    public String getName() { return name; }
    public int getServings() { return servings; }
    public String getNotes() { return notes; }
    public String getDescription() { return description; }
    public String getImageMimeType() { return imageMimeType; }
    public String getImageData() { return imageData; }
    public Instant getCreatedAt() { return createdAt; }
    public List<String> getTags() { return tags; }
    public List<String> getSteps() { return steps; }
    public List<RecipeIngredient> getIngredients() { return ingredients; }
}

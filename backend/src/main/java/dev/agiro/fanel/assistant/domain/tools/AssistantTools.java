package dev.agiro.fanel.assistant.domain.tools;

import dev.agiro.fanel.calendar.api.CalendarApi;
import dev.agiro.fanel.calendar.api.CalendarEventDto;
import dev.agiro.fanel.chores.api.ChoreDto;
import dev.agiro.fanel.chores.api.ChoresApi;
import dev.agiro.fanel.household.api.HouseholdApi;
import dev.agiro.fanel.household.api.MemberDto;
import dev.agiro.fanel.menu.api.MealPlanDto;
import dev.agiro.fanel.menu.api.MealType;
import dev.agiro.fanel.menu.api.MenuApi;
import dev.agiro.fanel.recipes.api.IngredientDto;
import dev.agiro.fanel.recipes.api.RecipeDto;
import dev.agiro.fanel.recipes.api.RecipesApi;
import dev.agiro.fanel.shopping.api.ShoppingApi;
import dev.agiro.fanel.shopping.api.ShoppingItemDto;
import dev.agiro.fanel.shopping.api.ShoppingListDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Component
public class AssistantTools {
    private static final Logger log = LoggerFactory.getLogger(AssistantTools.class);

    private final RecipesApi recipes;
    private final ShoppingApi shopping;
    private final MenuApi menu;
    private final ChoresApi chores;
    private final CalendarApi calendar;
    private final HouseholdApi household;

    public AssistantTools(RecipesApi recipes, ShoppingApi shopping, MenuApi menu, ChoresApi chores,
                          CalendarApi calendar, HouseholdApi household) {
        this.recipes = recipes;
        this.shopping = shopping;
        this.menu = menu;
        this.chores = chores;
        this.calendar = calendar;
        this.household = household;
    }

    @Tool(description = "Return current year week in ISO yyyy-ww")
    public String getCurrentIsoWeek() {
        LocalDate now = LocalDate.now();
        int week = now.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = now.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
        return String.format("%04d-W%02d", year, week);
    }


    @Tool(description = "List all recipes in the household")
    public List<RecipeDto> listRecipes(UUID householdId) {
        return recipes.list(householdId);
    }

    @Tool(description = "Create a new recipe with ingredients")
    public RecipeDto createRecipe(UUID householdId,
                                  @ToolParam(description = "Recipe name") String name,
                                  @ToolParam(description = "Number of servings") int servings,
                                  @ToolParam(description = "Optional notes") String notes,
                                  @ToolParam(description = "Optional preparation description") String description,
                                  @ToolParam(description = "Optional numbered preparation steps") List<String> steps,
                                  @ToolParam(description = "List of ingredients") List<IngredientInput> ingredients,
                                  @ToolParam(description = "Optional image mime type") String imageMimeType,
                                  @ToolParam(description = "Optional base64 image data") String imageData) {
        log.debug("Tool createRecipe called for household {}: name={}, servings={}, ingredients={}, steps={}, hasImage={}",
                householdId, name, servings,
                ingredients == null ? 0 : ingredients.size(),
                steps == null ? 0 : steps.size(),
                imageData != null && !imageData.isBlank());
        List<IngredientDto> dtoIngredients = ingredients == null ? List.of() : ingredients.stream()
                .map(i -> new IngredientDto(null, i.name(), i.quantity(), i.unit(), i.category()))
                .toList();
        return recipes.create(householdId, name, servings, notes, description, steps,
                List.of(), dtoIngredients, imageMimeType, imageData);
    }

    @Tool(description = "List shopping lists in the household")
    public List<ShoppingListDto> listShoppingLists(UUID householdId) {
        return shopping.listLists(householdId);
    }

    @Tool(description = "Get the default shopping list for the household")
    public ShoppingListDto getDefaultShoppingList(UUID householdId) {
        return shopping.getDefaultList(householdId);
    }

    @Tool(description = "Add an item to a shopping list. If listId is omitted, the default list is used.")
    public ShoppingItemDto addShoppingItem(UUID householdId,
                                           @ToolParam(description = "Name of the item") String name,
                                           @ToolParam(description = "Optional list id") UUID listId,
                                           @ToolParam(description = "Optional quantity") Double quantity,
                                           @ToolParam(description = "Optional unit") String unit,
                                           @ToolParam(description = "Optional category or aisle") String category) {
        UUID targetList = listId != null ? listId : shopping.getDefaultList(householdId).id();
        return shopping.addItem(householdId, targetList, name, quantity, unit, category, false);
    }

    @Tool(description = "Get the meal plan for a given ISO week")
    public MealPlanDto getMenu(UUID householdId,
                               @ToolParam(description = "ISO week year") int year,
                               @ToolParam(description = "ISO week number") int week) {
        return menu.getWeek(householdId, year, week);
    }

    @Tool(description = "Plan a meal for a specific day and slot")
    public dev.agiro.fanel.menu.api.MealSlotDto planMeal(UUID householdId,
                                                @ToolParam(description = "ISO week year") int year,
                                                @ToolParam(description = "ISO week number") int week,
                                                @ToolParam(description = "Day of week, 1=Monday through 7=Sunday") int dayOfWeek,
                                                @ToolParam(description = "Meal type: BREAKFAST, LUNCH, SNACK or DINNER") String mealType,
                                                @ToolParam(description = "Optional free-text description") String text,
                                                @ToolParam(description = "Optional recipe id") UUID recipeId) {
        return menu.setSlot(householdId, year, week, dayOfWeek, MealType.valueOf(mealType.toUpperCase()), text, recipeId);
    }

    @Tool(description = "List household chores")
    public List<ChoreDto> listChores(UUID householdId) {
        return chores.list(householdId);
    }

    @Tool(description = "Create a chore assigned to a member")
    public ChoreDto addChore(UUID householdId,
                               @ToolParam(description = "Chore title") String title,
                               @ToolParam(description = "Optional assignee member id") UUID assigneeId) {
        return chores.create(householdId, title, assigneeId);
    }

    @Tool(description = "List calendar events in a date range")
    public List<CalendarEventDto> listCalendarEvents(UUID householdId,
                                                     @ToolParam(description = "Start date ISO-8601") String from,
                                                     @ToolParam(description = "End date ISO-8601") String to) {
        return calendar.list(householdId, LocalDate.parse(from), LocalDate.parse(to));
    }

    @Tool(description = "Add a calendar event")
    public CalendarEventDto addCalendarEvent(UUID householdId,
                                             @ToolParam(description = "Event title") String title,
                                             @ToolParam(description = "Date ISO-8601") String date,
                                             @ToolParam(description = "Optional time ISO-8601 (HH:MM)") String time,
                                             @ToolParam(description = "Optional duration in minutes") Integer durationMinutes,
                                             @ToolParam(description = "Optional assignee member ids") List<UUID> assigneeIds) {
        return calendar.create(householdId, title, LocalDate.parse(date), time != null ? LocalTime.parse(time) : null,
                durationMinutes, null, assigneeIds, null, null, null);
    }

    @Tool(description = "List household members")
    public List<MemberDto> listMembers(UUID householdId) {
        return household.listMembers(householdId);
    }

    public record IngredientInput(String name, Double quantity, String unit, String category) {
    }
}

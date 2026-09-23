package dev.agiro.fanel.export.api;

import dev.agiro.fanel.automation.api.AutomationRuleDto;
import dev.agiro.fanel.calendar.api.CalendarEventDto;
import dev.agiro.fanel.calendar.api.CalendarSubscriptionDto;
import dev.agiro.fanel.chores.api.ChoreDto;
import dev.agiro.fanel.household.api.HouseholdDto;
import dev.agiro.fanel.household.api.MemberDto;
import dev.agiro.fanel.menu.api.MealPlanDto;
import dev.agiro.fanel.shopping.api.ShoppingListDto;

import java.util.List;

/** Full-household JSON payload used for export, import and scheduled backups. */
public record HouseholdExport(HouseholdDto household, List<MemberDto> members,
                              List<MealPlanDto> mealPlans, List<ShoppingListDto> shoppingLists,
                              List<CalendarEventDto> calendarEvents, List<ChoreDto> chores,
                              List<AutomationRuleDto> automationRules,
                              List<CalendarSubscriptionDto> calendarSubscriptions) {
}

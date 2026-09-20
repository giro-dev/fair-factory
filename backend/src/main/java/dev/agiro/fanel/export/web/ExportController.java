package dev.agiro.fanel.export.web;

import dev.agiro.fanel.calendar.api.CalendarApi;
import dev.agiro.fanel.calendar.api.CalendarEventDto;
import dev.agiro.fanel.chores.api.ChoreDto;
import dev.agiro.fanel.chores.api.ChoresApi;
import dev.agiro.fanel.household.api.HouseholdApi;
import dev.agiro.fanel.household.api.HouseholdDto;
import dev.agiro.fanel.household.api.MemberDto;
import dev.agiro.fanel.menu.api.MealPlanDto;
import dev.agiro.fanel.menu.api.MealSlotDto;
import dev.agiro.fanel.menu.api.MenuApi;
import dev.agiro.fanel.shared.security.CurrentAccess;
import dev.agiro.fanel.shared.web.ForbiddenException;
import dev.agiro.fanel.shopping.api.ShoppingApi;
import dev.agiro.fanel.shopping.api.ShoppingItemDto;
import dev.agiro.fanel.shopping.api.ShoppingListDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Full-household JSON export/import, used for backups and moving a household between instances. */
@RestController
@RequestMapping("/api/v1/households")
public class ExportController {
    private final HouseholdApi households;
    private final MenuApi menu;
    private final ShoppingApi shopping;
    private final CalendarApi calendar;
    private final ChoresApi chores;
    private final CurrentAccess access;

    public ExportController(HouseholdApi households, MenuApi menu, ShoppingApi shopping,
                            CalendarApi calendar, ChoresApi chores, CurrentAccess access) {
        this.households = households;
        this.menu = menu;
        this.shopping = shopping;
        this.calendar = calendar;
        this.chores = chores;
        this.access = access;
    }

    @GetMapping("/{householdId}/export")
    public HouseholdExport export(@PathVariable UUID householdId) {
        if (!access.hasFullAccess()) throw new ForbiddenException("Only admins can export a household");
        HouseholdDto household = households.get(householdId);
        List<MemberDto> members = households.listMembers(householdId);
        List<MealPlanDto> mealPlans = menu.listAll(householdId);
        List<ShoppingListDto> shoppingLists = shopping.listLists(householdId);
        List<CalendarEventDto> events = calendar.listAll(householdId);
        List<ChoreDto> choreList = chores.list(householdId);
        return new HouseholdExport(household, members, mealPlans, shoppingLists, events, choreList);
    }

    /**
     * Imports a household export as a brand-new household (new id, new member ids).
     * References to members ({@code addedBy}, {@code assigneeId}) are remapped from the
     * old member ids in the payload to the freshly created ones.
     */
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdDto importHousehold(@RequestBody HouseholdExport payload) {
        if (!access.hasGlobalAccess()) throw new ForbiddenException("Only the global admin can import households");
        HouseholdDto household = households.create(payload.household().name(), payload.household().locale(),
                payload.household().timezone());

        Map<UUID, UUID> memberIds = new HashMap<>();
        for (MemberDto member : payload.members()) {
            MemberDto created = households.addMember(household.id(), member.name(), member.role(), member.color());
            memberIds.put(member.id(), created.id());
        }

        for (MealPlanDto plan : payload.mealPlans()) {
            for (MealSlotDto slot : plan.slots()) {
                // Recipes are not part of the export payload yet, so imported slots keep only their free text.
                menu.setSlot(household.id(), plan.isoYear(), plan.isoWeek(), slot.dayOfWeek(), slot.mealType(),
                        slot.text(), null);
            }
        }

        if (!payload.shoppingLists().isEmpty()) {
            var defaultList = shopping.getDefaultList(household.id());
            boolean first = true;
            for (ShoppingListDto list : payload.shoppingLists()) {
                UUID listId;
                if (first) {
                    if (list.name() != null && !list.name().isBlank() && !list.name().equals(defaultList.name())) {
                        shopping.updateList(household.id(), defaultList.id(), list.name());
                    }
                    listId = defaultList.id();
                    first = false;
                } else {
                    var createdList = shopping.createList(household.id(), list.name());
                    listId = createdList.id();
                }
                for (ShoppingItemDto item : list.items()) {
                    var created = shopping.addItem(household.id(), listId, item.name(), item.quantity(), item.unit(),
                            item.category(), item.recurring());
                    if (item.done()) shopping.setDone(household.id(), created.id(), true);
                }
            }
        }

        for (CalendarEventDto event : payload.calendarEvents()) {
            List<UUID> assigneeIds = event.assigneeIds().stream().map(memberIds::get).filter(Objects::nonNull).toList();
            calendar.create(household.id(), event.title(), event.anchorDate(), event.time(), memberIds.get(event.addedBy()),
                    assigneeIds, event.recurrenceFreq(), event.recurrenceInterval(), event.recurrenceUntil());
        }

        for (ChoreDto chore : payload.chores()) {
            List<UUID> rotationMemberIds = chore.rotationMemberIds().stream()
                    .map(memberIds::get).filter(Objects::nonNull).toList();
            var created = chores.create(household.id(), chore.title(), memberIds.get(chore.assigneeId()),
                    chore.dueDate(), chore.recurrenceFreq(), chore.recurrenceInterval(), rotationMemberIds);
            if (chore.done()) chores.update(household.id(), created.id(), null, null, true);
        }

        return household;
    }

    public record HouseholdExport(HouseholdDto household, List<MemberDto> members,
                                  List<MealPlanDto> mealPlans, List<ShoppingListDto> shoppingLists,
                                  List<CalendarEventDto> calendarEvents, List<ChoreDto> chores) {
    }
}

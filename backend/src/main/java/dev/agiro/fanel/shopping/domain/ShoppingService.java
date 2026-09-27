package dev.agiro.fanel.shopping.domain;

import dev.agiro.fanel.shared.events.HouseholdEvent;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import dev.agiro.fanel.shopping.api.ShoppingApi;
import dev.agiro.fanel.shopping.api.ShoppingItemDto;
import dev.agiro.fanel.shopping.api.ShoppingListDto;
import dev.agiro.fanel.shopping.infra.ShoppingItemRepository;
import dev.agiro.fanel.shopping.infra.ShoppingListRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ShoppingService implements ShoppingApi {
    public static final String TOPIC = "shopping";
    private static final String DEFAULT_LIST_NAME = "Compra";

    private final ShoppingListRepository lists;
    private final ShoppingItemRepository items;
    private final ApplicationEventPublisher events;

    public ShoppingService(ShoppingListRepository lists, ShoppingItemRepository items,
                           ApplicationEventPublisher events) {
        this.lists = lists;
        this.items = items;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShoppingListDto> listLists(UUID householdId) {
        return lists.findAllByHouseholdId(householdId).stream().map(ShoppingService::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShoppingListDto getList(UUID householdId, UUID listId) {
        return toDto(findList(householdId, listId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShoppingListDto> findDefaultList(UUID householdId) {
        return lists.findFirstByHouseholdIdOrderByName(householdId).map(ShoppingService::toDto);
    }

    @Override
    public ShoppingListDto getDefaultList(UUID householdId) {
        return findDefaultList(householdId)
                .orElseGet(() -> toDto(lists.save(new ShoppingList(householdId, DEFAULT_LIST_NAME))));
    }

    @Override
    public ShoppingListDto createList(UUID householdId, String name) {
        ShoppingList list = lists.save(new ShoppingList(householdId, name));
        events.publishEvent(new HouseholdEvent(householdId, TOPIC));
        return toDto(list);
    }

    @Override
    public ShoppingListDto updateList(UUID householdId, UUID listId, String name) {
        ShoppingList list = findList(householdId, listId);
        list.setName(name);
        events.publishEvent(new HouseholdEvent(householdId, TOPIC));
        return toDto(lists.save(list));
    }

    @Override
    public void deleteList(UUID householdId, UUID listId) {
        ShoppingList list = findList(householdId, listId);
        lists.delete(list);
        events.publishEvent(new HouseholdEvent(householdId, TOPIC));
    }

    @Override
    public ShoppingItemDto addItem(UUID householdId, UUID listId, String name, Double quantity, String unit,
                                   String category, boolean recurring) {
        ShoppingList list = findList(householdId, listId);
        ShoppingItem saved = items.save(new ShoppingItem(list, name, quantity, unit, category, recurring));
        events.publishEvent(new HouseholdEvent(householdId, TOPIC));
        return toDto(saved);
    }

    @Override
    public ShoppingItemDto updateItem(UUID householdId, UUID itemId, String name, Double quantity, String unit,
                                      String category, Boolean recurring, Boolean done) {
        ShoppingItem item = findItem(householdId, itemId);
        if (name != null) item.setName(name);
        if (quantity != null) item.setQuantity(quantity);
        if (unit != null) item.setUnit(unit);
        if (category != null) item.setCategory(category);
        if (recurring != null) item.setRecurring(recurring);
        if (done != null) item.setDone(done);
        events.publishEvent(new HouseholdEvent(householdId, TOPIC));
        return toDto(items.save(item));
    }

    @Override
    public ShoppingItemDto setDone(UUID householdId, UUID itemId, boolean done) {
        ShoppingItem item = findItem(householdId, itemId);
        item.setDone(done);
        events.publishEvent(new HouseholdEvent(householdId, TOPIC));
        return toDto(items.save(item));
    }

    @Override
    public void removeItem(UUID householdId, UUID itemId) {
        ShoppingItem item = findItem(householdId, itemId);
        item.getList().getItems().remove(item);
        items.delete(item);
        events.publishEvent(new HouseholdEvent(householdId, TOPIC));
    }

    @Override
    public int clearPurchased(UUID householdId, UUID listId) {
        ShoppingList list = findList(householdId, listId);
        List<ShoppingItem> purchased = list.getItems().stream().filter(ShoppingItem::isDone).toList();
        for (ShoppingItem item : purchased) {
            if (item.isRecurring()) {
                item.setDone(false);
                items.save(item);
            } else {
                list.getItems().remove(item);
                items.delete(item);
            }
        }
        events.publishEvent(new HouseholdEvent(householdId, TOPIC));
        return purchased.size();
    }

    private ShoppingList findList(UUID householdId, UUID listId) {
        ShoppingList list = lists.findById(listId)
                .orElseThrow(() -> new EntityNotFoundException("Shopping list not found: " + listId));
        if (!list.getHouseholdId().equals(householdId)) {
            throw new EntityNotFoundException("Shopping list not found: " + listId);
        }
        return list;
    }

    private ShoppingItem findItem(UUID householdId, UUID itemId) {
        ShoppingItem item = items.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Shopping item not found: " + itemId));
        if (!item.getList().getHouseholdId().equals(householdId)) {
            throw new EntityNotFoundException("Shopping item not found: " + itemId);
        }
        return item;
    }

    private static ShoppingListDto toDto(ShoppingList list) {
        return new ShoppingListDto(list.getId(), list.getHouseholdId(), list.getName(),
                list.getItems().stream().map(ShoppingService::toDto).toList());
    }

    private static ShoppingItemDto toDto(ShoppingItem item) {
        return new ShoppingItemDto(item.getId(), item.getName(), item.getQuantity(), item.getUnit(),
                item.getCategory(), item.isRecurring(), item.isDone());
    }
}

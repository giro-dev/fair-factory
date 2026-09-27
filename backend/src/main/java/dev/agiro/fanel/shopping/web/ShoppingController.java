package dev.agiro.fanel.shopping.web;

import dev.agiro.fanel.shared.web.EntityNotFoundException;
import dev.agiro.fanel.shopping.api.ShoppingApi;
import dev.agiro.fanel.shopping.api.ShoppingItemDto;
import dev.agiro.fanel.shopping.api.ShoppingListDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/households/{householdId}/shopping")
public class ShoppingController {
    private final ShoppingApi shopping;

    public ShoppingController(ShoppingApi shopping) {
        this.shopping = shopping;
    }

    @GetMapping("/lists")
    public List<ShoppingListDto> listLists(@PathVariable UUID householdId) {
        return shopping.listLists(householdId);
    }

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingListDto createList(@PathVariable UUID householdId, @Valid @RequestBody CreateList request) {
        return shopping.createList(householdId, request.name());
    }

    @GetMapping("/lists/{listId}")
    public ShoppingListDto getList(@PathVariable UUID householdId, @PathVariable UUID listId) {
        return shopping.getList(householdId, listId);
    }

    @PutMapping("/lists/{listId}")
    public ShoppingListDto updateList(@PathVariable UUID householdId, @PathVariable UUID listId,
                                      @Valid @RequestBody UpdateList request) {
        return shopping.updateList(householdId, listId, request.name());
    }

    @DeleteMapping("/lists/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteList(@PathVariable UUID householdId, @PathVariable UUID listId) {
        shopping.deleteList(householdId, listId);
    }

    @GetMapping("/lists/default")
    public ShoppingListDto defaultList(@PathVariable UUID householdId) {
        return shopping.findDefaultList(householdId)
                .orElseThrow(() -> new EntityNotFoundException("No shopping list for household: " + householdId));
    }

    @PostMapping("/lists/{listId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingItemDto addItem(@PathVariable UUID householdId, @PathVariable UUID listId,
                                   @Valid @RequestBody AddItem request) {
        return shopping.addItem(householdId, listId, request.name(), request.quantity(), request.unit(),
                request.category(), Boolean.TRUE.equals(request.recurring()));
    }

    @PatchMapping("/items/{itemId}")
    public ShoppingItemDto updateItem(@PathVariable UUID householdId, @PathVariable UUID itemId,
                                      @RequestBody UpdateItem request) {
        return shopping.updateItem(householdId, itemId, request.name(), request.quantity(), request.unit(),
                request.category(), request.recurring(), request.done());
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@PathVariable UUID householdId, @PathVariable UUID itemId) {
        shopping.removeItem(householdId, itemId);
    }

    @PostMapping("/lists/{listId}/clear-purchased")
    public Map<String, Integer> clearPurchased(@PathVariable UUID householdId, @PathVariable UUID listId) {
        return Map.of("removed", shopping.clearPurchased(householdId, listId));
    }

    public record CreateList(@NotBlank String name) {}
    public record UpdateList(@NotBlank String name) {}
    public record AddItem(@NotBlank String name, Double quantity, String unit, String category, Boolean recurring) {}
    public record UpdateItem(String name, Double quantity, String unit, String category, Boolean recurring, Boolean done) {}
}

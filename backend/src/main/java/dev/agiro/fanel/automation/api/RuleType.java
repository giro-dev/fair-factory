package dev.agiro.fanel.automation.api;

public enum RuleType {
    /** Weekly: asks the menu-planner agent to fill the empty lunch/dinner slots of the next ISO week. */
    MENU_PROPOSAL,
    /** Scheduled push reminder with the number of pending items on the default shopping list. */
    SHOPPING_REMINDER
}

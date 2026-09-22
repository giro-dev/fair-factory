package dev.agiro.fanel.automation.domain;

import dev.agiro.fanel.assistant.api.AssistantApi;
import dev.agiro.fanel.automation.api.RuleType;
import dev.agiro.fanel.household.api.HouseholdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.Locale;
import java.util.UUID;

/** Asks the menu-planner agent to fill the empty lunch/dinner slots of the next ISO week. */
@Component
public class MenuProposalRunner implements RuleRunner {
    private static final Logger log = LoggerFactory.getLogger(MenuProposalRunner.class);
    static final String AGENT_ID = "menu-planner";

    private final AssistantApi assistant;
    private final HouseholdApi household;

    public MenuProposalRunner(AssistantApi assistant, HouseholdApi household) {
        this.assistant = assistant;
        this.household = household;
    }

    @Override
    public RuleType type() {
        return RuleType.MENU_PROPOSAL;
    }

    @Override
    public void run(UUID householdId, Locale locale) {
        ZoneId zone;
        String timezone = household.get(householdId).timezone();
        try {
            zone = ZoneId.of(timezone);
        } catch (DateTimeException e) {
            log.warn("Unknown timezone {} for household {}; falling back to system default", timezone, householdId);
            zone = ZoneId.systemDefault();
        }
        LocalDate nextWeek = LocalDate.now(zone).plusWeeks(1);
        int year = nextWeek.get(IsoFields.WEEK_BASED_YEAR);
        int week = nextWeek.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        // English on purpose: the agent's system prompt handles the reply language.
        String message = "Fill the empty lunch and dinner slots of ISO week " + year + "-W" + week
                + " with recipes from this household.";
        var response = assistant.run(householdId, AGENT_ID, message, locale);
        if (response.isEmpty()) {
            log.warn("Menu proposal for household {} skipped: agent '{}' unavailable or failed", householdId, AGENT_ID);
        }
    }
}

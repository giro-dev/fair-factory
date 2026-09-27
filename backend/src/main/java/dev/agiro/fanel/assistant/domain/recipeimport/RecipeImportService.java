package dev.agiro.fanel.assistant.domain.recipeimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agiro.fanel.assistant.api.AgentResponse;
import dev.agiro.fanel.assistant.api.AssistantApi;
import dev.agiro.fanel.assistant.domain.AgentRegistry;
import dev.agiro.fanel.assistant.domain.recipeimport.ImportOutcome.ImportFailure;
import dev.agiro.fanel.assistant.infra.recipeimport.WebRecipeParser;
import dev.agiro.fanel.recipes.api.IngredientDto;
import dev.agiro.fanel.recipes.api.RecipeDto;
import dev.agiro.fanel.recipes.api.RecipesApi;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-phase recipe book ingestion: {@code analyze*} splits a file or URL into candidates
 * kept in an in-memory session; {@code confirm} materializes the chosen ones as recipes.
 * With an active {@code recipe-extractor} agent the section text is structured by the
 * model; without it, the raw text is imported as the recipe description.
 */
@Service
public class RecipeImportService {
    private static final Logger log = LoggerFactory.getLogger(RecipeImportService.class);

    public static final String EXTRACTOR_AGENT = "recipe-extractor";
    private static final Duration SESSION_TTL = Duration.ofMinutes(60);
    private static final int MAX_SECTION_CHARS = 24_000;
    private static final int SNIPPET_CHARS = 240;
    private static final int MAX_DESCRIPTION = 3_900;

    private final Map<UUID, ImportSession> sessions = new ConcurrentHashMap<>();

    private final List<RecipeBookParser> fileParsers;
    private final WebRecipeParser webParser;
    private final RecipesApi recipes;
    private final AssistantApi assistant;
    private final AgentRegistry agentRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    public RecipeImportService(List<RecipeBookParser> fileParsers, WebRecipeParser webParser,
                               RecipesApi recipes, AssistantApi assistant,
                               AgentRegistry agentRegistry) {
        this.fileParsers = fileParsers;
        this.webParser = webParser;
        this.recipes = recipes;
        this.assistant = assistant;
        this.agentRegistry = agentRegistry;
    }

    public ImportSessionInfo analyzeFile(UUID householdId, String fileName, String contentType,
                                         byte[] data) {
        RecipeBookParser parser = fileParsers.stream()
                .filter(p -> p.supports(fileName, contentType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported file type. Accepted formats: PDF and EPUB"));
        List<BookSection> sections = parser.parse(data);
        return openSession(householdId, fileName != null ? fileName : "file", sections);
    }

    public ImportSessionInfo analyzeUrl(UUID householdId, String url) {
        List<BookSection> sections = webParser.fetch(url);
        return openSession(householdId, url, sections);
    }

    private ImportSessionInfo openSession(UUID householdId, String source, List<BookSection> sections) {
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("No recipe candidates found in the source");
        }
        purgeExpired();
        UUID id = UUID.randomUUID();
        sessions.put(id, new ImportSession(householdId, source, sections, Instant.now()));
        log.info("Import session {} opened for household {}: source={}, candidates={}",
                id, householdId, source, sections.size());
        return toInfo(id, sessions.get(id));
    }

    private ImportSessionInfo toInfo(UUID id, ImportSession session) {
        List<CandidateInfo> candidates = session.sections().stream()
                .map(s -> new CandidateInfo(s.key(), s.title(), snippet(s), s.structured() != null))
                .toList();
        return new ImportSessionInfo(id, session.source(), candidates, aiAvailable());
    }

    private boolean aiAvailable() {
        return agentRegistry.isEnabled(EXTRACTOR_AGENT);
    }

    private static String snippet(BookSection section) {
        String text = section.text();
        if (text == null) {
            return section.structured() != null && section.structured().description() != null
                    ? abbreviate(section.structured().description()) : "";
        }
        return abbreviate(text);
    }

    private static String abbreviate(String text) {
        String collapsed = text.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= SNIPPET_CHARS ? collapsed : collapsed.substring(0, SNIPPET_CHARS) + "…";
    }

    public ImportOutcome confirm(UUID householdId, UUID importId, List<String> keys, Locale locale) {
        ImportSession session = sessions.get(importId);
        if (session == null || !session.householdId().equals(householdId)) {
            throw new EntityNotFoundException("Import session not found: " + importId);
        }
        if (session.createdAt().plus(SESSION_TTL).isBefore(Instant.now())) {
            sessions.remove(importId);
            throw new IllegalArgumentException("The import session has expired; upload the book again");
        }
        List<RecipeDto> imported = new ArrayList<>();
        List<ImportFailure> failures = new ArrayList<>();
        for (String key : keys) {
            session.sections().stream().filter(s -> s.key().equals(key)).findFirst()
                    .ifPresentOrElse(
                            section -> importSection(householdId, section, locale, imported, failures),
                            () -> failures.add(new ImportFailure(key, key, "Unknown candidate")));
        }
        log.info("Import session {} confirmed for household {}: imported={}, failures={}",
                importId, householdId, imported.size(), failures.size());
        return new ImportOutcome(imported, failures);
    }

    public void discard(UUID householdId, UUID importId) {
        sessions.computeIfPresent(importId,
                (id, s) -> s.householdId().equals(householdId) ? null : s);
    }

    private void importSection(UUID householdId, BookSection section, Locale locale,
                               List<RecipeDto> imported, List<ImportFailure> failures) {
        try {
            ImportedRecipe payload = section.structured() != null
                    ? section.structured()
                    : extract(section, householdId, locale);
            imported.add(persist(householdId, payload, section.title()));
        } catch (Exception e) {
            log.warn("Failed to import candidate '{}' ({}): {}", section.title(), section.key(), e.toString());
            failures.add(new ImportFailure(section.key(), section.title(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private ImportedRecipe extract(BookSection section, UUID householdId, Locale locale) {
        String text = section.text() == null ? "" : section.text();
        String excerpt = text.length() > MAX_SECTION_CHARS ? text.substring(0, MAX_SECTION_CHARS) : text;
        Optional<AgentResponse> response = assistant.run(householdId, EXTRACTOR_AGENT, excerpt,
                locale != null ? locale : Locale.forLanguageTag("ca"));
        if (response.isPresent() && response.get().text() != null
                && !response.get().text().isBlank()) {
            try {
                ImportedRecipe recipe = mapper.readValue(stripFences(response.get().text()),
                        ImportedRecipe.class);
                if (recipe.name() != null && !recipe.name().isBlank()) {
                    return recipe;
                }
            } catch (Exception e) {
                log.warn("recipe-extractor returned unparseable output for '{}': {}",
                        section.title(), e.getMessage());
            }
        }
        // Fallback: keep the raw text so nothing found is lost.
        String description = text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) : text;
        return new ImportedRecipe(section.title(), null, description, null, List.of(), List.of(),
                List.of(), null, null);
    }

    private RecipeDto persist(UUID householdId, ImportedRecipe payload, String fallbackName) {
        String name = payload.name() != null && !payload.name().isBlank()
                ? payload.name().trim() : fallbackName;
        List<IngredientDto> ingredients = payload.ingredients() == null ? List.of()
                : payload.ingredients().stream()
                        .filter(i -> i.name() != null && !i.name().isBlank())
                        .map(i -> new IngredientDto(null, i.name().trim(), i.quantity(), i.unit(), i.category()))
                        .toList();
        return recipes.create(householdId, name,
                payload.servings() != null && payload.servings() > 0 ? payload.servings() : 4,
                payload.notes(), payload.description(), payload.steps(), payload.tags(),
                ingredients, payload.imageMimeType(), payload.imageData());
    }

    private static String stripFences(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        sessions.values().removeIf(s -> s.createdAt().isBefore(cutoff));
    }

    private record ImportSession(UUID householdId, String source, List<BookSection> sections,
                                 Instant createdAt) {
    }
}

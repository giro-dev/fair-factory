package dev.agiro.fanel.assistant.domain.recipeimport;

import dev.agiro.fanel.assistant.api.AgentResponse;
import dev.agiro.fanel.assistant.api.AssistantApi;
import dev.agiro.fanel.assistant.domain.AgentRegistry;
import dev.agiro.fanel.assistant.infra.recipeimport.WebRecipeParser;
import dev.agiro.fanel.recipes.api.IngredientDto;
import dev.agiro.fanel.recipes.api.RecipeDto;
import dev.agiro.fanel.recipes.api.RecipesApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeImportServiceTest {

    @Mock RecipesApi recipes;
    @Mock AssistantApi assistant;
    @Mock AgentRegistry agentRegistry;
    @Mock WebRecipeParser webParser;

    private RecipeImportService service;
    private final UUID household = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RecipeImportService(List.of(), webParser, recipes, assistant, agentRegistry);
    }

    private ImportSessionInfo sessionWith(BookSection section, boolean aiAvailable) {
        when(agentRegistry.isEnabled(RecipeImportService.EXTRACTOR_AGENT)).thenReturn(aiAvailable);
        when(webParser.fetch(anyString())).thenReturn(List.of(section));
        return service.analyzeUrl(household, "https://blog.example/recepta");
    }

    @Test
    void confirmWithAgentStructuresJson() {
        ImportSessionInfo info = sessionWith(
                BookSection.unstructured("k1", "Sopa", "Ingredientes: ajo, pan..."), true);
        when(assistant.run(eq(household), eq("recipe-extractor"), anyString(), any(Locale.class)))
                .thenReturn(Optional.of(new AgentResponse("recipe-extractor", "c1",
                        "```json\n{\"name\":\"Sopa d'all\",\"servings\":2,"
                                + "\"steps\":[\"Fregir l'all\"],"
                                + "\"ingredients\":[{\"name\":\"all\",\"quantity\":2,\"unit\":\"unitat\"}]}\n```",
                        List.of(), List.of())));
        when(recipes.create(eq(household), anyString(), anyInt(), any(), any(), any(), any(),
                any(), any(), any()))
                .thenAnswer(inv -> new RecipeDto(UUID.randomUUID(), household, inv.getArgument(1),
                        inv.getArgument(2), null, null, List.of(), List.of(), null, null, List.of(), null));

        ImportOutcome outcome = service.confirm(household, info.id(), List.of("k1"), Locale.forLanguageTag("es"));

        assertEquals(1, outcome.imported().size());
        assertTrue(outcome.failures().isEmpty());
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(recipes).create(eq(household), name.capture(), eq(2), any(), any(),
                eq(List.of("Fregir l'all")), any(), any(), any(), any());
        assertEquals("Sopa d'all", name.getValue());
    }

    @Test
    void confirmWithoutAgentFallsBackToRawText() {
        ImportSessionInfo info = sessionWith(
                BookSection.unstructured("k1", "Sopa", "Text complet de la recepta"), false);
        when(assistant.run(eq(household), eq("recipe-extractor"), anyString(), any(Locale.class)))
                .thenReturn(Optional.empty());
        when(recipes.create(eq(household), anyString(), anyInt(), any(), any(), any(), any(),
                any(), any(), any()))
                .thenAnswer(inv -> new RecipeDto(UUID.randomUUID(), household, inv.getArgument(1),
                        inv.getArgument(2), null, inv.getArgument(4), List.of(), List.of(), null, null,
                        List.of(), null));

        ImportOutcome outcome = service.confirm(household, info.id(), List.of("k1"), Locale.forLanguageTag("ca"));

        assertEquals(1, outcome.imported().size());
        assertEquals("Text complet de la recepta", outcome.imported().get(0).description());
        assertEquals("Sopa", outcome.imported().get(0).name());
    }

    @Test
    void structuredCandidateSkipsAgent() {
        ImportedRecipe recipe = new ImportedRecipe("Directe", 2, "desc", null,
                List.of("pas"), List.of("etiqueta"),
                List.of(new IngredientPayload("ou", 2.0, "unitat", null)), null, null);
        ImportSessionInfo info = sessionWith(BookSection.structured("ld0", "Directe", recipe), true);
        when(recipes.create(eq(household), eq("Directe"), eq(2), any(), eq("desc"),
                eq(List.of("pas")), eq(List.of("etiqueta")), any(), any(), any()))
                .thenReturn(new RecipeDto(UUID.randomUUID(), household, "Directe", 2, null, "desc",
                        List.of("pas"), List.of("etiqueta"), null, null,
                        List.of(new IngredientDto(null, "ou", 2.0, "unitat", null)), null));

        ImportOutcome outcome = service.confirm(household, info.id(), List.of("ld0"), Locale.ROOT);

        assertEquals(1, outcome.imported().size());
        assertEquals(1, outcome.imported().get(0).ingredients().size());
    }

    @Test
    void unknownCandidateReportsFailure() {
        ImportSessionInfo info = sessionWith(BookSection.unstructured("k1", "Sopa", "text"), false);
        ImportOutcome outcome = service.confirm(household, info.id(), List.of("nope"), Locale.ROOT);
        assertEquals(1, outcome.failures().size());
        assertEquals("nope", outcome.failures().get(0).key());
    }
}

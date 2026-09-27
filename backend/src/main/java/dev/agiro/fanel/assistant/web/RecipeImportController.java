package dev.agiro.fanel.assistant.web;

import dev.agiro.fanel.assistant.domain.recipeimport.ImportOutcome;
import dev.agiro.fanel.assistant.domain.recipeimport.ImportSessionInfo;
import dev.agiro.fanel.assistant.domain.recipeimport.RecipeImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Two-phase recipe book import: upload/fetch produces a candidate list; confirm persists
 * the chosen candidates as household recipes.
 */
@RestController
@RequestMapping("/api/v1/households/{householdId}/recipes/import")
public class RecipeImportController {
    private static final Logger log = LoggerFactory.getLogger(RecipeImportController.class);

    private final RecipeImportService imports;

    public RecipeImportController(RecipeImportService imports) {
        this.imports = imports;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportSessionInfo upload(@PathVariable UUID householdId,
                                    @RequestPart("file") MultipartFile file) {
        log.debug("POST /recipes/import for household {}: file={}, size={}",
                householdId, file.getOriginalFilename(), file.getSize());
        try {
            return imports.analyzeFile(householdId, file.getOriginalFilename(),
                    file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @PostMapping("/url")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportSessionInfo fromUrl(@PathVariable UUID householdId,
                                     @Valid @RequestBody UrlImport request) {
        return imports.analyzeUrl(householdId, request.url());
    }

    @PostMapping("/{importId}/confirm")
    public ImportOutcome confirm(@PathVariable UUID householdId, @PathVariable UUID importId,
                                 @Valid @RequestBody ConfirmImport request, Locale locale) {
        return imports.confirm(householdId, importId, request.candidates(), locale);
    }

    @DeleteMapping("/{importId}")
    public void discard(@PathVariable UUID householdId, @PathVariable UUID importId) {
        imports.discard(householdId, importId);
    }

    public record UrlImport(@NotBlank String url) {
    }

    public record ConfirmImport(@NotEmpty List<String> candidates) {
    }
}

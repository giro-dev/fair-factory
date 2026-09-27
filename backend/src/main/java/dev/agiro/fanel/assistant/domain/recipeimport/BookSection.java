package dev.agiro.fanel.assistant.domain.recipeimport;

/**
 * One candidate found inside an imported book or page. {@code text} is the raw content sent
 * to the extraction agent; {@code structured} is set when the source already carried a
 * machine-readable recipe (JSON-LD) and no AI pass is needed.
 */
public record BookSection(String key, String title, String text, ImportedRecipe structured) {

    public static BookSection unstructured(String key, String title, String text) {
        return new BookSection(key, title, text, null);
    }

    public static BookSection structured(String key, String title, ImportedRecipe recipe) {
        return new BookSection(key, title, null, recipe);
    }
}

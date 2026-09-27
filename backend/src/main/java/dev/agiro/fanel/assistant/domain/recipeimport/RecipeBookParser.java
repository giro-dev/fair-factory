package dev.agiro.fanel.assistant.domain.recipeimport;

import java.util.List;

/**
 * Splits an uploaded recipe book into candidate {@link BookSection}s.
 */
public interface RecipeBookParser {

    /** Lowercase file extensions this parser accepts, without the dot (e.g. "pdf"). */
    boolean supports(String fileName, String contentType);

    List<BookSection> parse(byte[] data);
}

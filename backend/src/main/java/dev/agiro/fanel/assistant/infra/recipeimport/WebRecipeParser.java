package dev.agiro.fanel.assistant.infra.recipeimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agiro.fanel.assistant.domain.recipeimport.BookSection;
import dev.agiro.fanel.assistant.domain.recipeimport.ImportedRecipe;
import dev.agiro.fanel.assistant.domain.recipeimport.IngredientPayload;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Extracts recipe candidates from a web page. Prefers schema.org {@code Recipe} JSON-LD
 * (structured, no AI pass needed); otherwise returns the article text as a single
 * unstructured candidate for the extraction agent.
 */
@Component
public class WebRecipeParser {
    private static final Logger log = LoggerFactory.getLogger(WebRecipeParser.class);

    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final String USER_AGENT = "Fanel/1.0 (recipe importer)";

    private final ObjectMapper mapper = new ObjectMapper();

    public List<BookSection> fetch(String url) {
        URI uri = parseUrl(url);
        Document doc;
        try {
            doc = Jsoup.connect(uri.toString())
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not fetch the page: " + e.getMessage(), e);
        }

        List<BookSection> structured = jsonLdRecipes(doc);
        if (!structured.isEmpty()) {
            return structured;
        }

        String text = mainText(doc);
        if (text.length() < 80) {
            throw new IllegalArgumentException("The page contains no readable recipe text");
        }
        String title = doc.title() != null && !doc.title().isBlank() ? doc.title() : uri.getHost();
        return List.of(BookSection.unstructured("page", title.trim(), text));
    }

    private URI parseUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
        if (uri.getScheme() == null
                || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("Only http(s) URLs are supported");
        }
        return uri;
    }

    private List<BookSection> jsonLdRecipes(Document doc) {
        List<BookSection> sections = new ArrayList<>();
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = mapper.readTree(script.data());
                List<JsonNode> recipes = new ArrayList<>();
                collectRecipes(root, recipes);
                for (JsonNode node : recipes) {
                    ImportedRecipe recipe = toImportedRecipe(node, doc.baseUri());
                    String title = recipe.name() != null ? recipe.name() : "Recipe";
                    sections.add(BookSection.structured("ld" + sections.size(), title, recipe));
                }
            } catch (Exception e) {
                log.debug("Skipping unparseable JSON-LD block in {}: {}", doc.baseUri(), e.getMessage());
            }
        }
        return sections;
    }

    private void collectRecipes(JsonNode node, List<JsonNode> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectRecipes(child, out));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        JsonNode graph = node.get("@graph");
        if (graph != null) {
            collectRecipes(graph, out);
        }
        JsonNode type = node.get("@type");
        boolean isRecipe = type != null && (type.isArray()
                ? iterableContains(type, "Recipe")
                : "Recipe".equalsIgnoreCase(type.asText()));
        if (isRecipe) {
            out.add(node);
        }
    }

    private static boolean iterableContains(JsonNode array, String value) {
        for (JsonNode item : array) {
            if (value.equalsIgnoreCase(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private ImportedRecipe toImportedRecipe(JsonNode node, String baseUri) {
        String name = text(node.get("name"));
        String description = text(node.get("description"));
        Integer servings = servings(node.get("recipeYield"));
        List<IngredientPayload> ingredients = ingredients(node.get("recipeIngredient"));
        List<String> steps = steps(node.get("recipeInstructions"));
        List<String> tags = tags(node.get("keywords"));

        String imageMime = null;
        String imageData = null;
        String imageUrl = imageUrl(node.get("image"), baseUri);
        if (imageUrl != null) {
            try {
                Connection.Response response = Jsoup.connect(imageUrl)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .ignoreContentType(true)
                        .maxBodySize(MAX_IMAGE_BYTES)
                        .execute();
                String mime = response.contentType();
                if (mime != null && mime.startsWith("image/")) {
                    imageMime = mime.split(";")[0].trim();
                    imageData = Base64.getEncoder().encodeToString(response.bodyAsBytes());
                }
            } catch (IOException e) {
                log.debug("Could not download recipe image {}: {}", imageUrl, e.getMessage());
            }
        }
        return new ImportedRecipe(name, servings, description, null, steps, tags, ingredients,
                imageMime, imageData);
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return node.isEmpty() ? null : node.get(0).asText();
        }
        if (node.isObject()) {
            JsonNode name = node.get("name");
            return name != null ? name.asText() : null;
        }
        return node.asText();
    }

    private static Integer servings(JsonNode node) {
        String raw = text(node);
        if (raw == null) {
            return null;
        }
        var matcher = java.util.regex.Pattern.compile("\\d+").matcher(raw);
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }

    private static List<IngredientPayload> ingredients(JsonNode node) {
        List<IngredientPayload> ingredients = new ArrayList<>();
        if (node == null) {
            return ingredients;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.isObject() ? text(item.get("name")) : item.asText();
                if (value != null && !value.isBlank()) {
                    ingredients.add(new IngredientPayload(value.trim(), null, null, null));
                }
            }
        } else if (node.isTextual()) {
            for (String line : node.asText().split("\\n")) {
                if (!line.isBlank()) {
                    ingredients.add(new IngredientPayload(line.trim(), null, null, null));
                }
            }
        }
        return ingredients;
    }

    private static List<String> steps(JsonNode node) {
        List<String> steps = new ArrayList<>();
        if (node == null) {
            return steps;
        }
        if (node.isTextual()) {
            steps.add(node.asText());
            return steps;
        }
        for (JsonNode item : node.isArray() ? node : List.of(node)) {
            JsonNode nested = item.get("itemListElement");
            if (nested != null) {
                steps.addAll(steps(nested));
                continue;
            }
            String value = item.isObject() ? text(item.get("text")) : item.asText();
            if (value != null && !value.isBlank()) {
                steps.add(value.trim());
            }
        }
        return steps;
    }

    private static List<String> tags(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        if (node.isArray()) {
            List<String> tags = new ArrayList<>();
            node.forEach(item -> tags.add(item.asText()));
            return tags;
        }
        String raw = node.asText();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split("\\s*,\\s*"));
    }

    private static String imageUrl(JsonNode node, String baseUri) {
        String url = null;
        if (node == null) {
            return null;
        }
        if (node.isArray() && !node.isEmpty()) {
            url = imageUrl(node.get(0), baseUri);
        } else if (node.isTextual()) {
            url = node.asText();
        } else if (node.isObject()) {
            JsonNode u = node.get("url");
            if (u != null) {
                url = u.asText();
            }
        }
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return URI.create(baseUri).resolve(url.trim()).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String mainText(Document doc) {
        Element main = doc.selectFirst("article, main, [itemtype*=Recipe]");
        String text = (main != null ? main : doc.body()).text();
        return text == null ? "" : text.trim();
    }
}

package dev.agiro.fanel.assistant.infra.recipeimport;

import com.sun.net.httpserver.HttpServer;
import dev.agiro.fanel.assistant.domain.recipeimport.BookSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebRecipeParserTest {

    private final WebRecipeParser parser = new WebRecipeParser();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String serve(String html) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/recipe", exchange -> {
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/recipe";
    }

    @Test
    void jsonLdBecomesStructuredCandidate() throws Exception {
        String url = serve("""
                <html><head><title>Gaspatxo</title>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"Recipe","name":"Gaspatxo",
                 "description":"Sopa freda","recipeYield":"4 servings",
                 "recipeIngredient":["1 kg de tomàquets","1 pebrot","oli"],
                 "recipeInstructions":[{"@type":"HowToStep","text":"Rentar"},{"@type":"HowToStep","text":"Triturar"}],
                 "keywords":"sopa, freda, andalusa"}
                </script></head><body>text</body></html>
                """);
        List<BookSection> sections = parser.fetch(url);
        assertEquals(1, sections.size());
        BookSection s = sections.get(0);
        assertNotNull(s.structured());
        assertEquals("Gaspatxo", s.structured().name());
        assertEquals(4, s.structured().servings());
        assertEquals(3, s.structured().ingredients().size());
        assertEquals(List.of("Rentar", "Triturar"), s.structured().steps());
        assertTrue(s.structured().tags().contains("sopa"));
    }

    @Test
    void plainPageFallsBackToUnstructuredCandidate() throws Exception {
        String url = serve("""
                <html><head><title>La meva truita</title></head>
                <body><article><h1>Truita</h1>
                <p>Batre quatre ous amb les patates fregides i quallar tot plegat a la paella
                fins que quedi sòlid però jugós per dins, tal com agrada a casa.</p></article></body></html>
                """);
        List<BookSection> sections = parser.fetch(url);
        assertEquals(1, sections.size());
        assertNull(sections.get(0).structured());
        assertTrue(sections.get(0).text().contains("patates"));
    }

    @Test
    void rejectsNonHttpUrls() {
        assertThrows(IllegalArgumentException.class, () -> parser.fetch("ftp://example.com/x"));
        assertThrows(IllegalArgumentException.class, () -> parser.fetch("not a url"));
    }
}

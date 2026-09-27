package dev.agiro.fanel.assistant.infra.recipeimport;

import dev.agiro.fanel.assistant.domain.recipeimport.BookSection;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpubRecipeBookParserTest {

    private final EpubRecipeBookParser parser = new EpubRecipeBookParser();

    @Test
    void ncxTocEntriesBecomeCandidates() {
        byte[] epub = epubWithNcx();
        List<BookSection> sections = parser.parse(epub);
        assertEquals(2, sections.size());
        assertEquals("Arròs negre", sections.get(0).title());
        assertTrue(sections.get(0).text().contains("sípia"));
        assertEquals("Flam d'ou", sections.get(1).title());
    }

    @Test
    void invalidZipFails() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("no epub".getBytes()));
    }

    private byte[] epubWithNcx() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            put(zip, "OEBPS/content.opf", """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <manifest>
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                        <item id="r1" href="arros.xhtml" media-type="application/xhtml+xml"/>
                        <item id="r2" href="flam.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine toc="ncx"><itemref idref="r1"/><itemref idref="r2"/></spine>
                    </package>
                    """);
            put(zip, "OEBPS/toc.ncx", """
                    <?xml version="1.0"?>
                    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
                      <navMap>
                        <navPoint id="n1"><navLabel><text>Arròs negre</text></navLabel><content src="arros.xhtml"/></navPoint>
                        <navPoint id="n2"><navLabel><text>Flam d'ou</text></navLabel><content src="flam.xhtml"/></navPoint>
                      </navMap>
                    </ncx>
                    """);
            put(zip, "OEBPS/arros.xhtml", """
                    <html><body><h1>Arròs negre</h1>
                    <p>Ingredients: arròs, sípia, ceba, tomàquet i tinta de calamar.</p>
                    <p>Sofregir la sípia, afegir l'arròs i coure amb el brou de peix uns divuit minuts.</p></body></html>
                    """);
            put(zip, "OEBPS/flam.xhtml", """
                    <html><body><h1>Flam d'ou</h1>
                    <p>Ingredients: ous, llet, sucre i caramel líquid.</p>
                    <p>Batre els ous amb la llet, escumar i coure al bany maria quaranta minuts.</p></body></html>
                    """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return buffer.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

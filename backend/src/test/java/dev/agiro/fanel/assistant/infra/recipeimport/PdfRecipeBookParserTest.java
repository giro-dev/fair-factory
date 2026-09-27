package dev.agiro.fanel.assistant.infra.recipeimport;

import dev.agiro.fanel.assistant.domain.recipeimport.BookSection;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfRecipeBookParserTest {

    private final PdfRecipeBookParser parser = new PdfRecipeBookParser();

    @Test
    void outlineEntriesBecomeCandidates() throws Exception {
        byte[] pdf = pdfWithOutline();
        List<BookSection> sections = parser.parse(pdf);
        assertEquals(2, sections.size());
        assertEquals("Canelons", sections.get(0).title());
        assertTrue(sections.get(0).text().contains("carn picada"));
        assertEquals("Crema de carbassa", sections.get(1).title());
        assertTrue(sections.get(1).text().contains("carbassa"));
    }

    @Test
    void pagesBecomeCandidatesWithoutOutline() throws Exception {
        byte[] pdf = pdfWithoutOutline();
        List<BookSection> sections = parser.parse(pdf);
        assertEquals(2, sections.size());
        assertTrue(sections.get(0).title().contains("Truita de patates"));
    }

    private byte[] pdfWithOutline() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage p1 = page(doc, "Canelons\n\nIngredients: canelons, 400 g de carn picada, ceba i beixamel.\nFarcir els canelons, cobrir amb beixamel i gratinar al forn uns trenta minuts fins que quedin daurats.");
            PDPage p2 = page(doc, "Crema de carbassa\n\nIngredients: carbassa, ceba, patata i un raig de nata.\nCoure totes les verdures i triturar fins a obtenir una crema fina, suau i homogènia.");

            PDDocumentOutline outline = new PDDocumentOutline();
            doc.getDocumentCatalog().setDocumentOutline(outline);
            PDOutlineItem item1 = new PDOutlineItem();
            item1.setTitle("Canelons");
            PDPageDestination dest1 = new PDPageFitWidthDestination();
            dest1.setPage(p1);
            item1.setDestination(dest1);
            PDOutlineItem item2 = new PDOutlineItem();
            item2.setTitle("Crema de carbassa");
            PDPageDestination dest2 = new PDPageFitWidthDestination();
            dest2.setPage(p2);
            item2.setDestination(dest2);
            outline.addFirst(item1);
            outline.addLast(item2);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithoutOutline() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            page(doc, "Truita de patates\n\nBatre els ous, afegir la patata fregida i la ceba confitada, i quallar a la paella a foc lent fins que quedi jugosa per dins.");
            page(doc, "Croquetes\n\nFer una beixamel ben espessa amb el pernil, refredar la massa tota la nit, formar les croquetes i fregir-les en oli ben calent.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private PDPage page(PDDocument doc, String text) throws Exception {
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
            content.newLineAtOffset(50, 750);
            for (String line : text.split("\n")) {
                content.showText(line.isBlank() ? " " : line);
                content.newLineAtOffset(0, -16);
            }
            content.endText();
        }
        return page;
    }
}

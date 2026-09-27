package dev.agiro.fanel.assistant.infra.recipeimport;

import dev.agiro.fanel.assistant.domain.recipeimport.BookSection;
import dev.agiro.fanel.assistant.domain.recipeimport.RecipeBookParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts recipe candidates from a PDF. Uses the document outline (bookmarks/TOC) when
 * present — each entry becomes a candidate covering the pages up to the next entry;
 * otherwise falls back to one candidate per non-trivial page, titled by its first line.
 */
@Component
public class PdfRecipeBookParser implements RecipeBookParser {

    private static final int MIN_PAGE_TEXT = 120;
    private static final int TITLE_LENGTH = 80;

    @Override
    public boolean supports(String fileName, String contentType) {
        return "application/pdf".equalsIgnoreCase(contentType)
                || (fileName != null && fileName.toLowerCase().endsWith(".pdf"));
    }

    @Override
    public List<BookSection> parse(byte[] data) {
        try (PDDocument document = Loader.loadPDF(data)) {
            Map<String, Integer> outline = outlineEntries(document);
            return outline.isEmpty() ? sectionsFromPages(document) : sectionsFromOutline(document, outline);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the PDF file: " + e.getMessage(), e);
        }
    }

    private Map<String, Integer> outlineEntries(PDDocument document) throws IOException {
        Map<String, Integer> entries = new LinkedHashMap<>();
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline == null) {
            return entries;
        }
        collect(outline.getFirstChild(), document, entries);
        return entries;
    }

    private void collect(PDOutlineItem item, PDDocument document, Map<String, Integer> entries)
            throws IOException {
        while (item != null) {
            PDPage target = item.findDestinationPage(document);
            if (target != null && item.getTitle() != null && !item.getTitle().isBlank()) {
                int page = document.getPages().indexOf(target);
                if (page >= 0) {
                    entries.put(item.getTitle().trim(), page);
                }
            }
            if (item.hasChildren()) {
                collect(item.getFirstChild(), document, entries);
            }
            item = item.getNextSibling();
        }
    }

    private List<BookSection> sectionsFromOutline(PDDocument document, Map<String, Integer> outline)
            throws IOException {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(outline.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getValue));
        int pageCount = document.getNumberOfPages();
        PDFTextStripper stripper = new PDFTextStripper();
        List<BookSection> sections = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            int start = entries.get(i).getValue();
            int end = (i + 1 < entries.size() ? entries.get(i + 1).getValue() : pageCount) - 1;
            if (end < start) {
                end = start;
            }
            stripper.setStartPage(start + 1);
            stripper.setEndPage(end + 1);
            String text = stripper.getText(document).trim();
            if (text.length() < MIN_PAGE_TEXT) {
                continue;
            }
            sections.add(BookSection.unstructured("s" + i, entries.get(i).getKey(), text));
        }
        return sections;
    }

    private List<BookSection> sectionsFromPages(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        List<BookSection> sections = new ArrayList<>();
        for (int page = 0; page < document.getNumberOfPages(); page++) {
            stripper.setStartPage(page + 1);
            stripper.setEndPage(page + 1);
            String text = stripper.getText(document).trim();
            if (text.length() < MIN_PAGE_TEXT) {
                continue;
            }
            String title = text.lines().map(String::trim).filter(l -> !l.isBlank())
                    .findFirst().orElse("Page " + (page + 1));
            if (title.length() > TITLE_LENGTH) {
                title = title.substring(0, TITLE_LENGTH).trim();
            }
            sections.add(BookSection.unstructured("p" + page,
                    title + " (p. " + (page + 1) + ")", text));
        }
        return sections;
    }
}

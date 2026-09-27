package dev.agiro.fanel.assistant.infra.recipeimport;

import dev.agiro.fanel.assistant.domain.recipeimport.BookSection;
import dev.agiro.fanel.assistant.domain.recipeimport.RecipeBookParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts recipe candidates from an EPUB. Follows the navigation document (EPUB 3) or the
 * NCX (EPUB 2) so each table-of-contents entry becomes a candidate; falls back to one
 * candidate per spine document, titled by its first heading.
 */
@Component
public class EpubRecipeBookParser implements RecipeBookParser {

    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final int MIN_SECTION_TEXT = 120;

    @Override
    public boolean supports(String fileName, String contentType) {
        return "application/epub+zip".equalsIgnoreCase(contentType)
                || (fileName != null && fileName.toLowerCase().endsWith(".epub"));
    }

    @Override
    public List<BookSection> parse(byte[] data) {
        Map<String, String> files = unzip(data);
        String opfPath = files.keySet().stream()
                .filter(p -> p.endsWith(".opf"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The EPUB has no OPF manifest"));
        String opfDir = parentDir(opfPath);
        Document opf = Jsoup.parse(files.get(opfPath), "", Parser.xmlParser());

        List<BookSection> fromToc = sectionsFromToc(files, opf, opfDir);
        return fromToc.isEmpty() ? sectionsFromSpine(files, opf, opfDir) : fromToc;
    }

    private Map<String, String> unzip(byte[] data) {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getSize() > MAX_FILE_BYTES) {
                    continue;
                }
                String name = entry.getName();
                if (!name.endsWith(".opf") && !name.endsWith(".ncx")
                        && !name.endsWith(".xhtml") && !name.endsWith(".html")
                        && !name.endsWith(".htm") && !name.endsWith(".xml")) {
                    continue;
                }
                files.put(name, new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the EPUB file: " + e.getMessage(), e);
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("The EPUB file is empty or not a ZIP archive");
        }
        return files;
    }

    private List<BookSection> sectionsFromToc(Map<String, String> files, Document opf, String opfDir) {
        // EPUB 3: manifest item with properties="nav"; EPUB 2: NCX item
        Element navItem = opf.selectFirst("manifest item[properties~=nav]");
        Element ncxItem = opf.selectFirst("manifest item[media-type=application/x-dtbncx+xml]");
        List<TocEntry> toc = new ArrayList<>();
        if (navItem != null) {
            String navPath = resolve(opfDir, navItem.attr("href"));
            String navXml = files.get(navPath);
            if (navXml != null) {
                Document nav = Jsoup.parse(navXml, "", Parser.htmlParser());
                for (Element a : nav.select("nav a[href]")) {
                    toc.add(new TocEntry(a.text().trim(),
                            resolve(parentDir(navPath), a.attr("href"))));
                }
            }
        } else if (ncxItem != null) {
            String ncxPath = resolve(opfDir, ncxItem.attr("href"));
            String ncxXml = files.get(ncxPath);
            if (ncxXml != null) {
                Document ncx = Jsoup.parse(ncxXml, "", Parser.xmlParser());
                for (Element point : ncx.select("navPoint")) {
                    Element label = point.selectFirst("> navLabel > text");
                    Element content = point.selectFirst("> content");
                    if (label != null && content != null) {
                        toc.add(new TocEntry(label.text().trim(),
                                resolve(parentDir(ncxPath), content.attr("src"))));
                    }
                }
            }
        }
        // One section per target document, keeping the first TOC title found for it.
        Map<String, String> titleByFile = new LinkedHashMap<>();
        for (TocEntry entry : toc) {
            String file = stripFragment(entry.href());
            titleByFile.putIfAbsent(file, entry.title());
        }
        List<BookSection> sections = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> e : titleByFile.entrySet()) {
            String html = files.get(e.getKey());
            if (html == null) {
                continue;
            }
            String text = Jsoup.parse(html).text().trim();
            if (text.length() < MIN_SECTION_TEXT) {
                continue;
            }
            String title = e.getValue().isBlank() ? guessTitle(html, e.getKey()) : e.getValue();
            sections.add(BookSection.unstructured("s" + i++, title, text));
        }
        return sections;
    }

    private List<BookSection> sectionsFromSpine(Map<String, String> files, Document opf, String opfDir) {
        Map<String, String> manifest = new LinkedHashMap<>();
        for (Element item : opf.select("manifest item")) {
            manifest.put(item.attr("id"), resolve(opfDir, item.attr("href")));
        }
        List<BookSection> sections = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        int i = 0;
        for (Element ref : opf.select("spine itemref")) {
            String path = manifest.get(ref.attr("idref"));
            if (path == null || !seen.add(path)) {
                continue;
            }
            String html = files.get(path);
            if (html == null) {
                continue;
            }
            String text = Jsoup.parse(html).text().trim();
            if (text.length() < MIN_SECTION_TEXT) {
                continue;
            }
            sections.add(BookSection.unstructured("d" + i++, guessTitle(html, path), text));
        }
        return sections;
    }

    private static String guessTitle(String html, String fallback) {
        Element heading = Jsoup.parse(html).selectFirst("h1, h2, h3, title");
        if (heading != null && !heading.text().isBlank()) {
            return heading.text().trim();
        }
        String name = fallback.substring(fallback.lastIndexOf('/') + 1);
        return name.replaceAll("\\.[^.]+$", "");
    }

    private static String resolve(String baseDir, String href) {
        String decoded = URLDecoder.decode(href, StandardCharsets.UTF_8);
        String path = decoded.startsWith("/") ? decoded.substring(1) : baseDir + decoded;
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
            } else if (!part.equals(".") && !part.isEmpty()) {
                parts.add(part);
            }
        }
        return String.join("/", parts);
    }

    private static String stripFragment(String href) {
        int hash = href.indexOf('#');
        return hash >= 0 ? href.substring(0, hash) : href;
    }

    private static String parentDir(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    private record TocEntry(String title, String href) {
    }
}

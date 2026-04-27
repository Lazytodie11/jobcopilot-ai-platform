package com.yipeng.jobcopilot.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Extracts plain text from PDF files using Apache PDFBox.
 * Used as the first step in the PDF resume upload flow.
 *
 * Quality checks performed after extraction:
 *   - Minimum content length (200 chars) — catches scanned/image PDFs
 *   - Warns if content seems suspiciously short relative to page count
 */
@Slf4j
@Service
public class PdfParserService {

    // A real resume should have at least this many characters.
    // Anything below this almost certainly means the PDF is image-based or corrupt.
    private static final int MIN_CONTENT_LENGTH = 200;

    // If average chars per page is below this, warn the user.
    private static final int MIN_CHARS_PER_PAGE = 100;

    /**
     * Extracts all text from the given PDF file.
     *
     * @param file the uploaded PDF
     * @return extracted plain text (always non-null, non-blank)
     * @throws IllegalArgumentException if the file is invalid, empty, or a scanned PDF
     * @throws RuntimeException         if PDFBox fails to parse the file
     */
    public String extractText(MultipartFile file) {
        validateFile(file);

        try {
            byte[] bytes = file.getBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {

                int pageCount = document.getNumberOfPages();
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document).trim();
                int charCount = text.length();

                log.info("PDF parsed: {} pages, {} characters extracted", pageCount, charCount);

                // ── Quality check 1: absolute minimum ─────────────────────────
                // If we got less than MIN_CONTENT_LENGTH characters total,
                // the PDF is almost certainly a scanned image or has encoding issues.
                if (charCount < MIN_CONTENT_LENGTH) {
                    log.error("PDF extraction quality check failed: only {} characters extracted " +
                            "from {} pages. PDF may be scanned/image-based.", charCount, pageCount);
                    throw new IllegalArgumentException(
                            "PDF appears to be a scanned image or has unreadable content. " +
                                    "Only " + charCount + " characters were extracted from " + pageCount +
                                    " page(s). Please upload a text-based PDF (not a scanned image)."
                    );
                }

                // ── Quality check 2: chars per page ───────────────────────────
                // Warn if content density is low — might indicate partial extraction.
                // This is non-fatal: we log a warning but still proceed.
                if (pageCount > 0) {
                    int avgCharsPerPage = charCount / pageCount;
                    if (avgCharsPerPage < MIN_CHARS_PER_PAGE) {
                        log.warn("PDF extraction quality warning: only {} avg chars/page " +
                                        "({} chars across {} pages). Content may be incomplete.",
                                avgCharsPerPage, charCount, pageCount);
                    }
                }

                return text;
            }
        } catch (IllegalArgumentException e) {
            // Re-throw our own validation exceptions unchanged
            throw e;
        } catch (IOException e) {
            log.error("Failed to parse PDF file: {}", e.getMessage());
            throw new RuntimeException("Failed to parse PDF: " + e.getMessage(), e);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file must not be empty");
        }

        String contentType = file.getContentType();
        String filename    = file.getOriginalFilename();

        boolean isPdf = "application/pdf".equals(contentType)
                || (filename != null && filename.toLowerCase().endsWith(".pdf"));

        if (!isPdf) {
            throw new IllegalArgumentException(
                    "Only PDF files are supported. Received: " + contentType);
        }
    }
}
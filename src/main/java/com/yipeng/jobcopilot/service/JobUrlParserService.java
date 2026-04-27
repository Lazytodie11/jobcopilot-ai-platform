package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.dto.ParsedJobPostResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class JobUrlParserService {

    private static final int MAX_DESCRIPTION_LENGTH = 8000;

    private static final List<String> START_KEYWORDS = List.of(
            "about the company",
            "about the role",
            "about this role",
            "job description",
            "the role",
            "role overview",
            "responsibilities",
            "what you will do",
            "what you'll do",
            "you will"
    );

    private static final List<String> STOP_KEYWORDS = List.of(
            "interested in building your career",
            "create a job alert",
            "linkedin profile",
            "website",
            "what is your gpa",
            "do you know anyone currently",
            "are you willing",
            "will you require",
            "work authorization",
            "sponsorship",
            "how did you hear",
            "submit application",
            "apply now",
            "voluntary self-identification",
            "self-identification",
            "disabled veteran",
            "protected veteran",
            "veteran status",
            "disability status",
            "eeo",
            "ofccp",
            "form cc-305",
            "privacy policy",
            "terms of use"
    );

    public ParsedJobPostResponse parseJobUrl(String url) {
        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            String pageTitle = cleanText(getBestPageTitle(document));
            String jobTitle = guessJobTitle(pageTitle);
            String companyName = guessCompanyName(pageTitle, url);

            String fullText = cleanText(document.body().text());
            String description = extractDescriptionFromFullText(fullText, companyName);

            if (description.isBlank()) {
                description = extractRelevantDescription(document, companyName);
            }

            if (description.isBlank()) {
                description = removeFormNoise(fullText);
            }

            return ParsedJobPostResponse.builder()
                    .url(url)
                    .pageTitle(pageTitle)
                    .companyName(companyName)
                    .jobTitle(jobTitle)
                    .description(limitLength(description))
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse job URL: " + e.getMessage());
        }
    }

    private String extractDescriptionFromFullText(String fullText, String companyName) {
        if (fullText == null || fullText.isBlank()) {
            return "";
        }

        String lower = fullText.toLowerCase();

        int startIndex = findBestStartIndex(lower, companyName);
        int endIndex = findFirstStopIndex(lower, startIndex);

        if (startIndex < 0) {
            startIndex = 0;
        }

        if (endIndex < 0 || endIndex <= startIndex) {
            endIndex = fullText.length();
        }

        String sliced = cleanText(fullText.substring(startIndex, endIndex));
        sliced = removeRepeatedSentences(sliced);
        sliced = removeFormNoise(sliced);

        return limitLength(sliced);
    }

    private int findBestStartIndex(String lowerText, String companyName) {
        int best = -1;

        if (companyName != null && !companyName.isBlank() && !companyName.equals("Unknown Company")) {
            String aboutCompany = "about " + companyName.toLowerCase();
            int index = lowerText.indexOf(aboutCompany);
            if (index >= 0) {
                best = index;
            }
        }

        for (String keyword : START_KEYWORDS) {
            int index = lowerText.indexOf(keyword);
            if (index >= 0 && (best == -1 || index < best)) {
                best = index;
            }
        }

        return best;
    }

    private int findFirstStopIndex(String lowerText, int startIndex) {
        int best = -1;

        int from = Math.max(startIndex, 0);

        for (String keyword : STOP_KEYWORDS) {
            int index = lowerText.indexOf(keyword, from);
            if (index >= 0 && (best == -1 || index < best)) {
                best = index;
            }
        }

        return best;
    }

    private String extractRelevantDescription(Document document, String companyName) {
        List<String> sections = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Element element : document.select("h1, h2, h3, p, li")) {
            String text = cleanText(element.text());

            if (!isUsefulText(text)) {
                continue;
            }

            if (shouldStop(text)) {
                break;
            }

            if (containsJdSignal(text) || containsCompanyIntro(text, companyName)) {
                String normalized = normalizeForDedup(text);

                if (!seen.contains(normalized)) {
                    seen.add(normalized);
                    sections.add(text);
                }
            }
        }

        String combined = cleanText(String.join(" ", sections));
        combined = removeRepeatedSentences(combined);
        combined = removeFormNoise(combined);

        return limitLength(combined);
    }

    private boolean containsJdSignal(String text) {
        String lower = text.toLowerCase();

        return lower.contains("responsibilities")
                || lower.contains("requirements")
                || lower.contains("qualifications")
                || lower.contains("experience")
                || lower.contains("skills")
                || lower.contains("about the role")
                || lower.contains("what you will do")
                || lower.contains("what you'll do")
                || lower.contains("you will")
                || lower.contains("about you")
                || lower.contains("minimum qualifications")
                || lower.contains("preferred qualifications")
                || lower.contains("software engineer")
                || lower.contains("backend")
                || lower.contains("distributed systems")
                || lower.contains("machine learning")
                || lower.contains("data structures")
                || lower.contains("algorithms");
    }

    private boolean isUsefulText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        int length = text.length();

        if (length < 20) {
            return false;
        }

        if (length > 2500) {
            return false;
        }

        String lower = text.toLowerCase();

        return !lower.contains("cookie")
                && !lower.contains("privacy policy")
                && !lower.contains("terms of use")
                && !lower.contains("sign in")
                && !lower.contains("subscribe")
                && !lower.contains("newsletter")
                && !lower.contains("recaptcha")
                && !lower.contains("captcha");
    }

    private boolean containsCompanyIntro(String text, String companyName) {
        if (companyName == null || companyName.isBlank() || companyName.equals("Unknown Company")) {
            return false;
        }

        String lower = text.toLowerCase();
        String lowerCompany = companyName.toLowerCase();

        return lower.contains(lowerCompany)
                && (lower.contains("platform")
                || lower.contains("company")
                || lower.contains("product")
                || lower.contains("customers")
                || lower.contains("enterprise")
                || lower.contains("ai"));
    }

    private boolean shouldStop(String text) {
        String lower = text.toLowerCase();

        for (String keyword : STOP_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private String removeFormNoise(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String lower = text.toLowerCase();
        int cutIndex = -1;

        for (String keyword : STOP_KEYWORDS) {
            int index = lower.indexOf(keyword);
            if (index >= 0 && (cutIndex == -1 || index < cutIndex)) {
                cutIndex = index;
            }
        }

        if (cutIndex >= 0) {
            return cleanText(text.substring(0, cutIndex));
        }

        return cleanText(text);
    }

    private String removeRepeatedSentences(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String[] parts = text.split("(?<=[.!?])\\s+");
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();

        for (String part : parts) {
            String cleaned = cleanText(part);
            String normalized = normalizeForDedup(cleaned);

            if (!cleaned.isBlank() && !seen.contains(normalized)) {
                seen.add(normalized);
                result.add(cleaned);
            }
        }

        return cleanText(String.join(" ", result));
    }

    private String normalizeForDedup(String text) {
        return cleanText(text)
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .trim();
    }

    private String getBestPageTitle(Document document) {
        String ogTitle = getMetaContent(document, "meta[property=og:title]");
        if (!ogTitle.isBlank()) {
            return ogTitle;
        }

        String twitterTitle = getMetaContent(document, "meta[name=twitter:title]");
        if (!twitterTitle.isBlank()) {
            return twitterTitle;
        }

        return document.title();
    }

    private String getMetaContent(Document document, String selector) {
        Element element = document.selectFirst(selector);
        if (element == null) {
            return "";
        }
        return cleanText(element.attr("content"));
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }

        return text.replaceAll("\\s+", " ").trim();
    }

    private String limitLength(String text) {
        if (text == null) {
            return "";
        }

        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            return text.substring(0, MAX_DESCRIPTION_LENGTH);
        }

        return text;
    }

    private String guessJobTitle(String pageTitle) {
        if (pageTitle == null || pageTitle.isBlank()) {
            return "Unknown Job Title";
        }

        String lower = pageTitle.toLowerCase();

        if (lower.startsWith("job application for ") && lower.contains(" at ")) {
            String cleaned = pageTitle.substring("Job Application for ".length());
            int atIndex = cleaned.toLowerCase().lastIndexOf(" at ");
            if (atIndex > 0) {
                return cleaned.substring(0, atIndex).trim();
            }
        }

        String[] separators = {" | ", " - ", " · ", " – "};

        for (String separator : separators) {
            if (pageTitle.contains(separator)) {
                return pageTitle.split(java.util.regex.Pattern.quote(separator))[0].trim();
            }
        }

        return pageTitle.trim();
    }

    private String guessCompanyName(String pageTitle, String url) {
        if (pageTitle != null && !pageTitle.isBlank()) {
            String lower = pageTitle.toLowerCase();

            if (lower.startsWith("job application for ") && lower.contains(" at ")) {
                int atIndex = lower.lastIndexOf(" at ");
                if (atIndex >= 0) {
                    return pageTitle.substring(atIndex + 4).trim();
                }
            }

            if (lower.contains(" at ")) {
                int atIndex = lower.lastIndexOf(" at ");
                return pageTitle.substring(atIndex + 4).trim();
            }

            String[] separators = {" | ", " - ", " · ", " – "};

            for (String separator : separators) {
                if (pageTitle.contains(separator)) {
                    String[] parts = pageTitle.split(java.util.regex.Pattern.quote(separator));
                    if (parts.length >= 2) {
                        return parts[parts.length - 1].trim();
                    }
                }
            }
        }

        String companyFromUrl = guessCompanyFromUrl(url);
        if (!companyFromUrl.isBlank()) {
            return companyFromUrl;
        }

        return "Unknown Company";
    }

    private String guessCompanyFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();

            if (host == null) {
                return "";
            }

            if (host.contains("greenhouse.io") && path != null) {
                String[] parts = path.split("/");
                for (String part : parts) {
                    if (!part.isBlank()
                            && !part.equalsIgnoreCase("jobs")
                            && !part.matches("\\d+")) {
                        return prettifyCompanyName(part);
                    }
                }
            }

            String[] hostParts = host.replace("www.", "").split("\\.");
            if (hostParts.length > 0) {
                return prettifyCompanyName(hostParts[0]);
            }

            return "";

        } catch (Exception e) {
            return "";
        }
    }

    private String prettifyCompanyName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String cleaned = raw
                .replace("-", " ")
                .replace("_", " ")
                .replaceAll("(?i)work$", "")
                .replaceAll("(?i)jobs$", "")
                .trim();

        if (cleaned.isBlank()) {
            return "";
        }

        String[] words = cleaned.split("\\s+");
        List<String> prettyWords = new ArrayList<>();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            prettyWords.add(word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase());
        }

        return String.join(" ", prettyWords);
    }
}
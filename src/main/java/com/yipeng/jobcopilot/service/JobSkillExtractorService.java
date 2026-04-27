package com.yipeng.jobcopilot.service;

import com.yipeng.jobcopilot.enumeration.SkillTier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Extracts technology skills from free text and exposes their tier weights.
 *
 * Registry design:
 *   CORE (weight 3)         — primary languages / make-or-break skills
 *   IMPORTANT (weight 2)    — frameworks, databases, infra commonly listed as required
 *   NICE_TO_HAVE (weight 1) — supporting tools; absence is not disqualifying
 *
 * Matching strategy:
 *   Uses regex with smart boundary detection instead of String.contains().
 *   - Skills starting/ending with a word character use \b (standard word boundary)
 *   - Skills ending with a non-word character (e.g. C++, C#) use (?!\w) lookahead
 *   This prevents false positives like "Scala" in "scalable", "Rust" in "trust",
 *   while correctly matching "C++" before ")" or end of line.
 */
@Service
public class JobSkillExtractorService {

    private static final Map<String, SkillTier> SKILL_REGISTRY = new LinkedHashMap<>();
    private static final Map<String, Pattern>   SKILL_PATTERNS  = new ConcurrentHashMap<>();

    static {
        // ── CORE: primary programming languages ───────────────────────────────
        SKILL_REGISTRY.put("Java",       SkillTier.CORE);
        SKILL_REGISTRY.put("Python",     SkillTier.CORE);
        SKILL_REGISTRY.put("JavaScript", SkillTier.CORE);
        SKILL_REGISTRY.put("TypeScript", SkillTier.CORE);
        SKILL_REGISTRY.put("Go",         SkillTier.CORE);
        SKILL_REGISTRY.put("C++",        SkillTier.CORE);
        SKILL_REGISTRY.put("Kotlin",     SkillTier.CORE);
        SKILL_REGISTRY.put("Scala",      SkillTier.CORE);
        SKILL_REGISTRY.put("Rust",       SkillTier.CORE);
        SKILL_REGISTRY.put("C#",         SkillTier.CORE);

        // ── IMPORTANT: frameworks, databases, infra ───────────────────────────
        SKILL_REGISTRY.put("Spring Boot", SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Spring",      SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Django",      SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Flask",       SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("FastAPI",     SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Node.js",     SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Express",     SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("React",       SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Vue",         SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Angular",     SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("PostgreSQL",  SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("MySQL",       SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("SQL",         SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("MongoDB",     SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Redis",       SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Elasticsearch", SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Kafka",       SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("RabbitMQ",    SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Docker",      SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Kubernetes",  SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("AWS",         SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("GCP",         SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("Azure",       SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("REST",        SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("GraphQL",     SkillTier.IMPORTANT);
        SKILL_REGISTRY.put("gRPC",        SkillTier.IMPORTANT);

        // ── NICE_TO_HAVE: supporting tools and libraries ──────────────────────
        SKILL_REGISTRY.put("JPA",            SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("Hibernate",      SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("JWT",            SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("OAuth2",         SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("Maven",          SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("Gradle",         SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("Git",            SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("Jenkins",        SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("GitHub Actions", SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("CI/CD",          SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("Terraform",      SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("Ansible",        SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("Spark",          SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("Airflow",        SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("PyTorch",        SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("TensorFlow",     SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("scikit-learn",   SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("Pandas",         SkillTier.NICE_TO_HAVE);
        SKILL_REGISTRY.put("NumPy",          SkillTier.NICE_TO_HAVE);

        // Pre-compile all patterns using smart boundary detection
        for (String skill : SKILL_REGISTRY.keySet()) {
            SKILL_PATTERNS.put(skill, buildPattern(skill));
        }
    }

    /**
     * Builds a regex pattern for a skill with appropriate boundary anchors.
     *
     * Standard \b only works when both sides of the boundary involve a word character.
     * For skills ending in non-word characters (C++, C#), \b after the last char fails
     * because the char before and after are both non-word. We use (?!\w) lookahead instead.
     *
     * Examples:
     *   "Java"  → \bJava\b          (both ends are word chars)
     *   "C++"   → \bC\+\+(?!\w)    (ends with +, a non-word char)
     *   "C#"    → \bC\#(?!\w)      (ends with #, a non-word char)
     */
    private static Pattern buildPattern(String skill) {
        char first = skill.charAt(0);
        char last  = skill.charAt(skill.length() - 1);

        String leading  = Character.isLetterOrDigit(first) || first == '_' ? "\\b" : "(?<!\\w)";
        String trailing = Character.isLetterOrDigit(last)  || last  == '_' ? "\\b" : "(?!\\w)";

        return Pattern.compile(
                leading + Pattern.quote(skill) + trailing,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
        );
    }

    /**
     * Extracts all skills found in the given text using whole-word matching.
     */
    public Set<String> extractSkills(String text) {
        Set<String> found = new LinkedHashSet<>();

        if (text == null || text.isBlank()) {
            return found;
        }

        for (Map.Entry<String, Pattern> entry : SKILL_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(text).find()) {
                found.add(entry.getKey());
            }
        }

        return found;
    }

    /**
     * Returns the SkillTier for a known skill, or NICE_TO_HAVE as a safe default.
     */
    public SkillTier getTier(String skill) {
        return SKILL_REGISTRY.getOrDefault(skill, SkillTier.NICE_TO_HAVE);
    }

    /**
     * Returns the numeric weight for a skill (1, 2, or 3).
     */
    public int getWeight(String skill) {
        return getTier(skill).getWeight();
    }

    /**
     * Sums the weights of every skill in the given set.
     */
    public int totalWeight(Set<String> skills) {
        return skills.stream()
                .mapToInt(this::getWeight)
                .sum();
    }
}
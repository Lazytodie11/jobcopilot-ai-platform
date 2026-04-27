package com.yipeng.jobcopilot.enumeration;

/**
 * Skill importance tiers for weighted match scoring.
 * CORE = make-or-break skills (e.g. Java for a Java backend role)
 * IMPORTANT = commonly required skills that meaningfully affect fit
 * NICE_TO_HAVE = supporting tools — absence is not disqualifying
 */
public enum SkillTier {

    CORE(3),
    IMPORTANT(2),
    NICE_TO_HAVE(1);

    private final int weight;

    SkillTier(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
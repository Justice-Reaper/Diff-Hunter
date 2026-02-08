package org.diffhunter.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds exclusion rules for a specific target (host + endpoint).
 */
public class TargetExclusions {

    private final List<ExclusionRule> requestExclusions = new ArrayList<>();
    private final List<ExclusionRule> responseExclusions = new ArrayList<>();

    /**
     * Returns the list of request exclusion rules.
     */
    public List<ExclusionRule> getRequestExclusions() {
        return requestExclusions;
    }

    /**
     * Returns the list of response exclusion rules.
     */
    public List<ExclusionRule> getResponseExclusions() {
        return responseExclusions;
    }

    /**
     * Adds a request exclusion rule.
     */
    public void addRequestExclusion(ExclusionRule rule) {
        requestExclusions.add(rule);
    }

    /**
     * Adds a response exclusion rule.
     */
    public void addResponseExclusion(ExclusionRule rule) {
        responseExclusions.add(rule);
    }

    /**
     * Removes a request exclusion rule.
     */
    public void removeRequestExclusion(ExclusionRule rule) {
        requestExclusions.remove(rule);
    }

    /**
     * Removes a response exclusion rule.
     */
    public void removeResponseExclusion(ExclusionRule rule) {
        responseExclusions.remove(rule);
    }

    /**
     * Checks if the given text matches any enabled request exclusion.
     */
    public boolean matchesRequestExclusion(String text) {
        for (ExclusionRule rule : requestExclusions) {
            if (rule.matches(text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given text matches any enabled response exclusion.
     */
    public boolean matchesResponseExclusion(String text) {
        for (ExclusionRule rule : responseExclusions) {
            if (rule.matches(text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if there are any enabled request exclusions.
     */
    public boolean hasEnabledRequestExclusions() {
        for (ExclusionRule rule : requestExclusions) {
            if (rule.isEnabled() && rule.isValid()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if there are any enabled response exclusions.
     */
    public boolean hasEnabledResponseExclusions() {
        for (ExclusionRule rule : responseExclusions) {
            if (rule.isEnabled() && rule.isValid()) {
                return true;
            }
        }
        return false;
    }
}

package org.diffhunter.model;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Represents an exclusion rule with a regex pattern.
 */
public class ExclusionRule {

    private String regex;
    private Pattern compiledPattern;
    private boolean enabled;
    private boolean valid;

    /**
     * Creates a new exclusion rule with the specified regex.
     */
    public ExclusionRule(String regex) {
        this.regex = regex;
        this.enabled = true;
        compilePattern();
    }

    /**
     * Compiles the regex pattern and sets validity flag.
     */
    private void compilePattern() {
        try {
            this.compiledPattern = Pattern.compile(regex);
            this.valid = true;
        } catch (PatternSyntaxException e) {
            this.compiledPattern = null;
            this.valid = false;
        }
    }

    /**
     * Returns the regex string.
     */
    public String getRegex() {
        return regex;
    }

    /**
     * Sets the regex string and recompiles the pattern.
     */
    public void setRegex(String regex) {
        this.regex = regex;
        compilePattern();
    }

    /**
     * Returns true if this rule is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether this rule is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns true if the regex pattern is valid.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Returns true if the given text matches this exclusion rule.
     * Returns false if the rule is disabled or invalid.
     */
    public boolean matches(String text) {
        if (!enabled || !valid || compiledPattern == null) {
            return false;
        }
        return compiledPattern.matcher(text).find();
    }
}

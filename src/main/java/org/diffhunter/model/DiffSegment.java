package org.diffhunter.model;

import org.diffhunter.util.Constants;
import java.awt.Color;

/**
 * Represents a difference segment between two texts.
 */
public class DiffSegment {

    private final int startOffset;
    private final int endOffset;
    private final String content;
    private final boolean isOriginal;
    private final DiffType type;

    /**
     * Creates a new DiffSegment with the specified parameters.
     */
    public DiffSegment(int startOffset, int endOffset, String content, boolean isOriginal, DiffType type) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.content = content;
        this.isOriginal = isOriginal;
        this.type = type;
    }

    /**
     * Returns the start offset of the difference in the text.
     */
    public int getStartOffset() {
        return startOffset;
    }

    /**
     * Returns the end offset of the difference in the text.
     */
    public int getEndOffset() {
        return endOffset;
    }

    /**
     * Returns the content of the difference.
     */
    public String getContent() {
        return content;
    }

    /**
     * Returns true if this difference is from the original text, false if from modified.
     */
    public boolean isOriginal() {
        return isOriginal;
    }

    /**
     * Returns the type of this difference (DELETED, ADDED, or MODIFIED).
     */
    public DiffType getType() {
        return type;
    }

    /**
     * Returns the appropriate color for this diff type based on the theme.
     */
    public Color getColor(boolean isDarkTheme) {
        return switch (type) {
            case DELETED -> isDarkTheme ? Constants.COLOR_DELETED_REQUEST_DARK : Constants.COLOR_DELETED_REQUEST_LIGHT;
            case ADDED -> isDarkTheme ? Constants.COLOR_ADDED_BOTH_DARK : Constants.COLOR_ADDED_BOTH_LIGHT;
            case MODIFIED -> isDarkTheme ? Constants.COLOR_MODIFIED_RESPONSE_DARK : Constants.COLOR_MODIFIED_RESPONSE_LIGHT;
        };
    }

    /**
     * Returns a string representation of this diff segment for display in tables.
     */
    @Override
    public String toString() {
        String typeStr = switch (type) {
            case DELETED -> "Deleted";
            case ADDED -> "Added";
            case MODIFIED -> "Modified";
        };

        String panelStr = isOriginal ? "Target" : "Selected";
        String prefix = "[" + panelStr + " - " + typeStr + "] ";

        if (content.length() > 50) {
            return prefix + content.substring(0, 50) + "... (" + content.length() + " chars)";
        }

        return prefix + content;
    }
}

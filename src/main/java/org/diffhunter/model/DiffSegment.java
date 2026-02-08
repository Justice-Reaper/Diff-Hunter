package org.diffhunter.model;

/**
 * Represents a difference segment between two texts.
 */
public class DiffSegment {

    private final int startOffset;
    private final int endOffset;
    private final String content;
    private final boolean isOriginal;
    private final DiffType type;
    private int parentLineIndex = -1;
    private int lineNumber = -1;

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
     * Creates a new DiffSegment with the specified parameters and parent line index.
     */
    public DiffSegment(int startOffset, int endOffset, String content, boolean isOriginal, DiffType type, int parentLineIndex) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.content = content;
        this.isOriginal = isOriginal;
        this.type = type;
        this.parentLineIndex = parentLineIndex;
    }

    /**
     * Creates a new DiffSegment with all parameters including line number.
     */
    public DiffSegment(int startOffset, int endOffset, String content, boolean isOriginal, DiffType type, int parentLineIndex, int lineNumber) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.content = content;
        this.isOriginal = isOriginal;
        this.type = type;
        this.parentLineIndex = parentLineIndex;
        this.lineNumber = lineNumber;
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
     * Returns the parent line index for character-level diffs, or -1 for line-level diffs.
     */
    public int getParentLineIndex() {
        return parentLineIndex;
    }

    /**
     * Returns the line number in the original text where this diff occurs.
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Returns the full content of this diff segment (not truncated).
     */
    public String getContent() {
        return content;
    }

    /**
     * Returns the status string for this diff segment (Deleted, Added, or Modified).
     */
    public String getStatusString() {
        return switch (type) {
            case DELETED -> "Deleted";
            case ADDED -> "Added";
            case MODIFIED -> "Modified";
        };
    }

    /**
     * Returns a string representation of this diff segment for display in tables.
     */
    @Override
    public String toString() {
        if (content.length() > 50) {
            return content.substring(0, 50) + "... (" + content.length() + " chars)";
        }
        return content;
    }
}

package org.diffhunter.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import org.diffhunter.model.DiffSegment;
import org.diffhunter.model.DiffType;
import org.diffhunter.model.HttpLogEntry;
import org.diffhunter.model.RowDiffType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Calculates differences between two texts using line and character level comparison.
 */
public class DiffCalculator {

    /**
     * Finds differences between original and modified text.
     */
    public List<DiffSegment> findDifferences(String original, String modified) {
        List<DiffSegment> diffs = new ArrayList<>();

        try {
            List<String> originalLines = Arrays.asList(original.split("\n", -1));
            List<String> modifiedLines = Arrays.asList(modified.split("\n", -1));

            Patch<String> patch = DiffUtils.diff(originalLines, modifiedLines);

            for (AbstractDelta<String> delta : patch.getDeltas()) {
                int sourcePosition = delta.getSource().getPosition();
                int targetPosition = delta.getTarget().getPosition();

                int originalCharOffset = calculateCharOffset(originalLines, sourcePosition);
                int modifiedCharOffset = calculateCharOffset(modifiedLines, targetPosition);

                switch (delta.getType()) {
                    case DELETE -> {
                        for (String line : delta.getSource().getLines()) {
                            int lineLen = line.length();
                            diffs.add(new DiffSegment(originalCharOffset, originalCharOffset + lineLen,
                                    line, true, DiffType.DELETED));
                            originalCharOffset += lineLen + 1;
                        }
                    }
                    case INSERT -> {
                        for (String line : delta.getTarget().getLines()) {
                            int lineLen = line.length();
                            diffs.add(new DiffSegment(modifiedCharOffset, modifiedCharOffset + lineLen,
                                    line, false, DiffType.ADDED));
                            modifiedCharOffset += lineLen + 1;
                        }
                    }
                    case CHANGE -> processChangedLines(delta, originalCharOffset, modifiedCharOffset, diffs);
                    default -> {}
                }
            }
        } catch (Exception e) {
            diffs.clear();
        }

        return diffs;
    }

    /**
     * Processes lines that have been modified, finding character-level differences.
     */
    private void processChangedLines(AbstractDelta<String> delta, int originalCharOffset,
                                     int modifiedCharOffset, List<DiffSegment> diffs) {
        List<String> sourceLines = delta.getSource().getLines();
        List<String> targetLines = delta.getTarget().getLines();

        int srcOffset = originalCharOffset;
        int tgtOffset = modifiedCharOffset;

        int maxLines = Math.max(sourceLines.size(), targetLines.size());
        int srcIdx = 0;
        int tgtIdx = 0;

        for (int i = 0; i < maxLines; i++) {
            String srcLine = srcIdx < sourceLines.size() ? sourceLines.get(srcIdx) : null;
            String tgtLine = tgtIdx < targetLines.size() ? targetLines.get(tgtIdx) : null;

            if (srcLine != null && tgtLine != null) {
                findCharacterDifferences(srcLine, tgtLine, srcOffset, tgtOffset, diffs);
                srcOffset += srcLine.length() + 1;
                tgtOffset += tgtLine.length() + 1;
                srcIdx++;
                tgtIdx++;
            } else if (srcLine != null) {
                diffs.add(new DiffSegment(srcOffset, srcOffset + srcLine.length(),
                        srcLine, true, DiffType.DELETED));
                srcOffset += srcLine.length() + 1;
                srcIdx++;
            } else if (tgtLine != null) {
                diffs.add(new DiffSegment(tgtOffset, tgtOffset + tgtLine.length(),
                        tgtLine, false, DiffType.ADDED));
                tgtOffset += tgtLine.length() + 1;
                tgtIdx++;
            }
        }
    }

    /**
     * Finds character-level differences between two lines.
     */
    private void findCharacterDifferences(String original, String modified,
                                          int originalBaseOffset, int modifiedBaseOffset,
                                          List<DiffSegment> diffs) {
        List<String> originalChars = new ArrayList<>();
        for (char c : original.toCharArray()) {
            originalChars.add(String.valueOf(c));
        }

        List<String> modifiedChars = new ArrayList<>();
        for (char c : modified.toCharArray()) {
            modifiedChars.add(String.valueOf(c));
        }

        Patch<String> charPatch = DiffUtils.diff(originalChars, modifiedChars);

        if (charPatch.getDeltas().isEmpty()) {
            return;
        }

        for (AbstractDelta<String> charDelta : charPatch.getDeltas()) {
            int sourcePos = charDelta.getSource().getPosition();
            int targetPos = charDelta.getTarget().getPosition();
            int sourceSize = charDelta.getSource().size();
            int targetSize = charDelta.getTarget().size();

            if (sourceSize > 0) {
                String chars = String.join("", charDelta.getSource().getLines());
                diffs.add(new DiffSegment(
                        originalBaseOffset + sourcePos,
                        originalBaseOffset + sourcePos + sourceSize,
                        chars, true, DiffType.MODIFIED));
            }

            if (targetSize > 0) {
                String chars = String.join("", charDelta.getTarget().getLines());
                diffs.add(new DiffSegment(
                        modifiedBaseOffset + targetPos,
                        modifiedBaseOffset + targetPos + targetSize,
                        chars, false, DiffType.MODIFIED));
            }
        }
    }

    /**
     * Calculates the character offset for a given line position.
     */
    private int calculateCharOffset(List<String> lines, int linePosition) {
        int offset = 0;
        for (int i = 0; i < linePosition && i < lines.size(); i++) {
            offset += lines.get(i).length() + 1;
        }
        return offset;
    }

    /**
     * Checks if an entry differs from the target entry.
     */
    public boolean differsFromTarget(HttpLogEntry target, HttpLogEntry entry) {
        return getDiffType(target, entry, true, true) != RowDiffType.NONE;
    }

    /**
     * Checks if an entry differs from the target entry, with options to check requests and/or responses.
     */
    public boolean differsFromTarget(HttpLogEntry target, HttpLogEntry entry,
                                      boolean checkRequests, boolean checkResponses) {
        return getDiffType(target, entry, checkRequests, checkResponses) != RowDiffType.NONE;
    }

    /**
     * Gets the type of difference between target and entry.
     */
    public RowDiffType getDiffType(HttpLogEntry target, HttpLogEntry entry,
                                    boolean checkRequests, boolean checkResponses) {
        if (target.getNumber() == entry.getNumber()) {
            return RowDiffType.NONE;
        }

        if (!checkRequests && !checkResponses) {
            return RowDiffType.NONE;
        }

        boolean requestDiffers = false;
        boolean responseDiffers = false;

        if (checkRequests) {
            if (target.getRequestStr().length() != entry.getRequestStr().length()) {
                requestDiffers = true;
            } else {
                requestDiffers = !target.getRequestStr().equals(entry.getRequestStr());
            }
        }

        if (checkResponses) {
            if (target.getResponseStr().length() != entry.getResponseStr().length()) {
                responseDiffers = true;
            } else {
                responseDiffers = !target.getResponseStr().equals(entry.getResponseStr());
            }
        }

        if (requestDiffers && responseDiffers) {
            return RowDiffType.BOTH;
        } else if (requestDiffers) {
            return RowDiffType.REQUEST_ONLY;
        } else if (responseDiffers) {
            return RowDiffType.RESPONSE_ONLY;
        }
        return RowDiffType.NONE;
    }
}

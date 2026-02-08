package org.diffhunter.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import org.diffhunter.model.DiffSegment;
import org.diffhunter.model.DiffType;
import org.diffhunter.model.HttpLogEntry;
import org.diffhunter.model.RowDiffType;

import burp.api.montoya.MontoyaApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Calculates differences between two texts using line and character level comparison.
 */
public class DiffCalculator {

    private static final double SIMILARITY_THRESHOLD = 0.74;
    private static final double COMMON_SUBSTRING_THRESHOLD = 0.5;
    private MontoyaApi api;

    /**
     * Sets the Montoya API reference for error logging.
     */
    public void setApi(MontoyaApi api) {
        this.api = api;
    }

    /**
     * Finds differences between original and modified text with character-level diff enabled.
     */
    public List<DiffSegment> findDifferences(String original, String modified) {
        return findDifferences(original, modified, true);
    }

    /**
     * Finds differences between original and modified text.
     * Tracks parent line indices for character-level diffs to enable O(1) visibility lookup.
     */
    public List<DiffSegment> findDifferences(String original, String modified, boolean characterLevelDiff) {
        List<DiffSegment> diffs = new ArrayList<>();
        int[] lineIndices = {0, 0};

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
                        int lineNum = sourcePosition + 1;
                        for (String line : delta.getSource().getLines()) {
                            int lineLen = line.length();
                            diffs.add(new DiffSegment(originalCharOffset, originalCharOffset + lineLen,
                                    line, true, DiffType.DELETED, lineIndices[0], lineNum));
                            lineIndices[0]++;
                            lineNum++;
                            originalCharOffset += lineLen + 1;
                        }
                    }
                    case INSERT -> {
                        int lineNum = targetPosition + 1;
                        for (String line : delta.getTarget().getLines()) {
                            int lineLen = line.length();
                            diffs.add(new DiffSegment(modifiedCharOffset, modifiedCharOffset + lineLen,
                                    line, false, DiffType.ADDED, lineIndices[1], lineNum));
                            lineIndices[1]++;
                            lineNum++;
                            modifiedCharOffset += lineLen + 1;
                        }
                    }
                    case CHANGE -> {
                        if (characterLevelDiff) {
                            processChangedLines(delta, originalCharOffset, modifiedCharOffset, diffs, lineIndices, sourcePosition, targetPosition);
                        } else {
                            processChangedLinesOnly(delta, originalCharOffset, modifiedCharOffset, diffs, lineIndices, sourcePosition, targetPosition);
                        }
                    }
                    default -> {}
                }
            }
        } catch (Exception e) {
            if (api != null) {
                api.logging().logToError("Error calculating diff: " + e.getMessage());
            }
            diffs.clear();
        }

        return diffs;
    }

    /**
     * Processes lines that have been modified, finding character-level differences.
     * Assigns parent line indices to character-level diffs for O(1) visibility lookup.
     */
    private void processChangedLines(AbstractDelta<String> delta, int originalCharOffset,
                                     int modifiedCharOffset, List<DiffSegment> diffs, int[] lineIndices,
                                     int sourcePosition, int targetPosition) {
        List<String> sourceLines = delta.getSource().getLines();
        List<String> targetLines = delta.getTarget().getLines();

        int srcOffset = originalCharOffset;
        int tgtOffset = modifiedCharOffset;
        int srcLineNum = sourcePosition + 1;
        int tgtLineNum = targetPosition + 1;

        int maxLines = Math.max(sourceLines.size(), targetLines.size());
        int srcIdx = 0;
        int tgtIdx = 0;

        for (int i = 0; i < maxLines; i++) {
            String srcLine = srcIdx < sourceLines.size() ? sourceLines.get(srcIdx) : null;
            String tgtLine = tgtIdx < targetLines.size() ? targetLines.get(tgtIdx) : null;

            if (srcLine != null && tgtLine != null) {
                if (shouldTreatAsModified(srcLine, tgtLine)) {
                    int originalParentIdx = lineIndices[0];
                    int modifiedParentIdx = lineIndices[1];
                    findCharacterDifferences(srcLine, tgtLine, srcOffset, tgtOffset, diffs, originalParentIdx, modifiedParentIdx, srcLineNum, tgtLineNum);
                    lineIndices[0]++;
                    lineIndices[1]++;
                } else {
                    diffs.add(new DiffSegment(srcOffset, srcOffset + srcLine.length(),
                            srcLine, true, DiffType.DELETED, lineIndices[0], srcLineNum));
                    diffs.add(new DiffSegment(tgtOffset, tgtOffset + tgtLine.length(),
                            tgtLine, false, DiffType.ADDED, lineIndices[1], tgtLineNum));
                    lineIndices[0]++;
                    lineIndices[1]++;
                }
                srcOffset += srcLine.length() + 1;
                tgtOffset += tgtLine.length() + 1;
                srcLineNum++;
                tgtLineNum++;
                srcIdx++;
                tgtIdx++;
            } else if (srcLine != null) {
                diffs.add(new DiffSegment(srcOffset, srcOffset + srcLine.length(),
                        srcLine, true, DiffType.DELETED, lineIndices[0], srcLineNum));
                lineIndices[0]++;
                srcOffset += srcLine.length() + 1;
                srcLineNum++;
                srcIdx++;
            } else if (tgtLine != null) {
                diffs.add(new DiffSegment(tgtOffset, tgtOffset + tgtLine.length(),
                        tgtLine, false, DiffType.ADDED, lineIndices[1], tgtLineNum));
                lineIndices[1]++;
                tgtOffset += tgtLine.length() + 1;
                tgtLineNum++;
                tgtIdx++;
            }
        }
    }

    /**
     * Processes changed lines marking entire lines as modified without character-level diff.
     * Uses Ratcliff/Obershelp similarity to determine if lines should be MODIFIED or DELETED+ADDED.
     */
    private void processChangedLinesOnly(AbstractDelta<String> delta, int originalCharOffset,
                                         int modifiedCharOffset, List<DiffSegment> diffs, int[] lineIndices,
                                         int sourcePosition, int targetPosition) {
        List<String> sourceLines = delta.getSource().getLines();
        List<String> targetLines = delta.getTarget().getLines();

        int srcOffset = originalCharOffset;
        int tgtOffset = modifiedCharOffset;
        int srcLineNum = sourcePosition + 1;
        int tgtLineNum = targetPosition + 1;

        int maxLines = Math.max(sourceLines.size(), targetLines.size());
        int srcIdx = 0;
        int tgtIdx = 0;

        for (int i = 0; i < maxLines; i++) {
            String srcLine = srcIdx < sourceLines.size() ? sourceLines.get(srcIdx) : null;
            String tgtLine = tgtIdx < targetLines.size() ? targetLines.get(tgtIdx) : null;

            if (srcLine != null && tgtLine != null) {
                if (shouldTreatAsModified(srcLine, tgtLine)) {
                    diffs.add(new DiffSegment(srcOffset, srcOffset + srcLine.length(),
                            srcLine, true, DiffType.MODIFIED, lineIndices[0], srcLineNum));
                    diffs.add(new DiffSegment(tgtOffset, tgtOffset + tgtLine.length(),
                            tgtLine, false, DiffType.MODIFIED, lineIndices[1], tgtLineNum));
                } else {
                    diffs.add(new DiffSegment(srcOffset, srcOffset + srcLine.length(),
                            srcLine, true, DiffType.DELETED, lineIndices[0], srcLineNum));
                    diffs.add(new DiffSegment(tgtOffset, tgtOffset + tgtLine.length(),
                            tgtLine, false, DiffType.ADDED, lineIndices[1], tgtLineNum));
                }
                lineIndices[0]++;
                lineIndices[1]++;
                srcOffset += srcLine.length() + 1;
                tgtOffset += tgtLine.length() + 1;
                srcLineNum++;
                tgtLineNum++;
                srcIdx++;
                tgtIdx++;
            } else if (srcLine != null) {
                diffs.add(new DiffSegment(srcOffset, srcOffset + srcLine.length(),
                        srcLine, true, DiffType.DELETED, lineIndices[0], srcLineNum));
                lineIndices[0]++;
                srcOffset += srcLine.length() + 1;
                srcLineNum++;
                srcIdx++;
            } else if (tgtLine != null) {
                diffs.add(new DiffSegment(tgtOffset, tgtOffset + tgtLine.length(),
                        tgtLine, false, DiffType.ADDED, lineIndices[1], tgtLineNum));
                lineIndices[1]++;
                tgtOffset += tgtLine.length() + 1;
                tgtLineNum++;
                tgtIdx++;
            }
        }
    }

    /**
     * Finds character-level differences between two lines.
     * Assigns parent line indices for O(1) visibility lookup.
     */
    private void findCharacterDifferences(String original, String modified,
                                          int originalBaseOffset, int modifiedBaseOffset,
                                          List<DiffSegment> diffs, int originalParentIdx, int modifiedParentIdx,
                                          int originalLineNum, int modifiedLineNum) {
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
                        chars, true, DiffType.MODIFIED, originalParentIdx, originalLineNum));
            }

            if (targetSize > 0) {
                String chars = String.join("", charDelta.getTarget().getLines());
                diffs.add(new DiffSegment(
                        modifiedBaseOffset + targetPos,
                        modifiedBaseOffset + targetPos + targetSize,
                        chars, false, DiffType.MODIFIED, modifiedParentIdx, modifiedLineNum));
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

    /**
     * Determines if two lines should be treated as MODIFIED rather than DELETED+ADDED.
     * Returns true if either:
     * - Ratcliff/Obershelp similarity >= 74%
     * - Longest common substring >= 50% of the shorter line (anywhere in the text)
     */
    private boolean shouldTreatAsModified(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return false;
        }
        if (s1.equals(s2)) {
            return true;
        }

        double similarity = calculateSimilarity(s1, s2);
        if (similarity >= SIMILARITY_THRESHOLD) {
            return true;
        }

        int[] lcs = findLongestCommonSubstring(s1, 0, s1.length(), s2, 0, s2.length());
        int longestCommon = lcs[0];
        int minLength = Math.min(s1.length(), s2.length());

        return longestCommon >= minLength * COMMON_SUBSTRING_THRESHOLD;
    }

    /**
     * Calculates the similarity ratio between two strings using the Ratcliff/Obershelp algorithm.
     * This is the same algorithm used by Python's difflib.SequenceMatcher.
     * Optimized with early exit when similarity cannot reach the threshold.
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.equals(s2)) {
            return 1.0;
        }
        if (s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }

        int totalLength = s1.length() + s2.length();
        int minMatchesNeeded = (int) Math.ceil(SIMILARITY_THRESHOLD * totalLength / 2.0);

        int matches = countMatchingCharacters(s1, 0, s1.length(), s2, 0, s2.length(), minMatchesNeeded, 0);

        if (matches < 0) {
            return 0.0;
        }

        return (2.0 * matches) / totalLength;
    }

    /**
     * Counts matching characters using the Ratcliff/Obershelp algorithm.
     * Uses indices instead of creating substrings to avoid object allocation.
     * Supports early exit when it's impossible to reach the required matches.
     */
    private int countMatchingCharacters(String s1, int start1, int end1,
                                        String s2, int start2, int end2,
                                        int minMatchesNeeded, int currentMatches) {
        int len1 = end1 - start1;
        int len2 = end2 - start2;

        if (len1 == 0 || len2 == 0) {
            return 0;
        }

        int maxPossible = Math.min(len1, len2);
        if (currentMatches + maxPossible < minMatchesNeeded) {
            return -1;
        }

        int[] lcs = findLongestCommonSubstring(s1, start1, end1, s2, start2, end2);
        int length = lcs[0];

        if (length == 0) {
            return 0;
        }

        int lcsStart1 = lcs[1];
        int lcsStart2 = lcs[2];

        int leftMatches = 0;
        int rightMatches = 0;

        if (lcsStart1 > start1 && lcsStart2 > start2) {
            leftMatches = countMatchingCharacters(
                    s1, start1, lcsStart1,
                    s2, start2, lcsStart2,
                    minMatchesNeeded, currentMatches + length);
            if (leftMatches < 0) {
                return -1;
            }
        }

        if (lcsStart1 + length < end1 && lcsStart2 + length < end2) {
            rightMatches = countMatchingCharacters(
                    s1, lcsStart1 + length, end1,
                    s2, lcsStart2 + length, end2,
                    minMatchesNeeded, currentMatches + length + leftMatches);
            if (rightMatches < 0) {
                return -1;
            }
        }

        return length + leftMatches + rightMatches;
    }

    /**
     * Finds the longest common substring between two string regions.
     * Uses O(min(n,m)) space with two arrays instead of O(n*m) matrix.
     * Returns array of [length, startIndex1, startIndex2].
     */
    private int[] findLongestCommonSubstring(String s1, int start1, int end1,
                                             String s2, int start2, int end2) {
        int len1 = end1 - start1;
        int len2 = end2 - start2;

        boolean swapped = len1 > len2;
        String shorter, longer;
        int shorterStart, shorterEnd, longerStart, longerEnd;

        if (swapped) {
            shorter = s2; shorterStart = start2; shorterEnd = end2;
            longer = s1; longerStart = start1; longerEnd = end1;
        } else {
            shorter = s1; shorterStart = start1; shorterEnd = end1;
            longer = s2; longerStart = start2; longerEnd = end2;
        }

        int shorterLen = shorterEnd - shorterStart;
        int longerLen = longerEnd - longerStart;

        int[] dpPrev = new int[shorterLen + 1];
        int[] dpCurr = new int[shorterLen + 1];

        int maxLength = 0;
        int maxEndShorter = 0;
        int maxEndLonger = 0;

        for (int i = 1; i <= longerLen; i++) {
            java.util.Arrays.fill(dpCurr, 0);

            for (int j = 1; j <= shorterLen; j++) {
                if (longer.charAt(longerStart + i - 1) == shorter.charAt(shorterStart + j - 1)) {
                    dpCurr[j] = dpPrev[j - 1] + 1;
                    if (dpCurr[j] > maxLength) {
                        maxLength = dpCurr[j];
                        maxEndLonger = i;
                        maxEndShorter = j;
                    }
                }
            }

            int[] temp = dpPrev;
            dpPrev = dpCurr;
            dpCurr = temp;
        }

        if (swapped) {
            return new int[]{maxLength, longerStart + maxEndLonger - maxLength, shorterStart + maxEndShorter - maxLength};
        } else {
            return new int[]{maxLength, shorterStart + maxEndShorter - maxLength, longerStart + maxEndLonger - maxLength};
        }
    }
}

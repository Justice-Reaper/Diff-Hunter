package org.diffhunter.diff;

import burp.api.montoya.MontoyaApi;
import org.diffhunter.model.DiffSegment;
import org.diffhunter.model.DiffType;
import org.diffhunter.util.Constants;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.Map;
import java.util.List;

/**
 * Handles highlighting of differences in JTextPane editors.
 */
public class DiffHighlighter {

    private MontoyaApi api;

    private static final DefaultHighlighter.DefaultHighlightPainter PAINTER_DELETED_DARK =
            new DefaultHighlighter.DefaultHighlightPainter(Constants.COLOR_DELETED_REQUEST_DARK);
    private static final DefaultHighlighter.DefaultHighlightPainter PAINTER_ADDED_DARK =
            new DefaultHighlighter.DefaultHighlightPainter(Constants.COLOR_ADDED_BOTH_DARK);
    private static final DefaultHighlighter.DefaultHighlightPainter PAINTER_MODIFIED_DARK =
            new DefaultHighlighter.DefaultHighlightPainter(Constants.COLOR_MODIFIED_RESPONSE_DARK);
    private static final DefaultHighlighter.DefaultHighlightPainter PAINTER_DELETED_LIGHT =
            new DefaultHighlighter.DefaultHighlightPainter(Constants.COLOR_DELETED_REQUEST_LIGHT);
    private static final DefaultHighlighter.DefaultHighlightPainter PAINTER_ADDED_LIGHT =
            new DefaultHighlighter.DefaultHighlightPainter(Constants.COLOR_ADDED_BOTH_LIGHT);
    private static final DefaultHighlighter.DefaultHighlightPainter PAINTER_MODIFIED_LIGHT =
            new DefaultHighlighter.DefaultHighlightPainter(Constants.COLOR_MODIFIED_RESPONSE_LIGHT);

    /**
     * Sets the Montoya API reference for error logging.
     */
    public void setApi(MontoyaApi api) {
        this.api = api;
    }

    /**
     * Returns the appropriate highlight painter for the given diff type and theme.
     */
    private DefaultHighlighter.DefaultHighlightPainter getPainter(DiffType type, boolean isDarkTheme) {
        return switch (type) {
            case DELETED -> isDarkTheme ? PAINTER_DELETED_DARK : PAINTER_DELETED_LIGHT;
            case ADDED -> isDarkTheme ? PAINTER_ADDED_DARK : PAINTER_ADDED_LIGHT;
            case MODIFIED -> isDarkTheme ? PAINTER_MODIFIED_DARK : PAINTER_MODIFIED_LIGHT;
        };
    }

    /**
     * Sets text in a JTextPane and applies highlighting to differences.
     */
    public void setTextWithHighlighting(JTextPane pane, String text, boolean showOriginalDiffs,
                                        List<DiffSegment> diffs, Map<Integer, Boolean> selection,
                                        boolean isDarkTheme) {
        if (pane == null) return;

        Document doc = pane.getDocument();
        String currentText;
        try {
            currentText = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            if (api != null) api.logging().logToError("[DiffHunter] Error getting document text: " + e.getMessage());
            currentText = "";
        }

        if (!currentText.equals(text)) {
            pane.setText(text);
            pane.setCaretPosition(0);
        }

        Highlighter highlighter = pane.getHighlighter();
        highlighter.removeAllHighlights();

        pane.setIgnoreRepaint(true);
        try {
            for (int i = 0; i < diffs.size(); i++) {
                Boolean show = selection.getOrDefault(i, Boolean.TRUE);
                if (show != null && show) {
                    DiffSegment diff = diffs.get(i);
                    if (diff.isOriginal() == showOriginalDiffs) {
                        int start = diff.getStartOffset();
                        int end = diff.getEndOffset();

                        if (start >= 0 && end <= text.length() && start < end) {
                            try {
                                highlighter.addHighlight(start, end, getPainter(diff.getType(), isDarkTheme));
                            } catch (BadLocationException e) {
                                if (api != null) api.logging().logToError("[DiffHunter] Error adding highlight: " + e.getMessage());
                            }
                        }
                    }
                }
            }
        } finally {
            pane.setIgnoreRepaint(false);
            pane.repaint();
        }

        forceRender(pane);
    }

    /**
     * Forces immediate layout calculation and rendering of a JTextPane.
     * This prevents deferred rendering when the pane becomes visible later.
     */
    private void forceRender(JTextPane pane) {
        if (pane == null) return;
        pane.validate();
        pane.getUI().getPreferredSize(pane);
    }

    /**
     * Clears highlighting from a JTextPane.
     */
    public void clearHighlighting(JTextPane pane) {
        if (pane == null) return;
        pane.getHighlighter().removeAllHighlights();
    }

    /**
     * Scrolls a JTextPane to the specified position.
     */
    public void scrollToPosition(JTextPane pane, int position) {
        if (pane == null) return;

        SwingUtilities.invokeLater(() -> {
            try {
                int length = pane.getDocument().getLength();
                int safePosition = Math.min(position, length);
                if (safePosition < 0) safePosition = 0;

                pane.setCaretPosition(safePosition);

                java.awt.geom.Rectangle2D rect2D = pane.modelToView2D(safePosition);
                if (rect2D != null) {
                    Rectangle rect = new Rectangle(
                            (int) rect2D.getX(),
                            Math.max(0, (int) rect2D.getY() - 50),
                            (int) rect2D.getWidth(),
                            150
                    );
                    pane.scrollRectToVisible(rect);
                }
            } catch (BadLocationException e) {
                if (api != null) api.logging().logToError("[DiffHunter] Error scrolling to position: " + e.getMessage());
            }
        });
    }

    /**
     * Saves scroll position of a JTextPane.
     */
    public void saveScrollPosition(JTextPane pane, Map<JTextPane, Point> positions) {
        if (pane == null) return;
        Container parent = pane.getParent();
        if (parent instanceof JViewport) {
            positions.put(pane, ((JViewport) parent).getViewPosition());
        }
    }

    /**
     * Restores saved scroll positions.
     */
    public void restoreScrollPositions(Map<JTextPane, Point> positions) {
        SwingUtilities.invokeLater(() -> {
            for (Map.Entry<JTextPane, Point> entry : positions.entrySet()) {
                JTextPane pane = entry.getKey();
                Point pos = entry.getValue();
                if (pane != null && pos != null) {
                    Container parent = pane.getParent();
                    if (parent instanceof JViewport) {
                        ((JViewport) parent).setViewPosition(pos);
                    }
                }
            }
        });
    }
}

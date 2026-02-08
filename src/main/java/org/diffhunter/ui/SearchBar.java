package org.diffhunter.ui;

import javax.swing.*;
import javax.swing.plaf.LayerUI;
import javax.swing.text.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Search bar component for searching and highlighting text in JTextPane.
 * Uses JLayer overlay to paint search underlines on top of diff highlights.
 */
public class SearchBar extends JPanel {

    private final JTextField searchField;
    private final JLabel highlightCountLabel;
    private final JButton prevButton;
    private final JButton nextButton;
    private final JTabbedPane tabbedPane;
    private final JTextPane requestPane;
    private final JTextPane responsePane;
    private final UIContext context;

    private final List<int[]> matchPositions = new ArrayList<>();
    private int currentMatchIndex = -1;
    private boolean searchPending = false;

    private SearchOverlayUI requestOverlay;
    private SearchOverlayUI responseOverlay;

    /**
     * Creates a search bar for the given tabbed pane containing request/response panes.
     */
    public SearchBar(UIContext context, JTabbedPane tabbedPane, JTextPane requestPane, JTextPane responsePane) {
        this.context = context;
        this.tabbedPane = tabbedPane;
        this.requestPane = requestPane;
        this.responsePane = responsePane;

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        prevButton = new JButton("←");
        prevButton.setMargin(new Insets(1, 4, 1, 4));
        prevButton.setFocusable(false);
        prevButton.addActionListener(e -> navigateToPrevious());
        prevButton.setEnabled(false);

        nextButton = new JButton("→");
        nextButton.setMargin(new Insets(1, 4, 1, 4));
        nextButton.setFocusable(false);
        nextButton.addActionListener(e -> navigateToNext());
        nextButton.setEnabled(false);

        searchField = new JTextField(20);
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, searchField.getPreferredSize().height));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                performSearch();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                performSearch();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                performSearch();
            }
        });

        highlightCountLabel = new JLabel("0 highlights");
        highlightCountLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        add(prevButton);
        add(Box.createHorizontalStrut(2));
        add(nextButton);
        add(Box.createHorizontalStrut(5));
        add(searchField);
        add(highlightCountLabel);

        tabbedPane.addChangeListener(e -> performSearch());

        requestOverlay = new SearchOverlayUI(requestPane, this);
        responseOverlay = new SearchOverlayUI(responsePane, this);

        wrapPaneWithOverlay(tabbedPane, 0, requestPane, requestOverlay);
        wrapPaneWithOverlay(tabbedPane, 1, responsePane, responseOverlay);

        javax.swing.event.DocumentListener paneDocListener = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
        };
        requestPane.getDocument().addDocumentListener(paneDocListener);
        responsePane.getDocument().addDocumentListener(paneDocListener);
    }

    /**
     * Wraps a JTextPane with a JLayer overlay for painting search highlights.
     */
    private void wrapPaneWithOverlay(JTabbedPane tabs, int tabIndex, JTextPane pane, SearchOverlayUI overlay) {
        Component tabComponent = tabs.getComponentAt(tabIndex);
        if (tabComponent instanceof JScrollPane) {
            JScrollPane scrollPane = (JScrollPane) tabComponent;
            JLayer<JTextPane> layer = new JLayer<>(pane, overlay);
            scrollPane.getViewport().setView(layer);
        }
    }

    /**
     * Schedules a search re-execution with debouncing.
     * When setText() fires remove+insert events, only one search runs after both complete.
     */
    private void scheduleSearch() {
        if (!searchPending && !searchField.getText().isEmpty()) {
            searchPending = true;
            SwingUtilities.invokeLater(() -> {
                searchPending = false;
                performSearch();
            });
        }
    }

    /**
     * Returns the currently active text pane based on the selected tab.
     */
    private JTextPane getActivePane() {
        return tabbedPane.getSelectedIndex() == 0 ? requestPane : responsePane;
    }

    /**
     * Performs a case-insensitive search and stores all match positions.
     */
    private void performSearch() {
        matchPositions.clear();
        currentMatchIndex = -1;

        String searchText = searchField.getText();
        if (searchText.isEmpty()) {
            updateSearchState();
            repaintOverlays();
            return;
        }

        JTextPane activePane = getActivePane();
        String content;
        try {
            content = activePane.getDocument().getText(0, activePane.getDocument().getLength());
        } catch (BadLocationException e) {
            if (context.getApi() != null) {
                context.getApi().logging().logToError("[DiffHunter] Error retrieving document text for search: " + e.getMessage());
            }
            return;
        }

        String searchLower = searchText.toLowerCase();
        String contentLower = content.toLowerCase();

        int index = 0;
        while ((index = contentLower.indexOf(searchLower, index)) >= 0) {
            matchPositions.add(new int[]{index, index + searchText.length()});
            index += searchText.length();
        }

        if (!matchPositions.isEmpty()) {
            currentMatchIndex = 0;
            scrollToMatch(activePane, currentMatchIndex);
        }

        updateSearchState();
        repaintOverlays();
    }

    /**
     * Triggers a repaint of both panes to update search highlights.
     */
    private void repaintOverlays() {
        requestPane.repaint();
        responsePane.repaint();
    }

    /**
     * Scrolls the text pane to make the specified match visible.
     */
    private void scrollToMatch(JTextPane pane, int matchIndex) {
        if (matchIndex < 0 || matchIndex >= matchPositions.size()) {
            return;
        }

        int[] pos = matchPositions.get(matchIndex);
        try {
            java.awt.geom.Rectangle2D rect2D = pane.modelToView2D(pos[0]);
            if (rect2D == null) {
                return;
            }
            Rectangle rect = rect2D.getBounds();
            pane.scrollRectToVisible(rect);
            pane.setCaretPosition(pos[0]);
        } catch (BadLocationException e) {
            if (context.getApi() != null) {
                context.getApi().logging().logToError("[DiffHunter] Error scrolling to search match: " + e.getMessage());
            }
        }
    }

    /**
     * Navigates to the previous match, wrapping to the last if at the first.
     */
    private void navigateToPrevious() {
        if (matchPositions.isEmpty()) {
            return;
        }

        currentMatchIndex--;
        if (currentMatchIndex < 0) {
            currentMatchIndex = matchPositions.size() - 1;
        }

        JTextPane activePane = getActivePane();
        scrollToMatch(activePane, currentMatchIndex);
        updateSearchState();
        repaintOverlays();
    }

    /**
     * Navigates to the next match, wrapping to the first if at the last.
     */
    private void navigateToNext() {
        if (matchPositions.isEmpty()) {
            return;
        }

        currentMatchIndex++;
        if (currentMatchIndex >= matchPositions.size()) {
            currentMatchIndex = 0;
        }

        JTextPane activePane = getActivePane();
        scrollToMatch(activePane, currentMatchIndex);
        updateSearchState();
        repaintOverlays();
    }

    /**
     * Updates the navigation buttons state and match count label.
     */
    private void updateSearchState() {
        boolean hasMatches = !matchPositions.isEmpty();
        prevButton.setEnabled(hasMatches);
        nextButton.setEnabled(hasMatches);

        if (matchPositions.isEmpty()) {
            highlightCountLabel.setText("0 highlights");
        } else {
            highlightCountLabel.setText((currentMatchIndex + 1) + "/" + matchPositions.size() + " highlights");
        }
    }

    /**
     * Clears the search field and removes all highlights.
     */
    public void clearSearch() {
        searchField.setText("");
        matchPositions.clear();
        currentMatchIndex = -1;
        updateSearchState();
        repaintOverlays();
    }

    /**
     * Returns the list of match positions as [start, end] arrays.
     */
    List<int[]> getMatchPositions() {
        return matchPositions;
    }

    /**
     * Returns the index of the currently selected match.
     */
    int getCurrentMatchIndex() {
        return currentMatchIndex;
    }

    /**
     * Checks whether the specified pane is the currently active pane.
     */
    boolean isActivePane(JTextPane pane) {
        return getActivePane() == pane;
    }

    /**
     * Returns the UI context.
     */
    UIContext getContext() {
        return context;
    }

    /**
     * LayerUI that paints search underlines on top of the JTextPane content.
     */
    private static class SearchOverlayUI extends LayerUI<JTextPane> {

        private final JTextPane pane;
        private final SearchBar searchBar;

        /**
         * Creates a new SearchOverlayUI for the specified pane.
         */
        public SearchOverlayUI(JTextPane pane, SearchBar searchBar) {
            this.pane = pane;
            this.searchBar = searchBar;
        }

        /**
         * Paints the component and draws search underlines on top.
         */
        @Override
        public void paint(Graphics g, JComponent c) {
            super.paint(g, c);

            if (!searchBar.isActivePane(pane)) {
                return;
            }

            List<int[]> positions = searchBar.getMatchPositions();
            if (positions.isEmpty()) {
                return;
            }

            Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color underlineColor = searchBar.getContext().isDarkTheme()
                        ? new Color(255, 200, 0)
                        : new Color(180, 120, 0);
                Color currentColor = new Color(255, 50, 0);

                int currentIdx = searchBar.getCurrentMatchIndex();

                int docLength = pane.getDocument().getLength();

                for (int i = 0; i < positions.size(); i++) {
                    int[] pos = positions.get(i);
                    if (pos[0] < 0 || pos[1] > docLength) continue;

                    boolean isCurrent = (i == currentIdx);

                    try {
                        Rectangle r0 = pane.modelToView(pos[0]);
                        Rectangle r1 = pane.modelToView(pos[1]);

                        if (r0 == null || r1 == null) continue;

                        g2d.setColor(isCurrent ? currentColor : underlineColor);
                        float thickness = isCurrent ? 4.0f : 3.0f;
                        g2d.setStroke(new BasicStroke(thickness));

                        if (r0.y == r1.y) {
                            int y = r0.y + r0.height - 2;
                            g2d.drawLine(r0.x, y, r1.x, y);
                        } else {
                            int fullWidth = pane.getWidth();

                            int y0 = r0.y + r0.height - 2;
                            g2d.drawLine(r0.x, y0, fullWidth, y0);

                            int lineHeight = r0.height;
                            for (int lineY = r0.y + lineHeight; lineY < r1.y; lineY += lineHeight) {
                                int yMid = lineY + lineHeight - 2;
                                g2d.drawLine(0, yMid, fullWidth, yMid);
                            }

                            int y1 = r1.y + r1.height - 2;
                            g2d.drawLine(0, y1, r1.x, y1);
                        }
                    } catch (BadLocationException e) {
                        if (searchBar.getContext().getApi() != null) {
                            searchBar.getContext().getApi().logging().logToError("[DiffHunter] Error painting search highlight: " + e.getMessage());
                        }
                    }
                }
            } finally {
                g2d.dispose();
            }
        }
    }
}

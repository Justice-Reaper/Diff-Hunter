package org.diffhunter;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import org.diffhunter.diff.DiffCalculator;
import org.diffhunter.diff.DiffHighlighter;
import org.diffhunter.diff.HexDumpConverter;
import org.diffhunter.handler.HttpCaptureHandler;
import org.diffhunter.model.DiffSegment;
import org.diffhunter.model.HttpLogEntry;
import org.diffhunter.model.RowDiffType;
import org.diffhunter.ui.*;
import org.diffhunter.util.Constants;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Main Diff Hunter extension class for Burp Suite.
 * Compares HTTP requests and responses to identify differences.
 */
public class DiffHunter implements BurpExtension {

    private MontoyaApi api;
    private final UIContext context = new UIContext();
    private PropertyChangeListener themeChangeListener;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        context.setApi(api);

        initializeTheme();

        api.extension().setName(Constants.EXTENSION_NAME);

        HttpCaptureHandler httpHandler = new HttpCaptureHandler(api, context);
        api.http().registerHttpHandler(httpHandler);

        themeChangeListener = this::onLookAndFeelChanged;
        UIManager.addPropertyChangeListener(themeChangeListener);

        api.extension().registerUnloadingHandler(this::cleanupResources);

        SwingUtilities.invokeLater(() -> {
            JPanel mainPanel = createUI();
            api.userInterface().registerSuiteTab(Constants.EXTENSION_NAME, mainPanel);

            javax.swing.Timer batchUpdateTimer = new javax.swing.Timer(
                    Constants.BATCH_UPDATE_INTERVAL_MS, e -> {
                        if (!context.isExtensionUnloading()) {
                            processPendingEntries();
                        }
                    });
            batchUpdateTimer.start();
            context.setBatchUpdateTimer(batchUpdateTimer);
        });

        api.logging().logToOutput("===========================================");
        api.logging().logToOutput(Constants.EXTENSION_NAME + " v" + Constants.EXTENSION_VERSION);
        api.logging().logToOutput("Usage details: https://github.com/Justice-Reaper/Diff-Hunter");
        api.logging().logToOutput("Author: " + Constants.AUTHOR);
        api.logging().logToOutput("Email: " + Constants.EMAIL);
        api.logging().logToOutput("Linktree: " + Constants.LINKTREE);
        api.logging().logToOutput("===========================================");
        api.logging().logToOutput(Constants.EXTENSION_NAME + " Extension loaded successfully!");
    }

    /**
     * Initializes theme colors and editor font based on Burp Suite settings.
     */
    private void initializeTheme() {
        boolean isDarkTheme = false;
        try {
            String themeName = api.userInterface().currentTheme().name().toLowerCase();
            isDarkTheme = themeName.contains("dark");
        } catch (Exception e) {
            api.logging().logToError("[DiffHunter] Error detecting theme: " + e.getMessage());
        }

        context.setDarkTheme(isDarkTheme);
        context.setColorBackground(isDarkTheme ? Constants.COLOR_DARK_BACKGROUND : Constants.COLOR_LIGHT_BACKGROUND);
        context.setColorForeground(isDarkTheme ? Constants.COLOR_DARK_FOREGROUND : Constants.COLOR_LIGHT_FOREGROUND);

        try {
            Font burpFont = api.userInterface().currentEditorFont();
            if (burpFont != null) {
                context.setEditorFont(burpFont);
            } else {
                context.setEditorFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            }
        } catch (Exception e) {
            api.logging().logToError("[DiffHunter] Error getting editor font: " + e.getMessage());
            context.setEditorFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        }
    }

    /**
     * Handles theme changes from Burp Suite and updates all UI components.
     */
    private void onLookAndFeelChanged(PropertyChangeEvent evt) {
        if ("lookAndFeel".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(() -> {
                boolean currentIsDark = false;
                try {
                    String themeName = api.userInterface().currentTheme().name().toLowerCase();
                    currentIsDark = themeName.contains("dark");
                } catch (Exception e) {
                    api.logging().logToError("[DiffHunter] Error detecting theme change: " + e.getMessage());
                    return;
                }

                context.setDarkTheme(currentIsDark);
                context.setColorBackground(currentIsDark ? Constants.COLOR_DARK_BACKGROUND : Constants.COLOR_LIGHT_BACKGROUND);
                context.setColorForeground(currentIsDark ? Constants.COLOR_DARK_FOREGROUND : Constants.COLOR_LIGHT_FOREGROUND);

                updateTextPaneColors(context.getRequestPane());
                updateTextPaneColors(context.getResponsePane());
                updateTextPaneColors(context.getRequestPaneEndpoint());
                updateTextPaneColors(context.getResponsePaneEndpoint());

                if (context.getRequestTable() != null) {
                    SwingUtilities.updateComponentTreeUI(context.getRequestTable());
                    context.getRequestTable().repaint();
                    if (context.getTableCheckBoxRenderer() != null) {
                        SwingUtilities.updateComponentTreeUI(context.getTableCheckBoxRenderer());
                    }
                }

                for (JPopupMenu menu : context.getContextMenus()) {
                    SwingUtilities.updateComponentTreeUI(menu);
                }

                if (context.getColorBoxModified() != null) {
                    context.getColorBoxModified().setBackground(currentIsDark ? Constants.COLOR_MODIFIED_RESPONSE_DARK : Constants.COLOR_MODIFIED_RESPONSE_LIGHT);
                }
                if (context.getColorBoxDeleted() != null) {
                    context.getColorBoxDeleted().setBackground(currentIsDark ? Constants.COLOR_DELETED_REQUEST_DARK : Constants.COLOR_DELETED_REQUEST_LIGHT);
                }
                if (context.getColorBoxAdded() != null) {
                    context.getColorBoxAdded().setBackground(currentIsDark ? Constants.COLOR_ADDED_BOTH_DARK : Constants.COLOR_ADDED_BOTH_LIGHT);
                }

                if (context.getTableFilterField() != null) {
                    context.getTableFilterField().setBackground(context.getColorBackground());
                    context.getTableFilterField().setForeground(context.getColorForeground());
                    context.getTableFilterField().setCaretColor(context.getColorForeground());
                }

                applyHighlightingToEditors();
            });
        }
    }

    /**
     * Updates colors for a text pane based on current theme.
     */
    private void updateTextPaneColors(JTextPane pane) {
        if (pane != null) {
            pane.setBackground(context.getColorBackground());
            pane.setForeground(context.getColorForeground());
            pane.setCaretColor(context.getColorForeground());
            pane.repaint();
        }
    }

    /**
     * Creates and assembles the main UI panel with all components.
     */
    private JPanel createUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        ControlPanel controlPanel = new ControlPanel(
                context,
                this::applyTableFilter,
                this::clearLog,
                this::recalculateAllInBackground
        );
        mainPanel.add(controlPanel.create(), BorderLayout.NORTH);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        TablePanel tablePanel = new TablePanel(
                context,
                this::displaySelectedRequest,
                this::updateHostAndEndpointCombos
        );
        centerSplit.setLeftComponent(tablePanel.create());

        MatchPanel matchPanel = new MatchPanel(
                context,
                this::updateEndpointCombo,
                this::selectEndpointInTable,
                this::applyHighlightingToEditors,
                this::scrollToSelectedRequestDiff,
                this::scrollToSelectedResponseDiff
        );
        centerSplit.setRightComponent(matchPanel.create());
        centerSplit.setResizeWeight(0.5);
        centerSplit.setDividerLocation(0.5);

        EditorsPanel editorsPanel = new EditorsPanel(context);
        JPanel bottomPanel = editorsPanel.create();

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerSplit, bottomPanel);
        mainSplit.setDividerLocation(Constants.DIVIDER_LOCATION_VERTICAL);
        mainSplit.setResizeWeight(Constants.RESIZE_WEIGHT);

        mainPanel.add(mainSplit, BorderLayout.CENTER);

        return mainPanel;
    }

    /**
     * Applies the current filter to the request table.
     */
    private void applyTableFilter() {
        String filterText = context.getTableFilterField().getText();
        boolean caseSensitive = context.getTableFilterCaseSensitive().isSelected();
        boolean useRegex = context.getTableFilterRegex().isSelected();
        boolean negative = context.getTableFilterNegative().isSelected();

        try {
            Pattern pattern = null;
            if (!filterText.isEmpty()) {
                if (useRegex) {
                    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                    pattern = Pattern.compile(filterText, flags);
                } else {
                    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                    pattern = Pattern.compile(Pattern.quote(filterText), flags);
                }
            }

            final Pattern finalPattern = pattern;

            context.getTableSorter().setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                    Object requestNumObj = entry.getValue(0);
                    if (requestNumObj == null) return false;

                    int requestNum = (Integer) requestNumObj;
                    HttpLogEntry logEntry = context.getLogEntriesMap().get(requestNum);
                    if (logEntry == null) return false;

                    if (!passesDiffTypeFilter(logEntry)) {
                        return false;
                    }

                    if (finalPattern != null) {
                        boolean matches = false;
                        if (context.isFilterRequests() && context.isFilterResponses()) {
                            matches = finalPattern.matcher(logEntry.getRequestStr()).find() ||
                                      finalPattern.matcher(logEntry.getResponseStr()).find();
                        } else if (context.isFilterRequests()) {
                            matches = finalPattern.matcher(logEntry.getRequestStr()).find();
                        } else if (context.isFilterResponses()) {
                            matches = finalPattern.matcher(logEntry.getResponseStr()).find();
                        } else {
                            return true;
                        }
                        return negative != matches;
                    }

                    return true;
                }
            });

            context.getTableFilterField().setBackground(context.getColorBackground());
        } catch (Exception e) {
            context.getTableFilterField().setBackground(Constants.COLOR_SEARCH_ERROR);
        }
    }

    /**
     * Checks if a log entry passes the diff type filter based on current settings.
     */
    private boolean passesDiffTypeFilter(HttpLogEntry logEntry) {
        RowDiffType rowDiffType = logEntry.getRowDiffType();

        return switch (rowDiffType) {
            case REQUEST_ONLY -> context.isShowRequestDiff();
            case RESPONSE_ONLY -> context.isShowResponseDiff();
            case BOTH -> context.isShowBothDiff();
            case NONE -> context.isShowNoDiff();
        };
    }

    /**
     * Updates the endpoint combo box based on the selected host.
     */
    private void updateEndpointCombo() {
        if (context.getEndpointFilterCombo() == null) return;

        String selectedHost = (String) context.getHostFilterCombo().getSelectedItem();
        context.getEndpointFilterCombo().removeAllItems();

        if (selectedHost == null) return;

        Set<String> endpoints = new LinkedHashSet<>();
        for (HttpLogEntry entry : context.getLogEntries()) {
            if (entry.isMarked() && entry.getHost().equals(selectedHost)) {
                endpoints.add(entry.getEndpoint());
            }
        }

        for (String endpoint : endpoints) {
            context.getEndpointFilterCombo().addItem(endpoint);
        }
    }

    /**
     * Selects the target entry based on the current host and endpoint combo selections.
     */
    private void selectEndpointInTable() {
        if (context.getEndpointFilterCombo() == null) return;

        String selectedHost = (String) context.getHostFilterCombo().getSelectedItem();
        String selectedEndpoint = (String) context.getEndpointFilterCombo().getSelectedItem();

        if (selectedHost == null || selectedEndpoint == null) {
            context.setCurrentTargetEntry(null);
            if (context.getRequestPaneEndpoint() != null) context.getRequestPaneEndpoint().setText("");
            if (context.getResponsePaneEndpoint() != null) context.getResponsePaneEndpoint().setText("");
            context.getRequestMatchTableModel().setRowCount(0);
            context.getResponseMatchTableModel().setRowCount(0);
            context.getRequestDiffs().clear();
            context.getResponseDiffs().clear();
            context.getRequestDiffSelection().clear();
            context.getResponseDiffSelection().clear();
            clearAllDifferenceMarks();
            return;
        }

        for (HttpLogEntry entry : context.getTargetEntries().values()) {
            if (entry.getHost().equals(selectedHost) && entry.getEndpoint().equals(selectedEndpoint)) {
                context.setCurrentTargetEntry(entry);
                if (context.getRequestPaneEndpoint() != null) {
                    context.getRequestPaneEndpoint().setText(getRequestText(entry));
                }
                if (context.getResponsePaneEndpoint() != null) {
                    context.getResponsePaneEndpoint().setText(getResponseText(entry));
                }
                calculateAndDisplayDiffs();
                markTableDifferences();
                break;
            }
        }
    }

    /**
     * Updates the host and endpoint combo boxes when targets change.
     */
    private void updateHostAndEndpointCombos() {
        if (context.getHostFilterCombo() == null || context.getEndpointFilterCombo() == null) return;

        String previousHost = (String) context.getHostFilterCombo().getSelectedItem();
        String previousEndpoint = (String) context.getEndpointFilterCombo().getSelectedItem();
        boolean wasEmpty = previousHost == null;

        context.setUpdatingCombos(true);
        try {
            Set<String> hosts = new LinkedHashSet<>();
            for (HttpLogEntry entry : context.getTargetEntries().values()) {
                hosts.add(entry.getHost());
            }

            context.getHostFilterCombo().removeAllItems();
            for (String host : hosts) {
                context.getHostFilterCombo().addItem(host);
            }

            if (previousHost != null) {
                for (int i = 0; i < context.getHostFilterCombo().getItemCount(); i++) {
                    if (previousHost.equals(context.getHostFilterCombo().getItemAt(i))) {
                        context.getHostFilterCombo().setSelectedIndex(i);
                        break;
                    }
                }
            }

            updateEndpointCombo();

            if (previousEndpoint != null) {
                for (int i = 0; i < context.getEndpointFilterCombo().getItemCount(); i++) {
                    if (previousEndpoint.equals(context.getEndpointFilterCombo().getItemAt(i))) {
                        context.getEndpointFilterCombo().setSelectedIndex(i);
                        break;
                    }
                }
            }
        } finally {
            context.setUpdatingCombos(false);
        }

        String currentHost = (String) context.getHostFilterCombo().getSelectedItem();
        String currentEndpoint = (String) context.getEndpointFilterCombo().getSelectedItem();

        if (currentHost == null || currentEndpoint == null) {
            context.setCurrentTargetEntry(null);
            clearAllDifferenceMarks();
        } else if (wasEmpty) {
            selectEndpointInTable();
        }
    }

    /**
     * Displays the currently selected request in the editors and calculates diffs.
     */
    private void displaySelectedRequest() {
        int selectedRow = context.getRequestTable().getSelectedRow();
        if (selectedRow >= 0) {
            int modelRow = context.getRequestTable().convertRowIndexToModel(selectedRow);
            int requestNum = (Integer) context.getTableModel().getValueAt(modelRow, 0);
            HttpLogEntry entry = context.getLogEntriesMap().get(requestNum);

            if (entry != null) {
                HttpLogEntry previousEntry = context.getCurrentSelectedEntry();
                if (previousEntry != null && previousEntry.getNumber() == entry.getNumber()) {
                    return;
                }

                context.setCurrentSelectedEntry(entry);
                if (context.getRequestPane() != null) {
                    context.getRequestPane().setText(getRequestText(entry));
                }
                if (context.getResponsePane() != null) {
                    context.getResponsePane().setText(getResponseText(entry));
                }
                calculateAndDisplayDiffs();
            }
        } else {
            context.setCurrentSelectedEntry(null);
            clearHighlighting();
        }
    }

    /**
     * Calculates differences between selected and target entries and updates the UI.
     */
    private void calculateAndDisplayDiffs() {
        context.getRequestMatchTableModel().setRowCount(0);
        context.getResponseMatchTableModel().setRowCount(0);
        context.getRequestDiffs().clear();
        context.getResponseDiffs().clear();
        context.getRequestDiffSelection().clear();
        context.getResponseDiffSelection().clear();

        if (context.getCurrentTargetEntry() == null || context.getCurrentSelectedEntry() == null) {
            clearHighlighting();
            return;
        }

        Component rootComponent = context.getRequestTable().getTopLevelAncestor();
        if (rootComponent != null) {
            rootComponent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        String targetRequestText = getRequestText(context.getCurrentTargetEntry());
        String selectedRequestText = getRequestText(context.getCurrentSelectedEntry());
        String targetResponseText = getResponseText(context.getCurrentTargetEntry());
        String selectedResponseText = getResponseText(context.getCurrentSelectedEntry());

        DiffCalculator calculator = context.getDiffCalculator();

        List<DiffSegment> reqDiffs = new ArrayList<>();
        List<DiffSegment> respDiffs = new ArrayList<>();

        Thread requestThread = new Thread(() -> {
            reqDiffs.addAll(calculator.findDifferences(targetRequestText, selectedRequestText));
        });

        Thread responseThread = new Thread(() -> {
            respDiffs.addAll(calculator.findDifferences(targetResponseText, selectedResponseText));
        });

        requestThread.start();
        responseThread.start();

        try {
            requestThread.join();
            responseThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        context.getRequestDiffs().addAll(reqDiffs);
        context.getResponseDiffs().addAll(respDiffs);

        for (int i = 0; i < reqDiffs.size(); i++) {
            context.getRequestDiffSelection().put(i, Boolean.TRUE);
            context.getRequestMatchTableModel().addRow(new Object[]{reqDiffs.get(i).toString(), Boolean.TRUE});
        }

        for (int i = 0; i < respDiffs.size(); i++) {
            context.getResponseDiffSelection().put(i, Boolean.TRUE);
            context.getResponseMatchTableModel().addRow(new Object[]{respDiffs.get(i).toString(), Boolean.TRUE});
        }

        applyHighlightingToEditorsWithTexts(selectedRequestText, selectedResponseText,
                targetRequestText, targetResponseText);

        if (rootComponent != null) {
            rootComponent.setCursor(Cursor.getDefaultCursor());
        }
    }

    /**
     * Applies highlighting to all editors based on current diffs.
     */
    private void applyHighlightingToEditors() {
        if (context.getCurrentTargetEntry() == null || context.getCurrentSelectedEntry() == null) {
            clearHighlighting();
            return;
        }

        String selectedRequestText = getRequestText(context.getCurrentSelectedEntry());
        String selectedResponseText = getResponseText(context.getCurrentSelectedEntry());
        String targetRequestText = getRequestText(context.getCurrentTargetEntry());
        String targetResponseText = getResponseText(context.getCurrentTargetEntry());

        applyHighlightingToEditorsWithTexts(selectedRequestText, selectedResponseText,
                targetRequestText, targetResponseText);
    }

    /**
     * Applies highlighting to editors with the provided texts.
     */
    private void applyHighlightingToEditorsWithTexts(String selectedRequestText, String selectedResponseText,
                                                      String targetRequestText, String targetResponseText) {
        DiffHighlighter highlighter = context.getDiffHighlighter();
        Map<JTextPane, Point> scrollPositions = new HashMap<>();
        highlighter.saveScrollPosition(context.getRequestPane(), scrollPositions);
        highlighter.saveScrollPosition(context.getResponsePane(), scrollPositions);
        highlighter.saveScrollPosition(context.getRequestPaneEndpoint(), scrollPositions);
        highlighter.saveScrollPosition(context.getResponsePaneEndpoint(), scrollPositions);

        boolean isDark = context.isDarkTheme();
        highlighter.setTextWithHighlighting(context.getRequestPane(), selectedRequestText, false,
                context.getRequestDiffs(), context.getRequestDiffSelection(), isDark);
        highlighter.setTextWithHighlighting(context.getResponsePane(), selectedResponseText, false,
                context.getResponseDiffs(), context.getResponseDiffSelection(), isDark);
        highlighter.setTextWithHighlighting(context.getRequestPaneEndpoint(), targetRequestText, true,
                context.getRequestDiffs(), context.getRequestDiffSelection(), isDark);
        highlighter.setTextWithHighlighting(context.getResponsePaneEndpoint(), targetResponseText, true,
                context.getResponseDiffs(), context.getResponseDiffSelection(), isDark);

        highlighter.restoreScrollPositions(scrollPositions);
    }

    /**
     * Clears all highlighting from the editors.
     */
    private void clearHighlighting() {
        DiffHighlighter highlighter = context.getDiffHighlighter();
        highlighter.clearHighlighting(context.getRequestPane());
        highlighter.clearHighlighting(context.getResponsePane());
        highlighter.clearHighlighting(context.getRequestPaneEndpoint());
        highlighter.clearHighlighting(context.getResponsePaneEndpoint());
    }

    /**
     * Scrolls the request editor to the selected difference.
     */
    private void scrollToSelectedRequestDiff() {
        int selectedRow = context.getRequestMatchTable().getSelectedRow();
        if (selectedRow < 0 || context.getRequestDiffs().isEmpty()) return;

        int modelRow = context.getRequestMatchTable().convertRowIndexToModel(selectedRow);
        if (modelRow < 0 || modelRow >= context.getRequestDiffs().size()) return;

        DiffSegment diff = context.getRequestDiffs().get(modelRow);
        DiffHighlighter highlighter = context.getDiffHighlighter();
        if (diff.isOriginal()) {
            highlighter.scrollToPosition(context.getRequestPaneEndpoint(), diff.getStartOffset());
        } else {
            highlighter.scrollToPosition(context.getRequestPane(), diff.getStartOffset());
        }
    }

    /**
     * Scrolls the response editor to the selected difference.
     */
    private void scrollToSelectedResponseDiff() {
        int selectedRow = context.getResponseMatchTable().getSelectedRow();
        if (selectedRow < 0 || context.getResponseDiffs().isEmpty()) return;

        int modelRow = context.getResponseMatchTable().convertRowIndexToModel(selectedRow);
        if (modelRow < 0 || modelRow >= context.getResponseDiffs().size()) return;

        DiffSegment diff = context.getResponseDiffs().get(modelRow);
        DiffHighlighter highlighter = context.getDiffHighlighter();
        if (diff.isOriginal()) {
            highlighter.scrollToPosition(context.getResponsePaneEndpoint(), diff.getStartOffset());
        } else {
            highlighter.scrollToPosition(context.getResponsePane(), diff.getStartOffset());
        }
    }

    /**
     * Returns the request text for an entry, with line endings normalized.
     */
    private String getRequestText(HttpLogEntry entry) {
        if (entry == null) return "";
        String text = context.isHexMode() ? HexDumpConverter.toHexDump(entry.getRequestStr()) : entry.getRequestStr();
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * Returns the response text for an entry, with line endings normalized.
     */
    private String getResponseText(HttpLogEntry entry) {
        if (entry == null) return "";
        String text = context.isHexMode() ? HexDumpConverter.toHexDump(entry.getResponseStr()) : entry.getResponseStr();
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * Recalculates all diffs and table markings in a background thread.
     */
    private void recalculateAllInBackground() {
        if (context.isExtensionUnloading()) return;
        if (context.getCurrentTargetEntry() == null) {
            clearAllDifferenceMarks();
            return;
        }

        final int currentVersion = context.getHighlightingVersion().incrementAndGet();
        final HttpLogEntry target = context.getCurrentTargetEntry();

        SwingUtilities.invokeLater(() -> context.getStatusLabel().setText("Highlighting differences..."));

        new Thread(() -> {
            try {
                if (context.isExtensionUnloading()) return;
                SwingUtilities.invokeLater(this::calculateAndDisplayDiffs);

                Map<HttpLogEntry, RowDiffType> results = new HashMap<>();
                DiffCalculator calculator = context.getDiffCalculator();

                for (HttpLogEntry entry : context.getLogEntries()) {
                    if (context.isExtensionUnloading() || context.getHighlightingVersion().get() != currentVersion) return;
                    results.put(entry, calculator.getDiffType(target, entry, true, true));
                }

                if (context.isExtensionUnloading() || context.getHighlightingVersion().get() != currentVersion) return;

                SwingUtilities.invokeLater(() -> {
                    if (!context.isExtensionUnloading() && context.getHighlightingVersion().get() == currentVersion) {
                        for (Map.Entry<HttpLogEntry, RowDiffType> e : results.entrySet()) {
                            e.getKey().setRowDiffType(e.getValue());
                        }
                        context.getStatusLabel().setText("");
                        context.getRequestTable().repaint();
                    }
                });
            } catch (Exception e) {
                api.logging().logToError("[DiffHunter] Error in background highlighting: " + e.getMessage());
                SwingUtilities.invokeLater(() -> context.getStatusLabel().setText(""));
            }
        }).start();
    }

    /**
     * Marks table rows as different based on comparison with target entry.
     */
    private void markTableDifferences() {
        if (context.isExtensionUnloading()) return;
        if (context.getCurrentTargetEntry() == null) {
            clearAllDifferenceMarks();
            return;
        }

        final int currentVersion = context.getHighlightingVersion().incrementAndGet();
        final HttpLogEntry target = context.getCurrentTargetEntry();

        SwingUtilities.invokeLater(() -> context.getStatusLabel().setText("Highlighting differences..."));

        new Thread(() -> {
            try {
                if (context.isExtensionUnloading()) return;
                Map<HttpLogEntry, RowDiffType> results = new HashMap<>();
                DiffCalculator calculator = context.getDiffCalculator();

                for (HttpLogEntry entry : context.getLogEntries()) {
                    if (context.isExtensionUnloading() || context.getHighlightingVersion().get() != currentVersion) return;
                    results.put(entry, calculator.getDiffType(target, entry, true, true));
                }

                if (context.isExtensionUnloading() || context.getHighlightingVersion().get() != currentVersion) return;

                SwingUtilities.invokeLater(() -> {
                    if (!context.isExtensionUnloading() && context.getHighlightingVersion().get() == currentVersion) {
                        for (Map.Entry<HttpLogEntry, RowDiffType> e : results.entrySet()) {
                            e.getKey().setRowDiffType(e.getValue());
                        }
                        context.getStatusLabel().setText("");
                        context.getRequestTable().repaint();
                    }
                });
            } catch (Exception e) {
                api.logging().logToError("[DiffHunter] Error in background table marking: " + e.getMessage());
                SwingUtilities.invokeLater(() -> context.getStatusLabel().setText(""));
            }
        }).start();
    }

    /**
     * Clears all difference marks from table rows.
     */
    private void clearAllDifferenceMarks() {
        for (HttpLogEntry entry : context.getLogEntries()) {
            entry.setRowDiffType(RowDiffType.NONE);
        }
        SwingUtilities.invokeLater(() -> {
            context.getStatusLabel().setText("");
            context.getRequestTable().repaint();
        });
    }

    /**
     * Processes pending HTTP entries and adds them to the table.
     */
    private void processPendingEntries() {
        if (context.getPendingEntries().isEmpty()) return;

        List<HttpLogEntry> toProcess;
        synchronized (context.getPendingEntries()) {
            toProcess = new ArrayList<>(context.getPendingEntries());
            context.getPendingEntries().clear();
        }

        int excess = context.getLogEntries().size() + toProcess.size() - context.getMaxLogEntries();
        if (excess > 0) {
            try {
                removeOldestEntries(excess);
            } catch (Exception e) {
                api.logging().logToError("[DiffHunter] Error removing old entries: " + e.getMessage());
            }
        }

        for (HttpLogEntry entry : toProcess) {
            try {
                addEntryToTable(entry);
            } catch (Exception e) {
                api.logging().logToError("[DiffHunter] Error adding entry: " + e.getMessage());
            }
        }
    }

    /**
     * Removes the oldest non-marked entries to stay within the log limit.
     */
    private void removeOldestEntries(int count) {
        List<HttpLogEntry> toRemove = new ArrayList<>();
        for (HttpLogEntry entry : context.getLogEntries()) {
            if (toRemove.size() >= count) break;

            if (entry.isMarked()) {
                continue;
            }

            if (context.getCurrentSelectedEntry() != null &&
                entry.getNumber() == context.getCurrentSelectedEntry().getNumber()) {
                continue;
            }

            toRemove.add(entry);
        }

        for (HttpLogEntry entry : toRemove) {
            try {
                synchronized (context.getWriteLock()) {
                    context.getLogEntries().remove(entry);
                    context.getLogEntriesMap().remove(entry.getNumber());
                }

                for (int row = 0; row < context.getTableModel().getRowCount(); row++) {
                    if (entry.getNumber() == (Integer) context.getTableModel().getValueAt(row, 0)) {
                        context.getTableModel().removeRow(row);
                        break;
                    }
                }
            } catch (Exception e) {
                api.logging().logToError("[DiffHunter] Error removing entry: " + e.getMessage());
            }
        }
    }

    /**
     * Adds an HTTP log entry to the table with difference marking.
     */
    private void addEntryToTable(HttpLogEntry entry) {
        if (context.getCurrentTargetEntry() != null) {
            entry.setRowDiffType(context.getDiffCalculator().getDiffType(
                    context.getCurrentTargetEntry(), entry, true, true));
        }

        Object[] row = {
                entry.getNumber(),
                Constants.DATE_FORMAT.get().format(entry.getTimestamp()),
                entry.getTool(),
                entry.getMethod(),
                entry.getHost(),
                entry.getPath(),
                entry.getQuery().isEmpty() ? "" : "?" + entry.getQuery(),
                entry.getStatusCode(),
                entry.getLength(),
                entry.getResponseTime(),
                entry.isMarked()
        };

        context.getTableModel().addRow(row);
    }

    /**
     * Clears all log entries and resets the UI state.
     */
    private void clearLog() {
        synchronized (context.getWriteLock()) {
            context.getLogEntries().clear();
            context.getLogEntriesMap().clear();
            context.getPendingEntries().clear();
            context.resetRequestCounter();
        }

        context.getTargetEntries().clear();
        context.getRequestDiffs().clear();
        context.getResponseDiffs().clear();
        context.getRequestDiffSelection().clear();
        context.getResponseDiffSelection().clear();
        context.setCurrentTargetEntry(null);
        context.setCurrentSelectedEntry(null);

        context.getTableModel().setRowCount(0);
        context.getRequestMatchTableModel().setRowCount(0);
        context.getResponseMatchTableModel().setRowCount(0);
        clearHighlighting();

        if (context.getHostFilterCombo() != null) context.getHostFilterCombo().removeAllItems();
        if (context.getEndpointFilterCombo() != null) context.getEndpointFilterCombo().removeAllItems();
        if (context.getTableFilterField() != null) context.getTableFilterField().setText("");

        if (context.getRequestPane() != null) context.getRequestPane().setText("");
        if (context.getResponsePane() != null) context.getResponsePane().setText("");
        if (context.getRequestPaneEndpoint() != null) context.getRequestPaneEndpoint().setText("");
        if (context.getResponsePaneEndpoint() != null) context.getResponsePaneEndpoint().setText("");
    }

    /**
     * Cleans up resources when the extension is unloaded.
     */
    private void cleanupResources() {
        context.setExtensionUnloading(true);
        context.getHighlightingVersion().incrementAndGet();

        if (context.getBatchUpdateTimer() != null) {
            context.getBatchUpdateTimer().stop();
        }

        UIManager.removePropertyChangeListener(themeChangeListener);
        context.getContextMenus().clear();

        synchronized (context.getWriteLock()) {
            context.getLogEntries().clear();
            context.getLogEntriesMap().clear();
            context.getPendingEntries().clear();
        }
        context.getTargetEntries().clear();
        context.getRequestDiffs().clear();
        context.getResponseDiffs().clear();
        context.getRequestDiffSelection().clear();
        context.getResponseDiffSelection().clear();

        context.setCurrentTargetEntry(null);
        context.setCurrentSelectedEntry(null);
        context.setApi(null);

        api.logging().logToOutput(Constants.EXTENSION_NAME + " Extension unloaded successfully!");
    }
}

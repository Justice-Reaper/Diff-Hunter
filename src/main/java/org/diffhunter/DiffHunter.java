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
import org.diffhunter.model.TargetExclusions;
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
    private HttpCaptureHandler httpHandler;
    private MatchPanel matchPanel;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        context.setApi(api);

        initializeTheme();

        ToolTipManager.sharedInstance().setInitialDelay(1000);

        api.extension().setName(Constants.EXTENSION_NAME);

        httpHandler = new HttpCaptureHandler(api, context);
        api.http().registerHttpHandler(httpHandler);

        themeChangeListener = this::onLookAndFeelChanged;
        UIManager.addPropertyChangeListener(themeChangeListener);

        api.extension().registerUnloadingHandler(this::cleanupResources);

        SwingUtilities.invokeLater(() -> {
            JPanel mainPanel = createUI();
            api.userInterface().registerSuiteTab(Constants.EXTENSION_NAME, mainPanel);

            javax.swing.Timer batchUpdateTimer = new javax.swing.Timer(
                    Constants.BATCH_UPDATE_INTERVAL_MS, e -> {
                        if (context.isExtensionUnloading()) {
                            ((javax.swing.Timer) e.getSource()).stop();
                            return;
                        }
                        processPendingEntries();
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

        matchPanel = new MatchPanel(
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

        context.setExclusionsChangedCallback(this::recalculateAllInBackground);

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

        context.setFiltering(true);
        try {
            Pattern pattern = null;
            String literalFilter = null;

            if (!filterText.isEmpty()) {
                if (useRegex) {
                    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                    pattern = Pattern.compile(filterText, flags);
                } else {
                    literalFilter = caseSensitive ? filterText : filterText.toLowerCase();
                }
            }

            final Pattern finalPattern = pattern;
            final String finalLiteralFilter = literalFilter;
            final boolean finalCaseSensitive = caseSensitive;

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
                        boolean matches = matchesRegex(logEntry, finalPattern);
                        return negative != matches;
                    }

                    if (finalLiteralFilter != null) {
                        boolean matches = matchesLiteral(logEntry, finalLiteralFilter, finalCaseSensitive);
                        return negative != matches;
                    }

                    return true;
                }
            });

            context.getTableFilterField().setBackground(context.getColorBackground());
        } catch (Exception e) {
            context.getTableFilterField().setBackground(Constants.COLOR_SEARCH_ERROR);
        } finally {
            context.setFiltering(false);
        }
    }

    /**
     * Checks if a log entry matches a regex pattern.
     */
    private boolean matchesRegex(HttpLogEntry logEntry, Pattern pattern) {
        if (context.isFilterRequests() && context.isFilterResponses()) {
            return pattern.matcher(logEntry.getRequestStr()).find() ||
                   pattern.matcher(logEntry.getResponseStr()).find();
        } else if (context.isFilterRequests()) {
            return pattern.matcher(logEntry.getRequestStr()).find();
        } else if (context.isFilterResponses()) {
            return pattern.matcher(logEntry.getResponseStr()).find();
        }
        return true;
    }

    /**
     * Checks if a log entry contains a literal string using String.contains().
     */
    private boolean matchesLiteral(HttpLogEntry logEntry, String filter, boolean caseSensitive) {
        if (context.isFilterRequests() && context.isFilterResponses()) {
            return containsLiteral(logEntry.getRequestStr(), filter, caseSensitive) ||
                   containsLiteral(logEntry.getResponseStr(), filter, caseSensitive);
        } else if (context.isFilterRequests()) {
            return containsLiteral(logEntry.getRequestStr(), filter, caseSensitive);
        } else if (context.isFilterResponses()) {
            return containsLiteral(logEntry.getResponseStr(), filter, caseSensitive);
        }
        return true;
    }

    private boolean containsLiteral(String text, String filter, boolean caseSensitive) {
        return caseSensitive ? text.contains(filter) : text.toLowerCase().contains(filter);
    }

    /**
     * Builds a mapping from parentLineIndex to sequential table row index.
     * Used to correctly map character-level diffs to their show/hide state in the diff tables.
     */
    private Map<Integer, Integer> buildParentIndexMap(List<DiffSegment> lineDiffs) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < lineDiffs.size(); i++) {
            map.put(lineDiffs.get(i).getParentLineIndex(), i);
        }
        return map;
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
            clearAllDiffTables();
            clearAllDifferenceMarks();
            refreshExclusionsPanel();
            calculateAndDisplayDiffs();
            return;
        }

        for (HttpLogEntry entry : context.getTargetEntries().values()) {
            if (entry.getHost().equals(selectedHost) && entry.getEndpoint().equals(selectedEndpoint)) {
                context.setCurrentTargetEntry(entry);
                if (context.getRequestPaneEndpoint() != null) {
                    context.getRequestPaneEndpoint().setText(getRequestText(entry));
                    context.getRequestPaneEndpoint().setCaretPosition(0);
                }
                if (context.getResponsePaneEndpoint() != null) {
                    context.getResponsePaneEndpoint().setText(getResponseText(entry));
                    context.getResponsePaneEndpoint().setCaretPosition(0);
                }
                refreshExclusionsPanel();
                calculateAndDisplayDiffs();
                markTableDifferences();
                break;
            }
        }
    }

    /**
     * Refreshes the exclusions panel to show current target's exclusions.
     */
    private void refreshExclusionsPanel() {
        if (matchPanel != null && matchPanel.getExclusionsPanel() != null) {
            matchPanel.getExclusionsPanel().refreshTables();
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
            selectEndpointInTable();
        } else if (wasEmpty || context.getCurrentTargetEntry() == null ||
                   !context.getTargetEntries().containsKey(context.getCurrentTargetEntry().getNumber())) {
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
                calculateAndDisplayDiffs();
            }
        } else {
            context.setCurrentSelectedEntry(null);
            clearHighlighting();
        }
    }

    /**
     * Calculates differences between selected and target entries and updates the UI.
     * Tables always show line-level diffs, while editor highlighting respects the diff mode setting.
     */
    private void calculateAndDisplayDiffs() {
        clearAllDiffTables();

        if (context.getCurrentSelectedEntry() == null) {
            clearHighlighting();
            return;
        }

        String selectedRequestText = getRequestText(context.getCurrentSelectedEntry());
        String selectedResponseText = getResponseText(context.getCurrentSelectedEntry());

        if (context.getCurrentTargetEntry() == null) {
            setTextWithoutHighlighting(context.getRequestPane(), selectedRequestText);
            setTextWithoutHighlighting(context.getResponsePane(), selectedResponseText);
            setTextWithoutHighlighting(context.getRequestPaneEndpoint(), "");
            setTextWithoutHighlighting(context.getResponsePaneEndpoint(), "");
            return;
        }

        Component rootComponent = context.getRequestTable().getTopLevelAncestor();
        if (rootComponent != null) {
            rootComponent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        String targetRequestText = getRequestText(context.getCurrentTargetEntry());
        String targetResponseText = getResponseText(context.getCurrentTargetEntry());

        DiffCalculator calculator = context.getDiffCalculator();

        List<DiffSegment> reqDiffsForTables = new ArrayList<>();
        List<DiffSegment> respDiffsForTables = new ArrayList<>();

        Thread requestThread = new Thread(() -> {
            try {
                reqDiffsForTables.addAll(calculator.findDifferences(targetRequestText, selectedRequestText, false));
            } catch (Exception e) {
                api.logging().logToError("[DiffHunter] Error calculating request diffs: " + e.getMessage());
            }
        });

        Thread responseThread = new Thread(() -> {
            try {
                respDiffsForTables.addAll(calculator.findDifferences(targetResponseText, selectedResponseText, false));
            } catch (Exception e) {
                api.logging().logToError("[DiffHunter] Error calculating response diffs: " + e.getMessage());
            }
        });

        requestThread.start();
        responseThread.start();

        try {
            requestThread.join();
            responseThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        populateDiffTables(reqDiffsForTables, respDiffsForTables);

        applyHighlightingToEditorsWithTexts(selectedRequestText, selectedResponseText,
                targetRequestText, targetResponseText);

        if (rootComponent != null) {
            rootComponent.setCursor(Cursor.getDefaultCursor());
        }
    }

    /**
     * Populates the diff tables with separated Selected and Target differences.
     * Filters out diffs that match enabled exclusion rules.
     */
    private void populateDiffTables(List<DiffSegment> reqDiffs, List<DiffSegment> respDiffs) {
        int reqSelectedIdx = 0;
        int reqTargetIdx = 0;
        int respSelectedIdx = 0;
        int respTargetIdx = 0;

        TargetExclusions exclusions = context.getCurrentTargetExclusions();

        for (DiffSegment diff : reqDiffs) {
            if (exclusions != null && exclusions.matchesRequestExclusion(diff.getContent())) {
                continue;
            }
            if (diff.isOriginal()) {
                context.getRequestTargetDiffs().add(diff);
                context.getRequestTargetDiffSelection().put(reqTargetIdx++, Boolean.TRUE);
                context.getRequestTargetMatchTableModel().addRow(new Object[]{diff.getLineNumber(), diff.getStatusString(), diff.toString(), Boolean.TRUE});
            } else {
                context.getRequestSelectedDiffs().add(diff);
                context.getRequestSelectedDiffSelection().put(reqSelectedIdx++, Boolean.TRUE);
                context.getRequestSelectedMatchTableModel().addRow(new Object[]{diff.getLineNumber(), diff.getStatusString(), diff.toString(), Boolean.TRUE});
            }
        }

        for (DiffSegment diff : respDiffs) {
            if (exclusions != null && exclusions.matchesResponseExclusion(diff.getContent())) {
                continue;
            }
            if (diff.isOriginal()) {
                context.getResponseTargetDiffs().add(diff);
                context.getResponseTargetDiffSelection().put(respTargetIdx++, Boolean.TRUE);
                context.getResponseTargetMatchTableModel().addRow(new Object[]{diff.getLineNumber(), diff.getStatusString(), diff.toString(), Boolean.TRUE});
            } else {
                context.getResponseSelectedDiffs().add(diff);
                context.getResponseSelectedDiffSelection().put(respSelectedIdx++, Boolean.TRUE);
                context.getResponseSelectedMatchTableModel().addRow(new Object[]{diff.getLineNumber(), diff.getStatusString(), diff.toString(), Boolean.TRUE});
            }
        }
    }

    /**
     * Clears all diff tables and selections.
     */
    private void clearAllDiffTables() {
        if (context.getRequestSelectedMatchTableModel() != null)
            context.getRequestSelectedMatchTableModel().setRowCount(0);
        if (context.getRequestTargetMatchTableModel() != null)
            context.getRequestTargetMatchTableModel().setRowCount(0);
        if (context.getResponseSelectedMatchTableModel() != null)
            context.getResponseSelectedMatchTableModel().setRowCount(0);
        if (context.getResponseTargetMatchTableModel() != null)
            context.getResponseTargetMatchTableModel().setRowCount(0);

        context.getRequestSelectedDiffs().clear();
        context.getRequestTargetDiffs().clear();
        context.getResponseSelectedDiffs().clear();
        context.getResponseTargetDiffs().clear();

        context.getRequestSelectedDiffSelection().clear();
        context.getRequestTargetDiffSelection().clear();
        context.getResponseSelectedDiffSelection().clear();
        context.getResponseTargetDiffSelection().clear();
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
                targetRequestText, targetResponseText, true);
    }

    /**
     * Applies highlighting to editors with the provided texts.
     * In Line Diff mode, uses stored line-level diffs with selection map.
     * In Character Diff mode, calculates character-level diffs for more detailed highlighting.
     */
    private void applyHighlightingToEditorsWithTexts(String selectedRequestText, String selectedResponseText,
                                                      String targetRequestText, String targetResponseText) {
        applyHighlightingToEditorsWithTexts(selectedRequestText, selectedResponseText,
                targetRequestText, targetResponseText, false);
    }

    /**
     * Applies highlighting to editors with the provided texts.
     * When preserveScroll is true, saves and restores scroll positions (used for re-highlighting).
     * When preserveScroll is false, allows scroll to reset to top (used for new content).
     */
    private void applyHighlightingToEditorsWithTexts(String selectedRequestText, String selectedResponseText,
                                                      String targetRequestText, String targetResponseText,
                                                      boolean preserveScroll) {
        DiffHighlighter highlighter = context.getDiffHighlighter();
        Map<JTextPane, Point> scrollPositions = new HashMap<>();
        if (preserveScroll) {
            highlighter.saveScrollPosition(context.getRequestPane(), scrollPositions);
            highlighter.saveScrollPosition(context.getResponsePane(), scrollPositions);
            highlighter.saveScrollPosition(context.getRequestPaneEndpoint(), scrollPositions);
            highlighter.saveScrollPosition(context.getResponsePaneEndpoint(), scrollPositions);
        }

        boolean isDark = context.isDarkTheme();
        boolean charLevelDiff = context.isCharacterLevelDiff();

        if (charLevelDiff) {
            DiffCalculator calculator = context.getDiffCalculator();
            List<DiffSegment> reqDiffs = calculator.findDifferences(targetRequestText, selectedRequestText, true);
            List<DiffSegment> respDiffs = calculator.findDifferences(targetResponseText, selectedResponseText, true);

            Map<Integer, Integer> reqSelectedIndexMap = buildParentIndexMap(context.getRequestSelectedDiffs());
            Map<Integer, Integer> reqTargetIndexMap = buildParentIndexMap(context.getRequestTargetDiffs());
            Map<Integer, Integer> respSelectedIndexMap = buildParentIndexMap(context.getResponseSelectedDiffs());
            Map<Integer, Integer> respTargetIndexMap = buildParentIndexMap(context.getResponseTargetDiffs());

            List<DiffSegment> visibleReqSelectedDiffs = new ArrayList<>();
            List<DiffSegment> visibleReqTargetDiffs = new ArrayList<>();
            List<DiffSegment> visibleRespSelectedDiffs = new ArrayList<>();
            List<DiffSegment> visibleRespTargetDiffs = new ArrayList<>();

            for (DiffSegment diff : reqDiffs) {
                int parentIdx = diff.getParentLineIndex();
                if (diff.isOriginal()) {
                    Integer tableRow = reqTargetIndexMap.get(parentIdx);
                    if (tableRow != null && context.getRequestTargetDiffSelection().getOrDefault(tableRow, true)) {
                        visibleReqTargetDiffs.add(diff);
                    }
                } else {
                    Integer tableRow = reqSelectedIndexMap.get(parentIdx);
                    if (tableRow != null && context.getRequestSelectedDiffSelection().getOrDefault(tableRow, true)) {
                        visibleReqSelectedDiffs.add(diff);
                    }
                }
            }

            for (DiffSegment diff : respDiffs) {
                int parentIdx = diff.getParentLineIndex();
                if (diff.isOriginal()) {
                    Integer tableRow = respTargetIndexMap.get(parentIdx);
                    if (tableRow != null && context.getResponseTargetDiffSelection().getOrDefault(tableRow, true)) {
                        visibleRespTargetDiffs.add(diff);
                    }
                } else {
                    Integer tableRow = respSelectedIndexMap.get(parentIdx);
                    if (tableRow != null && context.getResponseSelectedDiffSelection().getOrDefault(tableRow, true)) {
                        visibleRespSelectedDiffs.add(diff);
                    }
                }
            }

            Map<Integer, Boolean> reqSelectedVisible = new HashMap<>();
            for (int i = 0; i < visibleReqSelectedDiffs.size(); i++) {
                reqSelectedVisible.put(i, Boolean.TRUE);
            }
            Map<Integer, Boolean> reqTargetVisible = new HashMap<>();
            for (int i = 0; i < visibleReqTargetDiffs.size(); i++) {
                reqTargetVisible.put(i, Boolean.TRUE);
            }
            Map<Integer, Boolean> respSelectedVisible = new HashMap<>();
            for (int i = 0; i < visibleRespSelectedDiffs.size(); i++) {
                respSelectedVisible.put(i, Boolean.TRUE);
            }
            Map<Integer, Boolean> respTargetVisible = new HashMap<>();
            for (int i = 0; i < visibleRespTargetDiffs.size(); i++) {
                respTargetVisible.put(i, Boolean.TRUE);
            }

            highlighter.setTextWithHighlighting(context.getRequestPane(), selectedRequestText, false,
                    visibleReqSelectedDiffs, reqSelectedVisible, isDark);
            highlighter.setTextWithHighlighting(context.getResponsePane(), selectedResponseText, false,
                    visibleRespSelectedDiffs, respSelectedVisible, isDark);
            highlighter.setTextWithHighlighting(context.getRequestPaneEndpoint(), targetRequestText, true,
                    visibleReqTargetDiffs, reqTargetVisible, isDark);
            highlighter.setTextWithHighlighting(context.getResponsePaneEndpoint(), targetResponseText, true,
                    visibleRespTargetDiffs, respTargetVisible, isDark);
        } else {
            highlighter.setTextWithHighlighting(context.getRequestPane(), selectedRequestText, false,
                    context.getRequestSelectedDiffs(), context.getRequestSelectedDiffSelection(), isDark);
            highlighter.setTextWithHighlighting(context.getResponsePane(), selectedResponseText, false,
                    context.getResponseSelectedDiffs(), context.getResponseSelectedDiffSelection(), isDark);
            highlighter.setTextWithHighlighting(context.getRequestPaneEndpoint(), targetRequestText, true,
                    context.getRequestTargetDiffs(), context.getRequestTargetDiffSelection(), isDark);
            highlighter.setTextWithHighlighting(context.getResponsePaneEndpoint(), targetResponseText, true,
                    context.getResponseTargetDiffs(), context.getResponseTargetDiffSelection(), isDark);
        }

        if (preserveScroll) {
            highlighter.restoreScrollPositions(scrollPositions);
        }
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
     * Sets text on a JTextPane without highlighting, only if the text has changed.
     * This is more efficient than setText() as it avoids unnecessary document events.
     */
    private void setTextWithoutHighlighting(JTextPane pane, String text) {
        if (pane == null) return;
        try {
            javax.swing.text.Document doc = pane.getDocument();
            String currentText = doc.getText(0, doc.getLength());
            if (!currentText.equals(text)) {
                pane.setText(text);
                pane.setCaretPosition(0);
            }
            pane.getHighlighter().removeAllHighlights();
        } catch (javax.swing.text.BadLocationException e) {
            pane.setText(text);
            pane.setCaretPosition(0);
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
     * Scrolls the request editor to the selected difference.
     */
    private void scrollToSelectedRequestDiff() {
        DiffHighlighter highlighter = context.getDiffHighlighter();

        JTable selectedTable = context.getRequestSelectedMatchTable();
        if (selectedTable != null && selectedTable.getSelectedRow() >= 0) {
            int modelRow = selectedTable.convertRowIndexToModel(selectedTable.getSelectedRow());
            if (modelRow >= 0 && modelRow < context.getRequestSelectedDiffs().size()) {
                DiffSegment diff = context.getRequestSelectedDiffs().get(modelRow);
                highlighter.scrollToPosition(context.getRequestPane(), diff.getStartOffset());
                return;
            }
        }

        JTable targetTable = context.getRequestTargetMatchTable();
        if (targetTable != null && targetTable.getSelectedRow() >= 0) {
            int modelRow = targetTable.convertRowIndexToModel(targetTable.getSelectedRow());
            if (modelRow >= 0 && modelRow < context.getRequestTargetDiffs().size()) {
                DiffSegment diff = context.getRequestTargetDiffs().get(modelRow);
                highlighter.scrollToPosition(context.getRequestPaneEndpoint(), diff.getStartOffset());
            }
        }
    }

    /**
     * Scrolls the response editor to the selected difference.
     */
    private void scrollToSelectedResponseDiff() {
        DiffHighlighter highlighter = context.getDiffHighlighter();

        JTable selectedTable = context.getResponseSelectedMatchTable();
        if (selectedTable != null && selectedTable.getSelectedRow() >= 0) {
            int modelRow = selectedTable.convertRowIndexToModel(selectedTable.getSelectedRow());
            if (modelRow >= 0 && modelRow < context.getResponseSelectedDiffs().size()) {
                DiffSegment diff = context.getResponseSelectedDiffs().get(modelRow);
                highlighter.scrollToPosition(context.getResponsePane(), diff.getStartOffset());
                return;
            }
        }

        JTable targetTable = context.getResponseTargetMatchTable();
        if (targetTable != null && targetTable.getSelectedRow() >= 0) {
            int modelRow = targetTable.convertRowIndexToModel(targetTable.getSelectedRow());
            if (modelRow >= 0 && modelRow < context.getResponseTargetDiffs().size()) {
                DiffSegment diff = context.getResponseTargetDiffs().get(modelRow);
                highlighter.scrollToPosition(context.getResponsePaneEndpoint(), diff.getStartOffset());
            }
        }
    }

    /**
     * Returns the request text for an entry.
     * Line endings are already normalized at capture time.
     */
    private String getRequestText(HttpLogEntry entry) {
        if (entry == null) return "";
        if (context.isHexMode()) {
            return HexDumpConverter.toHexDump(entry.getRequestStr());
        }
        return entry.getRequestStr();
    }

    /**
     * Returns the response text for an entry.
     * Line endings are already normalized at capture time.
     */
    private String getResponseText(HttpLogEntry entry) {
        if (entry == null) return "";
        if (context.isHexMode()) {
            return HexDumpConverter.toHexDump(entry.getResponseStr());
        }
        return entry.getResponseStr();
    }

    /**
     * Recalculates all diffs and table markings in a background thread.
     */
    private void recalculateAllInBackground() {
        if (context.isExtensionUnloading()) return;
        if (context.getCurrentTargetEntry() == null) {
            clearAllDifferenceMarks();
            SwingUtilities.invokeLater(this::calculateAndDisplayDiffs);
            return;
        }

        final int currentVersion = context.getHighlightingVersion().incrementAndGet();
        final HttpLogEntry target = context.getCurrentTargetEntry();
        final TargetExclusions exclusions = context.getCurrentTargetExclusions();

        SwingUtilities.invokeLater(() -> context.getStatusLabel().setText("Highlighting differences..."));

        new Thread(() -> {
            try {
                if (context.isExtensionUnloading()) return;
                SwingUtilities.invokeLater(this::calculateAndDisplayDiffs);

                Map<HttpLogEntry, RowDiffType> results = new HashMap<>();
                DiffCalculator calculator = context.getDiffCalculator();

                for (HttpLogEntry entry : context.getLogEntries()) {
                    if (context.isExtensionUnloading() || context.getHighlightingVersion().get() != currentVersion) return;
                    results.put(entry, getDiffTypeWithExclusions(calculator, target, entry, exclusions));
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
        final TargetExclusions exclusions = context.getCurrentTargetExclusions();

        SwingUtilities.invokeLater(() -> context.getStatusLabel().setText("Highlighting differences..."));

        new Thread(() -> {
            try {
                if (context.isExtensionUnloading()) return;
                Map<HttpLogEntry, RowDiffType> results = new HashMap<>();
                DiffCalculator calculator = context.getDiffCalculator();

                for (HttpLogEntry entry : context.getLogEntries()) {
                    if (context.isExtensionUnloading() || context.getHighlightingVersion().get() != currentVersion) return;
                    results.put(entry, getDiffTypeWithExclusions(calculator, target, entry, exclusions));
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
     * Calculates the diff type between target and entry, considering exclusions.
     * If all diffs are excluded, returns NONE.
     */
    private RowDiffType getDiffTypeWithExclusions(DiffCalculator calculator, HttpLogEntry target,
                                                   HttpLogEntry entry, TargetExclusions exclusions) {
        if (target.getNumber() == entry.getNumber()) {
            return RowDiffType.NONE;
        }

        if (exclusions == null || (!exclusions.hasEnabledRequestExclusions() && !exclusions.hasEnabledResponseExclusions())) {
            return calculator.getDiffType(target, entry, true, true);
        }

        boolean requestDiffers = false;
        boolean responseDiffers = false;

        String targetRequest = getRequestText(target);
        String entryRequest = getRequestText(entry);
        if (!targetRequest.equals(entryRequest)) {
            List<DiffSegment> reqDiffs = calculator.findDifferences(targetRequest, entryRequest, false);
            for (DiffSegment diff : reqDiffs) {
                if (!exclusions.matchesRequestExclusion(diff.getContent())) {
                    requestDiffers = true;
                    break;
                }
            }
        }

        String targetResponse = getResponseText(target);
        String entryResponse = getResponseText(entry);
        if (!targetResponse.equals(entryResponse)) {
            List<DiffSegment> respDiffs = calculator.findDifferences(targetResponse, entryResponse, false);
            for (DiffSegment diff : respDiffs) {
                if (!exclusions.matchesResponseExclusion(diff.getContent())) {
                    responseDiffers = true;
                    break;
                }
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

            HttpLogEntry selected = context.getCurrentSelectedEntry();
            if (selected != null && entry.getNumber() == selected.getNumber()) {
                continue;
            }

            toRemove.add(entry);
        }

        for (HttpLogEntry entry : toRemove) {
            try {
                synchronized (context.getWriteLock()) {
                    context.getLogEntries().remove(entry);
                    context.getLogEntriesMap().remove(entry.getNumber());
                    context.getPendingEntries().remove(entry);
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
            TargetExclusions exclusions = context.getCurrentTargetExclusions();
            entry.setRowDiffType(getDiffTypeWithExclusions(
                    context.getDiffCalculator(), context.getCurrentTargetEntry(), entry, exclusions));
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
        context.getTargetExclusionsMap().clear();
        clearAllDiffTables();
        context.setCurrentTargetEntry(null);
        context.setCurrentSelectedEntry(null);
        refreshExclusionsPanel();

        context.getTableModel().setRowCount(0);
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

        if (httpHandler != null) {
            httpHandler.cleanup();
        }

        UIManager.removePropertyChangeListener(themeChangeListener);
        context.getContextMenus().clear();

        synchronized (context.getWriteLock()) {
            context.getLogEntries().clear();
            context.getLogEntriesMap().clear();
            context.getPendingEntries().clear();
        }
        context.getTargetEntries().clear();
        context.getRequestSelectedDiffs().clear();
        context.getRequestTargetDiffs().clear();
        context.getResponseSelectedDiffs().clear();
        context.getResponseTargetDiffs().clear();
        context.getRequestSelectedDiffSelection().clear();
        context.getRequestTargetDiffSelection().clear();
        context.getResponseSelectedDiffSelection().clear();
        context.getResponseTargetDiffSelection().clear();

        context.setCurrentTargetEntry(null);
        context.setCurrentSelectedEntry(null);
        context.setApi(null);

        api.logging().logToOutput(Constants.EXTENSION_NAME + " Extension unloaded successfully!");
    }
}

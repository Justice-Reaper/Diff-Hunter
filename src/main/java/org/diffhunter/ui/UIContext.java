package org.diffhunter.ui;

import org.diffhunter.diff.DiffCalculator;
import org.diffhunter.diff.DiffHighlighter;
import org.diffhunter.model.DiffSegment;
import org.diffhunter.model.HttpLogEntry;
import org.diffhunter.model.TargetExclusions;
import org.diffhunter.util.Constants;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared UI context that holds all state and components.
 */
public class UIContext {

    private burp.api.montoya.MontoyaApi api;

    private JTable requestTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> tableSorter;
    private JTable requestSelectedMatchTable;
    private JTable requestTargetMatchTable;
    private JTable responseSelectedMatchTable;
    private JTable responseTargetMatchTable;
    private DefaultTableModel requestSelectedMatchTableModel;
    private DefaultTableModel requestTargetMatchTableModel;
    private DefaultTableModel responseSelectedMatchTableModel;
    private DefaultTableModel responseTargetMatchTableModel;

    private JTextPane requestPane;
    private JTextPane responsePane;
    private JTextPane requestPaneEndpoint;
    private JTextPane responsePaneEndpoint;

    private JTextField tableFilterField;
    private JCheckBox tableFilterCaseSensitive;
    private JCheckBox tableFilterRegex;
    private JCheckBox tableFilterNegative;
    private JComboBox<String> hostFilterCombo;
    private JComboBox<String> endpointFilterCombo;

    private JLabel statusLabel;

    private boolean showRequestDiff = true;
    private boolean showResponseDiff = true;
    private boolean showBothDiff = true;
    private boolean showNoDiff = true;

    private boolean filterRequests = true;
    private boolean filterResponses = true;
    private volatile boolean updatingCombos = false;
    private volatile boolean editingCheckbox = false;
    private volatile boolean filtering = false;

    private final List<HttpLogEntry> logEntries = new CopyOnWriteArrayList<>();
    private final Map<Integer, HttpLogEntry> logEntriesMap = new ConcurrentHashMap<>();
    private final Map<Integer, HttpLogEntry> targetEntries = new ConcurrentHashMap<>();
    private final List<DiffSegment> requestSelectedDiffs = new ArrayList<>();
    private final List<DiffSegment> requestTargetDiffs = new ArrayList<>();
    private final List<DiffSegment> responseSelectedDiffs = new ArrayList<>();
    private final List<DiffSegment> responseTargetDiffs = new ArrayList<>();
    private final Map<Integer, Boolean> requestSelectedDiffSelection = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> requestTargetDiffSelection = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> responseSelectedDiffSelection = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> responseTargetDiffSelection = new ConcurrentHashMap<>();

    private volatile HttpLogEntry currentTargetEntry = null;
    private volatile HttpLogEntry currentSelectedEntry = null;

    private int requestCounter = 0;
    private volatile int maxLogEntries = Constants.DEFAULT_MAX_LOG_ENTRIES;
    private volatile boolean captureEnabled = false;
    private volatile boolean extensionUnloading = false;
    private final AtomicInteger highlightingVersion = new AtomicInteger(0);
    private final Object writeLock = new Object();

    private final List<HttpLogEntry> pendingEntries = Collections.synchronizedList(new ArrayList<>());
    private javax.swing.Timer batchUpdateTimer;

    private Color colorBackground;
    private Color colorForeground;
    private Font editorFont;
    private boolean darkTheme;
    private boolean characterLevelDiff = false;
    private boolean hexMode = false;

    private final DiffCalculator diffCalculator = new DiffCalculator();
    private final DiffHighlighter diffHighlighter = new DiffHighlighter();
    private final List<JPopupMenu> contextMenus = new ArrayList<>();
    private JCheckBox tableCheckBoxRenderer;
    private JPanel colorBoxModified;
    private JPanel colorBoxDeleted;
    private JPanel colorBoxAdded;

    private final Map<String, TargetExclusions> targetExclusionsMap = new ConcurrentHashMap<>();
    private Runnable exclusionsChangedCallback;

    /** Returns the Burp Suite API reference. */
    public burp.api.montoya.MontoyaApi getApi() { return api; }

    /** Sets the Burp Suite API reference and configures dependent components. */
    public void setApi(burp.api.montoya.MontoyaApi api) {
        this.api = api;
        diffCalculator.setApi(api);
        diffHighlighter.setApi(api);
    }

    /** Returns the main request table. */
    public JTable getRequestTable() { return requestTable; }

    /** Sets the main request table. */
    public void setRequestTable(JTable requestTable) { this.requestTable = requestTable; }

    /** Returns the table model for the request table. */
    public DefaultTableModel getTableModel() { return tableModel; }

    /** Sets the table model for the request table. */
    public void setTableModel(DefaultTableModel tableModel) { this.tableModel = tableModel; }

    /** Returns the row sorter for the request table. */
    public TableRowSorter<DefaultTableModel> getTableSorter() { return tableSorter; }

    /** Sets the row sorter for the request table. */
    public void setTableSorter(TableRowSorter<DefaultTableModel> tableSorter) { this.tableSorter = tableSorter; }

    /** Returns the request selected differences table. */
    public JTable getRequestSelectedMatchTable() { return requestSelectedMatchTable; }

    /** Sets the request selected differences table. */
    public void setRequestSelectedMatchTable(JTable table) { this.requestSelectedMatchTable = table; }

    /** Returns the request target differences table. */
    public JTable getRequestTargetMatchTable() { return requestTargetMatchTable; }

    /** Sets the request target differences table. */
    public void setRequestTargetMatchTable(JTable table) { this.requestTargetMatchTable = table; }

    /** Returns the response selected differences table. */
    public JTable getResponseSelectedMatchTable() { return responseSelectedMatchTable; }

    /** Sets the response selected differences table. */
    public void setResponseSelectedMatchTable(JTable table) { this.responseSelectedMatchTable = table; }

    /** Returns the response target differences table. */
    public JTable getResponseTargetMatchTable() { return responseTargetMatchTable; }

    /** Sets the response target differences table. */
    public void setResponseTargetMatchTable(JTable table) { this.responseTargetMatchTable = table; }

    /** Returns the table model for request selected differences. */
    public DefaultTableModel getRequestSelectedMatchTableModel() { return requestSelectedMatchTableModel; }

    /** Sets the table model for request selected differences. */
    public void setRequestSelectedMatchTableModel(DefaultTableModel model) { this.requestSelectedMatchTableModel = model; }

    /** Returns the table model for request target differences. */
    public DefaultTableModel getRequestTargetMatchTableModel() { return requestTargetMatchTableModel; }

    /** Sets the table model for request target differences. */
    public void setRequestTargetMatchTableModel(DefaultTableModel model) { this.requestTargetMatchTableModel = model; }

    /** Returns the table model for response selected differences. */
    public DefaultTableModel getResponseSelectedMatchTableModel() { return responseSelectedMatchTableModel; }

    /** Sets the table model for response selected differences. */
    public void setResponseSelectedMatchTableModel(DefaultTableModel model) { this.responseSelectedMatchTableModel = model; }

    /** Returns the table model for response target differences. */
    public DefaultTableModel getResponseTargetMatchTableModel() { return responseTargetMatchTableModel; }

    /** Sets the table model for response target differences. */
    public void setResponseTargetMatchTableModel(DefaultTableModel model) { this.responseTargetMatchTableModel = model; }

    /** Returns the request editor pane for the selected entry. */
    public JTextPane getRequestPane() { return requestPane; }

    /** Sets the request editor pane for the selected entry. */
    public void setRequestPane(JTextPane requestPane) { this.requestPane = requestPane; }

    /** Returns the response editor pane for the selected entry. */
    public JTextPane getResponsePane() { return responsePane; }

    /** Sets the response editor pane for the selected entry. */
    public void setResponsePane(JTextPane responsePane) { this.responsePane = responsePane; }

    /** Returns the request editor pane for the target entry. */
    public JTextPane getRequestPaneEndpoint() { return requestPaneEndpoint; }

    /** Sets the request editor pane for the target entry. */
    public void setRequestPaneEndpoint(JTextPane requestPaneEndpoint) { this.requestPaneEndpoint = requestPaneEndpoint; }

    /** Returns the response editor pane for the target entry. */
    public JTextPane getResponsePaneEndpoint() { return responsePaneEndpoint; }

    /** Sets the response editor pane for the target entry. */
    public void setResponsePaneEndpoint(JTextPane responsePaneEndpoint) { this.responsePaneEndpoint = responsePaneEndpoint; }

    /** Returns the table filter text field. */
    public JTextField getTableFilterField() { return tableFilterField; }

    /** Sets the table filter text field. */
    public void setTableFilterField(JTextField tableFilterField) { this.tableFilterField = tableFilterField; }

    /** Returns the case sensitive filter checkbox. */
    public JCheckBox getTableFilterCaseSensitive() { return tableFilterCaseSensitive; }

    /** Sets the case sensitive filter checkbox. */
    public void setTableFilterCaseSensitive(JCheckBox cb) { this.tableFilterCaseSensitive = cb; }

    /** Returns the regex filter checkbox. */
    public JCheckBox getTableFilterRegex() { return tableFilterRegex; }

    /** Sets the regex filter checkbox. */
    public void setTableFilterRegex(JCheckBox cb) { this.tableFilterRegex = cb; }

    /** Returns the negative filter checkbox. */
    public JCheckBox getTableFilterNegative() { return tableFilterNegative; }

    /** Sets the negative filter checkbox. */
    public void setTableFilterNegative(JCheckBox cb) { this.tableFilterNegative = cb; }

    /** Returns the host filter combo box. */
    public JComboBox<String> getHostFilterCombo() { return hostFilterCombo; }

    /** Sets the host filter combo box. */
    public void setHostFilterCombo(JComboBox<String> hostFilterCombo) { this.hostFilterCombo = hostFilterCombo; }

    /** Returns the endpoint filter combo box. */
    public JComboBox<String> getEndpointFilterCombo() { return endpointFilterCombo; }

    /** Sets the endpoint filter combo box. */
    public void setEndpointFilterCombo(JComboBox<String> endpointFilterCombo) { this.endpointFilterCombo = endpointFilterCombo; }

    /** Returns the status label. */
    public JLabel getStatusLabel() { return statusLabel; }

    /** Returns true if request differences should be shown in the table. */
    public boolean isShowRequestDiff() { return showRequestDiff; }

    /** Sets whether request differences should be shown in the table. */
    public void setShowRequestDiff(boolean show) { this.showRequestDiff = show; }

    /** Returns true if response differences should be shown in the table. */
    public boolean isShowResponseDiff() { return showResponseDiff; }

    /** Sets whether response differences should be shown in the table. */
    public void setShowResponseDiff(boolean show) { this.showResponseDiff = show; }

    /** Returns true if both request and response differences should be shown in the table. */
    public boolean isShowBothDiff() { return showBothDiff; }

    /** Sets whether both request and response differences should be shown in the table. */
    public void setShowBothDiff(boolean show) { this.showBothDiff = show; }

    /** Returns true if entries with no differences should be shown in the table. */
    public boolean isShowNoDiff() { return showNoDiff; }

    /** Sets whether entries with no differences should be shown in the table. */
    public void setShowNoDiff(boolean show) { this.showNoDiff = show; }

    /** Sets the status label. */
    public void setStatusLabel(JLabel statusLabel) { this.statusLabel = statusLabel; }

    /** Returns true if requests should be included in filter search. */
    public boolean isFilterRequests() { return filterRequests; }

    /** Sets whether requests should be included in filter search. */
    public void setFilterRequests(boolean filterRequests) { this.filterRequests = filterRequests; }

    /** Returns true if responses should be included in filter search. */
    public boolean isFilterResponses() { return filterResponses; }

    /** Sets whether responses should be included in filter search. */
    public void setFilterResponses(boolean filterResponses) { this.filterResponses = filterResponses; }

    /** Returns true if combo boxes are being updated programmatically. */
    public boolean isUpdatingCombos() { return updatingCombos; }

    /** Sets the flag indicating combo boxes are being updated programmatically. */
    public void setUpdatingCombos(boolean updatingCombos) { this.updatingCombos = updatingCombos; }

    /** Returns true if a checkbox is currently being edited. */
    public boolean isEditingCheckbox() { return editingCheckbox; }

    /** Sets the flag indicating a checkbox is currently being edited. */
    public void setEditingCheckbox(boolean editingCheckbox) { this.editingCheckbox = editingCheckbox; }

    /** Returns true if a filter is being applied. */
    public boolean isFiltering() { return filtering; }

    /** Sets the flag indicating a filter is being applied. */
    public void setFiltering(boolean filtering) { this.filtering = filtering; }

    /** Returns the list of all HTTP log entries. */
    public List<HttpLogEntry> getLogEntries() { return logEntries; }

    /** Returns the map of request numbers to HTTP log entries. */
    public Map<Integer, HttpLogEntry> getLogEntriesMap() { return logEntriesMap; }

    /** Returns the map of marked target entries. */
    public Map<Integer, HttpLogEntry> getTargetEntries() { return targetEntries; }

    /** Returns the list of request selected differences. */
    public List<DiffSegment> getRequestSelectedDiffs() { return requestSelectedDiffs; }

    /** Returns the list of request target differences. */
    public List<DiffSegment> getRequestTargetDiffs() { return requestTargetDiffs; }

    /** Returns the list of response selected differences. */
    public List<DiffSegment> getResponseSelectedDiffs() { return responseSelectedDiffs; }

    /** Returns the list of response target differences. */
    public List<DiffSegment> getResponseTargetDiffs() { return responseTargetDiffs; }

    /** Returns the map of request selected difference visibility selections. */
    public Map<Integer, Boolean> getRequestSelectedDiffSelection() { return requestSelectedDiffSelection; }

    /** Returns the map of request target difference visibility selections. */
    public Map<Integer, Boolean> getRequestTargetDiffSelection() { return requestTargetDiffSelection; }

    /** Returns the map of response selected difference visibility selections. */
    public Map<Integer, Boolean> getResponseSelectedDiffSelection() { return responseSelectedDiffSelection; }

    /** Returns the map of response target difference visibility selections. */
    public Map<Integer, Boolean> getResponseTargetDiffSelection() { return responseTargetDiffSelection; }

    /** Returns the currently selected target entry for comparison. */
    public HttpLogEntry getCurrentTargetEntry() { return currentTargetEntry; }

    /** Sets the currently selected target entry for comparison. */
    public void setCurrentTargetEntry(HttpLogEntry currentTargetEntry) { this.currentTargetEntry = currentTargetEntry; }

    /** Returns the currently selected entry in the request table. */
    public HttpLogEntry getCurrentSelectedEntry() { return currentSelectedEntry; }

    /** Sets the currently selected entry in the request table. */
    public void setCurrentSelectedEntry(HttpLogEntry currentSelectedEntry) { this.currentSelectedEntry = currentSelectedEntry; }

    /** Returns the current request counter value. */
    public int getRequestCounter() { return requestCounter; }

    /** Increments and returns the request counter. */
    public int incrementAndGetRequestCounter() { return ++requestCounter; }

    /** Resets the request counter to zero. */
    public void resetRequestCounter() { requestCounter = 0; }

    /** Returns the maximum number of log entries to keep in memory. */
    public int getMaxLogEntries() { return maxLogEntries; }

    /** Sets the maximum number of log entries to keep in memory. */
    public void setMaxLogEntries(int maxLogEntries) { this.maxLogEntries = maxLogEntries; }

    /** Returns true if HTTP capture is enabled. */
    public boolean isCaptureEnabled() { return captureEnabled; }

    /** Sets whether HTTP capture is enabled. */
    public void setCaptureEnabled(boolean captureEnabled) { this.captureEnabled = captureEnabled; }

    /** Returns true if the extension is currently unloading. */
    public boolean isExtensionUnloading() { return extensionUnloading; }

    /** Sets the flag indicating the extension is unloading. */
    public void setExtensionUnloading(boolean extensionUnloading) { this.extensionUnloading = extensionUnloading; }

    /** Returns the highlighting version counter for cancellation checks. */
    public AtomicInteger getHighlightingVersion() { return highlightingVersion; }

    /** Returns the write lock for thread-safe operations. */
    public Object getWriteLock() { return writeLock; }

    /** Returns the list of pending entries to be added to the table. */
    public List<HttpLogEntry> getPendingEntries() { return pendingEntries; }

    /** Returns the batch update timer. */
    public javax.swing.Timer getBatchUpdateTimer() { return batchUpdateTimer; }

    /** Sets the batch update timer. */
    public void setBatchUpdateTimer(javax.swing.Timer batchUpdateTimer) { this.batchUpdateTimer = batchUpdateTimer; }

    /** Returns the current background color based on theme. */
    public Color getColorBackground() { return colorBackground; }

    /** Sets the background color. */
    public void setColorBackground(Color colorBackground) { this.colorBackground = colorBackground; }

    /** Returns the current foreground color based on theme. */
    public Color getColorForeground() { return colorForeground; }

    /** Sets the foreground color. */
    public void setColorForeground(Color colorForeground) { this.colorForeground = colorForeground; }

    /** Returns the editor font. */
    public Font getEditorFont() { return editorFont; }

    /** Sets the editor font. */
    public void setEditorFont(Font editorFont) { this.editorFont = editorFont; }

    /** Returns true if the current theme is dark. */
    public boolean isDarkTheme() { return darkTheme; }

    /** Sets whether the current theme is dark. */
    public void setDarkTheme(boolean darkTheme) { this.darkTheme = darkTheme; }

    /** Returns true if character-level diff is enabled. */
    public boolean isCharacterLevelDiff() { return characterLevelDiff; }

    /** Sets whether to use character-level diff. */
    public void setCharacterLevelDiff(boolean characterLevelDiff) { this.characterLevelDiff = characterLevelDiff; }

    /** Returns the diff calculator instance. */
    public DiffCalculator getDiffCalculator() { return diffCalculator; }

    /** Returns the diff highlighter instance. */
    public DiffHighlighter getDiffHighlighter() { return diffHighlighter; }

    /** Registers a context menu for theme updates. */
    public void registerContextMenu(JPopupMenu menu) { contextMenus.add(menu); }

    /** Returns the list of registered context menus. */
    public List<JPopupMenu> getContextMenus() { return contextMenus; }

    /** Returns the checkbox renderer used in the table. */
    public JCheckBox getTableCheckBoxRenderer() { return tableCheckBoxRenderer; }

    /** Sets the checkbox renderer used in the table. */
    public void setTableCheckBoxRenderer(JCheckBox checkBox) { this.tableCheckBoxRenderer = checkBox; }

    /** Returns the modified color box in the legend. */
    public JPanel getColorBoxModified() { return colorBoxModified; }

    /** Sets the modified color box in the legend. */
    public void setColorBoxModified(JPanel colorBox) { this.colorBoxModified = colorBox; }

    /** Returns the deleted color box in the legend. */
    public JPanel getColorBoxDeleted() { return colorBoxDeleted; }

    /** Sets the deleted color box in the legend. */
    public void setColorBoxDeleted(JPanel colorBox) { this.colorBoxDeleted = colorBox; }

    /** Returns the added color box in the legend. */
    public JPanel getColorBoxAdded() { return colorBoxAdded; }

    /** Sets the added color box in the legend. */
    public void setColorBoxAdded(JPanel colorBox) { this.colorBoxAdded = colorBox; }

    /**
     * Returns true if hexdump view mode is enabled.
     */
    public boolean isHexMode() {
        return hexMode;
    }

    /** Sets whether hexdump view mode is enabled. */
    public void setHexMode(boolean hexMode) {
        this.hexMode = hexMode;
    }

    /**
     * Returns the exclusions for a target, creating a new one if it doesn't exist.
     */
    public TargetExclusions getExclusionsForTarget(String targetKey) {
        return targetExclusionsMap.computeIfAbsent(targetKey, k -> new TargetExclusions());
    }

    /**
     * Returns the exclusions for the current target, or null if no target is selected.
     */
    public TargetExclusions getCurrentTargetExclusions() {
        if (currentTargetEntry == null) return null;
        String key = currentTargetEntry.getHost() + currentTargetEntry.getEndpoint();
        return getExclusionsForTarget(key);
    }

    /**
     * Returns the map of all target exclusions.
     */
    public Map<String, TargetExclusions> getTargetExclusionsMap() {
        return targetExclusionsMap;
    }

    /**
     * Sets the callback to be called when exclusions change.
     */
    public void setExclusionsChangedCallback(Runnable callback) {
        this.exclusionsChangedCallback = callback;
    }

    /**
     * Notifies that exclusions have changed and recalculation is needed.
     */
    public void notifyExclusionsChanged() {
        if (exclusionsChangedCallback != null) {
            exclusionsChangedCallback.run();
        }
    }

    /**
     * Removes exclusions for a target.
     */
    public void removeExclusionsForTarget(HttpLogEntry entry) {
        if (entry == null) return;
        String key = entry.getHost() + entry.getEndpoint();
        targetExclusionsMap.remove(key);
    }
}

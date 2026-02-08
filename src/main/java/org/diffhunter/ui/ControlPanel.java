package org.diffhunter.ui;

import org.diffhunter.ui.components.ColorBox;
import org.diffhunter.ui.components.StayOpenCheckBoxMenuItem;
import org.diffhunter.util.Constants;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Top control panel with filter, buttons, legend and mode selection.
 */
public class ControlPanel {

    private final UIContext context;
    private final Runnable applyTableFilterCallback;
    private final Runnable clearLogCallback;
    private final Runnable recalculateAllCallback;

    /**
     * Creates a new ControlPanel with the specified context and callbacks.
     */
    public ControlPanel(UIContext context, Runnable applyTableFilterCallback,
                        Runnable clearLogCallback, Runnable recalculateAllCallback) {
        this.context = context;
        this.applyTableFilterCallback = applyTableFilterCallback;
        this.clearLogCallback = clearLogCallback;
        this.recalculateAllCallback = recalculateAllCallback;
    }

    /**
     * Creates and configures the control panel with all buttons, filters and options.
     */
    public JPanel create() {
        JPanel controlPanel = new JPanel(new BorderLayout());
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        addFilterSection(leftPanel);
        int buttonHeight = addClearAndLimitSection(leftPanel);
        addLegendSection(leftPanel, buttonHeight);
        addViewModeSection(leftPanel);

        controlPanel.add(leftPanel, BorderLayout.WEST);
        controlPanel.add(createStatusPanel(), BorderLayout.EAST);

        return controlPanel;
    }

    /**
     * Adds the filter field and options button to the panel.
     */
    private void addFilterSection(JPanel panel) {
        JCheckBox tableFilterCaseSensitive = new JCheckBox("Case Sensitive");
        tableFilterCaseSensitive.addActionListener(e -> applyTableFilterCallback.run());
        context.setTableFilterCaseSensitive(tableFilterCaseSensitive);

        JCheckBox tableFilterRegex = new JCheckBox("Regex");
        tableFilterRegex.addActionListener(e -> applyTableFilterCallback.run());
        context.setTableFilterRegex(tableFilterRegex);

        JCheckBox tableFilterNegative = new JCheckBox("Negative Search");
        tableFilterNegative.setToolTipText("Show requests that do NOT match");
        tableFilterNegative.addActionListener(e -> applyTableFilterCallback.run());
        context.setTableFilterNegative(tableFilterNegative);

        JButton gearButton = new JButton("⚙");
        gearButton.setToolTipText("Filter options");

        JPopupMenu filterPopup = createFilterPopup(tableFilterCaseSensitive, tableFilterRegex, tableFilterNegative);
        context.registerContextMenu(filterPopup);

        gearButton.addActionListener(e -> filterPopup.show(gearButton, 0, gearButton.getHeight()));

        JTextField tableFilterField = new JTextField(60);
        tableFilterField.setToolTipText("Filter by request and response content. Press Enter to apply.");
        tableFilterField.addActionListener(e -> applyTableFilterCallback.run());
        tableFilterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {}
            @Override
            public void removeUpdate(DocumentEvent e) {
                if (tableFilterField.getText().isEmpty()) {
                    applyTableFilterCallback.run();
                }
            }
            @Override
            public void changedUpdate(DocumentEvent e) {}
        });
        context.setTableFilterField(tableFilterField);

        panel.add(new JLabel("Filter:"));
        panel.add(tableFilterField);
        panel.add(gearButton);
    }

    /**
     * Adds the clear button and request limit spinner to the panel.
     * Returns the button height for sizing other components.
     */
    private int addClearAndLimitSection(JPanel panel) {
        JButton clearButton = new JButton("Clear All");
        clearButton.addActionListener(e -> clearLogCallback.run());

        panel.add(new JLabel("  Request Limit:"));

        JSpinner maxEntriesSpinner = new JSpinner(new SpinnerNumberModel(
                context.getMaxLogEntries(),
                Constants.MIN_LOG_ENTRIES,
                Constants.MAX_LOG_ENTRIES,
                Constants.LOG_ENTRIES_STEP));
        maxEntriesSpinner.setToolTipText("Maximum number of requests stored in memory");
        maxEntriesSpinner.setPreferredSize(new Dimension(105, clearButton.getPreferredSize().height));
        maxEntriesSpinner.addChangeListener(e -> context.setMaxLogEntries((Integer) maxEntriesSpinner.getValue()));
        panel.add(maxEntriesSpinner);
        panel.add(new JLabel("  |  "));
        panel.add(clearButton);

        return clearButton.getPreferredSize().height;
    }

    /**
     * Adds the color legend section to the panel.
     */
    private void addLegendSection(JPanel panel, int buttonHeight) {

        int boxSize = buttonHeight - 4;
        boolean isDark = context.isDarkTheme();

        ColorBox deletedBox = new ColorBox(
                isDark ? Constants.COLOR_DELETED_REQUEST_DARK : Constants.COLOR_DELETED_REQUEST_LIGHT,
                boxSize, boxSize);
        ColorBox modifiedBox = new ColorBox(
                isDark ? Constants.COLOR_MODIFIED_RESPONSE_DARK : Constants.COLOR_MODIFIED_RESPONSE_LIGHT,
                boxSize, boxSize);
        ColorBox addedBox = new ColorBox(
                isDark ? Constants.COLOR_ADDED_BOTH_DARK : Constants.COLOR_ADDED_BOTH_LIGHT,
                boxSize, boxSize);

        context.setColorBoxDeleted(deletedBox);
        context.setColorBoxModified(modifiedBox);
        context.setColorBoxAdded(addedBox);

        JButton legendButton = new JButton("Colors ▼");

        JPopupMenu legendPopup = new JPopupMenu();
        legendPopup.add(createLegendItem(deletedBox, "Deleted / Request Differences", boxSize));
        legendPopup.add(createLegendItem(modifiedBox, "Modified / Response Differences", boxSize));
        legendPopup.add(createLegendItem(addedBox, "Added / Both", boxSize));
        context.registerContextMenu(legendPopup);

        legendButton.addActionListener(e -> legendPopup.show(legendButton, 0, legendButton.getHeight()));
        panel.add(legendButton);
    }

    /**
     * Creates a non-interactive legend row with a color box and label.
     */
    private JPanel createLegendItem(ColorBox colorBox, String text, int boxSize) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        item.add(colorBox);
        item.add(new JLabel(text));
        return item;
    }

    /**
     * Adds the view mode button with popup for diff and display modes.
     */
    private void addViewModeSection(JPanel panel) {

        JButton modeButton = new JButton("Mode ▼");
        modeButton.setToolTipText("Diff and display mode options");

        JPopupMenu modePopup = createModePopup();
        context.registerContextMenu(modePopup);

        modeButton.addActionListener(e -> modePopup.show(modeButton, 0, modeButton.getHeight()));
        panel.add(modeButton);
    }

    /**
     * Creates the mode options popup menu with diff and display settings.
     */
    private JPopupMenu createModePopup() {
        JPopupMenu modePopup = new JPopupMenu();

        JRadioButtonMenuItem linesOnlyItem = new JRadioButtonMenuItem("Line Diff", true);
        JRadioButtonMenuItem linesCharsItem = new JRadioButtonMenuItem("Character Diff", false);

        ButtonGroup diffModeGroup = new ButtonGroup();
        diffModeGroup.add(linesOnlyItem);
        diffModeGroup.add(linesCharsItem);

        linesOnlyItem.addActionListener(e -> {
            context.setCharacterLevelDiff(false);
            recalculateAllCallback.run();
        });
        linesCharsItem.addActionListener(e -> {
            context.setCharacterLevelDiff(true);
            recalculateAllCallback.run();
        });

        modePopup.add(linesOnlyItem);
        modePopup.add(linesCharsItem);
        modePopup.addSeparator();

        JRadioButtonMenuItem wordsItem = new JRadioButtonMenuItem("Words", true);
        JRadioButtonMenuItem hexdumpItem = new JRadioButtonMenuItem("Hexdump", false);

        ButtonGroup displayModeGroup = new ButtonGroup();
        displayModeGroup.add(wordsItem);
        displayModeGroup.add(hexdumpItem);

        wordsItem.addActionListener(e -> {
            context.setHexMode(false);
            recalculateAllCallback.run();
        });
        hexdumpItem.addActionListener(e -> {
            context.setHexMode(true);
            recalculateAllCallback.run();
        });

        modePopup.add(wordsItem);
        modePopup.add(hexdumpItem);

        return modePopup;
    }

    /**
     * Creates the status panel with status label and capture toggle button.
     */
    private JPanel createStatusPanel() {
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JLabel statusLabel = new JLabel("");
        rightPanel.add(statusLabel);
        context.setStatusLabel(statusLabel);

        JToggleButton statusButton = new JToggleButton("OFF");
        statusButton.setSelected(false);
        statusButton.addActionListener(e -> {
            if (statusButton.isSelected()) {
                statusButton.setText("ON");
                statusButton.setBackground(Constants.COLOR_BUTTON_ACTIVE);
                context.setCaptureEnabled(true);
            } else {
                statusButton.setText("OFF");
                statusButton.setBackground(null);
                context.setCaptureEnabled(false);
            }
        });
        rightPanel.add(statusButton);

        return rightPanel;
    }

    /**
     * Creates the filter options popup menu.
     */
    private JPopupMenu createFilterPopup(JCheckBox caseSensitive, JCheckBox regex, JCheckBox negative) {
        JPopupMenu filterPopup = new JPopupMenu();

        StayOpenCheckBoxMenuItem filterRequestsItem = new StayOpenCheckBoxMenuItem("Search In Requests", true);
        StayOpenCheckBoxMenuItem filterResponsesItem = new StayOpenCheckBoxMenuItem("Search In Responses", true);

        filterRequestsItem.addActionListener(e -> {
            context.setFilterRequests(filterRequestsItem.isSelected());
            applyTableFilterCallback.run();
        });
        filterResponsesItem.addActionListener(e -> {
            context.setFilterResponses(filterResponsesItem.isSelected());
            applyTableFilterCallback.run();
        });

        filterPopup.add(filterRequestsItem);
        filterPopup.add(filterResponsesItem);
        filterPopup.addSeparator();

        StayOpenCheckBoxMenuItem caseSensitiveItem = new StayOpenCheckBoxMenuItem("Case Sensitive");
        caseSensitiveItem.addActionListener(e -> {
            caseSensitive.setSelected(caseSensitiveItem.isSelected());
            applyTableFilterCallback.run();
        });
        filterPopup.add(caseSensitiveItem);

        StayOpenCheckBoxMenuItem regexItem = new StayOpenCheckBoxMenuItem("Regex");
        regexItem.addActionListener(e -> {
            regex.setSelected(regexItem.isSelected());
            applyTableFilterCallback.run();
        });
        filterPopup.add(regexItem);

        StayOpenCheckBoxMenuItem negativeItem = new StayOpenCheckBoxMenuItem("Negative Search");
        negativeItem.setToolTipText("Show requests that do NOT match");
        negativeItem.addActionListener(e -> {
            negative.setSelected(negativeItem.isSelected());
            applyTableFilterCallback.run();
        });
        filterPopup.add(negativeItem);

        filterPopup.addSeparator();

        StayOpenCheckBoxMenuItem requestDiffItem = new StayOpenCheckBoxMenuItem("Request Differences Only", true);
        requestDiffItem.addActionListener(e -> {
            context.setShowRequestDiff(requestDiffItem.isSelected());
            applyTableFilterCallback.run();
        });
        filterPopup.add(requestDiffItem);

        StayOpenCheckBoxMenuItem responseDiffItem = new StayOpenCheckBoxMenuItem("Response Differences Only", true);
        responseDiffItem.addActionListener(e -> {
            context.setShowResponseDiff(responseDiffItem.isSelected());
            applyTableFilterCallback.run();
        });
        filterPopup.add(responseDiffItem);

        StayOpenCheckBoxMenuItem bothDiffItem = new StayOpenCheckBoxMenuItem("Request And Response Differences Only", true);
        bothDiffItem.addActionListener(e -> {
            context.setShowBothDiff(bothDiffItem.isSelected());
            applyTableFilterCallback.run();
        });
        filterPopup.add(bothDiffItem);

        StayOpenCheckBoxMenuItem noDiffItem = new StayOpenCheckBoxMenuItem("No Differences", true);
        noDiffItem.addActionListener(e -> {
            context.setShowNoDiff(noDiffItem.isSelected());
            applyTableFilterCallback.run();
        });
        filterPopup.add(noDiffItem);

        return filterPopup;
    }
}

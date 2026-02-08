package org.diffhunter.ui;

import org.diffhunter.model.DiffSegment;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel containing the difference matches tables and filter combos.
 */
public class MatchPanel {

    private final UIContext context;
    private final Runnable updateEndpointComboCallback;
    private final Runnable selectEndpointInTableCallback;
    private final Runnable applyHighlightingCallback;
    private final Runnable scrollToRequestDiffCallback;
    private final Runnable scrollToResponseDiffCallback;
    private volatile boolean clearingSiblingSelection = false;
    private volatile boolean batchUpdating = false;
    private ExclusionsPanel exclusionsPanel;

    /**
     * Creates a new MatchPanel with the specified context and callbacks.
     */
    public MatchPanel(UIContext context, Runnable updateEndpointComboCallback,
                      Runnable selectEndpointInTableCallback, Runnable applyHighlightingCallback,
                      Runnable scrollToRequestDiffCallback, Runnable scrollToResponseDiffCallback) {
        this.context = context;
        this.updateEndpointComboCallback = updateEndpointComboCallback;
        this.selectEndpointInTableCallback = selectEndpointInTableCallback;
        this.applyHighlightingCallback = applyHighlightingCallback;
        this.scrollToRequestDiffCallback = scrollToRequestDiffCallback;
        this.scrollToResponseDiffCallback = scrollToResponseDiffCallback;
    }

    /**
     * Creates the match panel with domain/target combos and difference tables.
     */
    public JPanel create() {
        JPanel matchPanel = new JPanel(new BorderLayout());
        matchPanel.setBorder(BorderFactory.createTitledBorder("Differences"));

        JPanel filtersPanel = new JPanel();
        filtersPanel.setLayout(new BoxLayout(filtersPanel, BoxLayout.Y_AXIS));
        filtersPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        JPanel domainPanel = new JPanel(new BorderLayout(5, 0));
        domainPanel.add(new JLabel("Domains:"), BorderLayout.WEST);
        JComboBox<String> hostFilterCombo = new JComboBox<>();
        hostFilterCombo.addActionListener(e -> {
            if (!context.isUpdatingCombos()) updateEndpointComboCallback.run();
        });
        domainPanel.add(hostFilterCombo, BorderLayout.CENTER);
        filtersPanel.add(domainPanel);
        context.setHostFilterCombo(hostFilterCombo);

        filtersPanel.add(Box.createVerticalStrut(5));

        JPanel endpointPanel = new JPanel(new BorderLayout(5, 0));
        endpointPanel.add(new JLabel("Targets:"), BorderLayout.WEST);
        JComboBox<String> endpointFilterCombo = new JComboBox<>();
        endpointFilterCombo.addActionListener(e -> {
            if (!context.isUpdatingCombos()) selectEndpointInTableCallback.run();
        });
        endpointPanel.add(endpointFilterCombo, BorderLayout.CENTER);
        filtersPanel.add(endpointPanel);
        context.setEndpointFilterCombo(endpointFilterCombo);

        matchPanel.add(filtersPanel, BorderLayout.NORTH);

        exclusionsPanel = new ExclusionsPanel(context);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Requests", createRequestTabPanel());
        tabbedPane.addTab("Responses", createResponseTabPanel());
        tabbedPane.addTab("Exclusions", exclusionsPanel.create());
        matchPanel.add(tabbedPane, BorderLayout.CENTER);

        return matchPanel;
    }

    /**
     * Returns the exclusions panel for external refresh calls.
     */
    public ExclusionsPanel getExclusionsPanel() {
        return exclusionsPanel;
    }

    /**
     * Sets all checkboxes in a table model to the specified value.
     */
    private void setAllInTable(DefaultTableModel model, boolean selected) {
        if (model == null) return;
        for (int i = 0; i < model.getRowCount(); i++) {
            model.setValueAt(selected, i, 3);
        }
    }

    /**
     * Creates the Request tab panel with Selected and Target tables.
     */
    private JPanel createRequestTabPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 5, 0));

        JPanel selectedPanel = createDiffTable("Selected Request Differences",
            context::getRequestSelectedDiffSelection,
            context::setRequestSelectedMatchTable,
            context::setRequestSelectedMatchTableModel,
            scrollToRequestDiffCallback,
            context::getRequestTargetMatchTable,
            context::getRequestSelectedDiffs);

        JPanel targetPanel = createDiffTable("Target Request Differences",
            context::getRequestTargetDiffSelection,
            context::setRequestTargetMatchTable,
            context::setRequestTargetMatchTableModel,
            scrollToRequestDiffCallback,
            context::getRequestSelectedMatchTable,
            context::getRequestTargetDiffs);

        panel.add(selectedPanel);
        panel.add(targetPanel);
        return panel;
    }

    /**
     * Creates the Response tab panel with Selected and Target tables.
     */
    private JPanel createResponseTabPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 5, 0));

        JPanel selectedPanel = createDiffTable("Selected Response Differences",
            context::getResponseSelectedDiffSelection,
            context::setResponseSelectedMatchTable,
            context::setResponseSelectedMatchTableModel,
            scrollToResponseDiffCallback,
            context::getResponseTargetMatchTable,
            context::getResponseSelectedDiffs);

        JPanel targetPanel = createDiffTable("Target Response Differences",
            context::getResponseTargetDiffSelection,
            context::setResponseTargetMatchTable,
            context::setResponseTargetMatchTableModel,
            scrollToResponseDiffCallback,
            context::getResponseSelectedMatchTable,
            context::getResponseTargetDiffs);

        panel.add(selectedPanel);
        panel.add(targetPanel);
        return panel;
    }

    /**
     * Creates a diff table panel with the specified column name for differences.
     */
    private JPanel createDiffTable(String diffColumnName,
                                   java.util.function.Supplier<java.util.Map<Integer, Boolean>> selectionMapSupplier,
                                   java.util.function.Consumer<JTable> tableConsumer,
                                   java.util.function.Consumer<DefaultTableModel> modelConsumer,
                                   Runnable scrollCallback,
                                   java.util.function.Supplier<JTable> siblingTableSupplier,
                                   java.util.function.Supplier<List<DiffSegment>> diffsSupplier) {
        JPanel tablePanel = new JPanel(new BorderLayout());

        String[] columnNames = {"Line", "Status", diffColumnName, "Show"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) return Integer.class;
                if (column == 3) return Boolean.class;
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3;
            }

            @Override
            public void setValueAt(Object value, int row, int column) {
                super.setValueAt(value, row, column);
                if (column == 3) {
                    selectionMapSupplier.get().put(row, (Boolean) value);
                    if (!batchUpdating) {
                        applyHighlightingCallback.run();
                    }
                }
            }
        };
        modelConsumer.accept(tableModel);

        JTable table = new JTable(tableModel);
        table.setRowSorter(createThreeStateSorter(tableModel));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(45);
        table.getColumnModel().getColumn(0).setMinWidth(45);
        table.getColumnModel().getColumn(1).setMaxWidth(70);
        table.getColumnModel().getColumn(1).setMinWidth(70);
        table.getColumnModel().getColumn(3).setMaxWidth(50);
        table.getColumnModel().getColumn(3).setMinWidth(50);
        tableConsumer.accept(table);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !clearingSiblingSelection) {
                JTable siblingTable = siblingTableSupplier.get();
                if (siblingTable != null && table.getSelectedRow() >= 0) {
                    clearingSiblingSelection = true;
                    try {
                        siblingTable.clearSelection();
                    } finally {
                        clearingSiblingSelection = false;
                    }
                }
                applyHighlightingCallback.run();
            }
        });

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 2) {
                    scrollCallback.run();
                }
            }
        });

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow < 0) return;
            int modelRow = table.convertRowIndexToModel(selectedRow);
            List<DiffSegment> diffs = diffsSupplier.get();
            if (modelRow >= 0 && modelRow < diffs.size()) {
                String content = diffs.get(modelRow).getContent();
                StringSelection selection = new StringSelection(content);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            }
        });
        popupMenu.add(copyItem);

        popupMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                copyItem.setEnabled(table.getSelectedRow() >= 0);
            }
            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });

        table.setComponentPopupMenu(popupMenu);

        tablePanel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        JButton selectAllBtn = new JButton("Select All");
        selectAllBtn.setMargin(new Insets(1, 4, 1, 4));
        selectAllBtn.addActionListener(e -> {
            batchUpdating = true;
            try {
                setAllInTable(tableModel, true);
            } finally {
                batchUpdating = false;
            }
            applyHighlightingCallback.run();
        });

        JButton deselectAllBtn = new JButton("Deselect All");
        deselectAllBtn.setMargin(new Insets(1, 4, 1, 4));
        deselectAllBtn.addActionListener(e -> {
            batchUpdating = true;
            try {
                setAllInTable(tableModel, false);
            } finally {
                batchUpdating = false;
            }
            applyHighlightingCallback.run();
        });

        buttonPanel.add(selectAllBtn);
        buttonPanel.add(deselectAllBtn);
        tablePanel.add(buttonPanel, BorderLayout.SOUTH);

        return tablePanel;
    }

    /**
     * Creates a TableRowSorter with 3-state sorting: ascending, descending, unsorted.
     */
    private TableRowSorter<DefaultTableModel> createThreeStateSorter(DefaultTableModel model) {
        return new TableRowSorter<>(model) {
            private int lastColumn = -1;
            private int sortState = 0;

            @Override
            public void toggleSortOrder(int column) {
                if (lastColumn != column) {
                    sortState = 0;
                    lastColumn = column;
                }

                sortState = (sortState + 1) % 3;

                List<SortKey> sortKeys = new ArrayList<>();
                if (sortState == 1) {
                    sortKeys.add(new SortKey(column, SortOrder.ASCENDING));
                } else if (sortState == 2) {
                    sortKeys.add(new SortKey(column, SortOrder.DESCENDING));
                }

                setSortKeys(sortKeys);
            }
        };
    }
}

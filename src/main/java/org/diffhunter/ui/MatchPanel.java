package org.diffhunter.ui;

import org.diffhunter.util.Constants;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;

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
        matchPanel.setBorder(BorderFactory.createTitledBorder("Differences (Selected Request/Response vs Target)"));

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

        JSplitPane tablesSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        tablesSplitPane.setResizeWeight(0.5);

        JPanel requestTablePanel = createRequestDiffTable();
        tablesSplitPane.setLeftComponent(requestTablePanel);

        JPanel responseTablePanel = createResponseDiffTable();
        tablesSplitPane.setRightComponent(responseTablePanel);

        matchPanel.add(tablesSplitPane, BorderLayout.CENTER);

        JPanel matchButtonPanel = new JPanel(new FlowLayout());

        JButton selectAllBtn = new JButton("Select All");
        selectAllBtn.addActionListener(e -> {
            for (int i = 0; i < context.getRequestMatchTableModel().getRowCount(); i++) {
                context.getRequestMatchTableModel().setValueAt(Boolean.TRUE, i, 1);
            }
            for (int i = 0; i < context.getResponseMatchTableModel().getRowCount(); i++) {
                context.getResponseMatchTableModel().setValueAt(Boolean.TRUE, i, 1);
            }
        });

        JButton deselectAllBtn = new JButton("Deselect All");
        deselectAllBtn.addActionListener(e -> {
            for (int i = 0; i < context.getRequestMatchTableModel().getRowCount(); i++) {
                context.getRequestMatchTableModel().setValueAt(Boolean.FALSE, i, 1);
            }
            for (int i = 0; i < context.getResponseMatchTableModel().getRowCount(); i++) {
                context.getResponseMatchTableModel().setValueAt(Boolean.FALSE, i, 1);
            }
        });

        matchButtonPanel.add(selectAllBtn);
        matchButtonPanel.add(deselectAllBtn);
        matchPanel.add(matchButtonPanel, BorderLayout.SOUTH);

        return matchPanel;
    }

    /**
     * Creates the request differences table panel.
     */
    private JPanel createRequestDiffTable() {
        JPanel requestTablePanel = new JPanel(new BorderLayout());
        requestTablePanel.setBorder(BorderFactory.createTitledBorder("Request"));

        DefaultTableModel requestMatchTableModel = new DefaultTableModel(Constants.MATCH_TABLE_COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 1) return Boolean.class;
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }

            @Override
            public void setValueAt(Object value, int row, int column) {
                super.setValueAt(value, row, column);
                if (column == 1) {
                    context.getRequestDiffSelection().put(row, (Boolean) value);
                    applyHighlightingCallback.run();
                }
            }
        };
        context.setRequestMatchTableModel(requestMatchTableModel);

        JTable requestMatchTable = new JTable(requestMatchTableModel);
        requestMatchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        requestMatchTable.getColumnModel().getColumn(1).setMaxWidth(50);
        requestMatchTable.getColumnModel().getColumn(1).setMinWidth(50);
        context.setRequestMatchTable(requestMatchTable);

        TableRowSorter<DefaultTableModel> requestMatchTableSorter = new TableRowSorter<>(requestMatchTableModel);
        requestMatchTable.setRowSorter(requestMatchTableSorter);
        context.setRequestMatchTableSorter(requestMatchTableSorter);

        requestMatchTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                applyHighlightingCallback.run();
            }
        });

        requestMatchTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = requestMatchTable.rowAtPoint(e.getPoint());
                int col = requestMatchTable.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 0) {
                    scrollToRequestDiffCallback.run();
                }
            }
        });

        requestTablePanel.add(new JScrollPane(requestMatchTable), BorderLayout.CENTER);
        return requestTablePanel;
    }

    /**
     * Creates the response differences table panel.
     */
    private JPanel createResponseDiffTable() {
        JPanel responseTablePanel = new JPanel(new BorderLayout());
        responseTablePanel.setBorder(BorderFactory.createTitledBorder("Response"));

        DefaultTableModel responseMatchTableModel = new DefaultTableModel(Constants.MATCH_TABLE_COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 1) return Boolean.class;
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }

            @Override
            public void setValueAt(Object value, int row, int column) {
                super.setValueAt(value, row, column);
                if (column == 1) {
                    context.getResponseDiffSelection().put(row, (Boolean) value);
                    applyHighlightingCallback.run();
                }
            }
        };
        context.setResponseMatchTableModel(responseMatchTableModel);

        JTable responseMatchTable = new JTable(responseMatchTableModel);
        responseMatchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        responseMatchTable.getColumnModel().getColumn(1).setMaxWidth(50);
        responseMatchTable.getColumnModel().getColumn(1).setMinWidth(50);
        context.setResponseMatchTable(responseMatchTable);

        TableRowSorter<DefaultTableModel> responseMatchTableSorter = new TableRowSorter<>(responseMatchTableModel);
        responseMatchTable.setRowSorter(responseMatchTableSorter);
        context.setResponseMatchTableSorter(responseMatchTableSorter);

        responseMatchTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                applyHighlightingCallback.run();
            }
        });

        responseMatchTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = responseMatchTable.rowAtPoint(e.getPoint());
                int col = responseMatchTable.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 0) {
                    scrollToResponseDiffCallback.run();
                }
            }
        });

        responseTablePanel.add(new JScrollPane(responseMatchTable), BorderLayout.CENTER);
        return responseTablePanel;
    }
}

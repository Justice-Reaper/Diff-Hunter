package org.diffhunter.ui;

import org.diffhunter.model.ExclusionRule;
import org.diffhunter.model.TargetExclusions;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;

/**
 * Panel for managing exclusion rules per target.
 */
public class ExclusionsPanel {

    private final UIContext context;
    private JTable requestExclusionsTable;
    private JTable responseExclusionsTable;
    private DefaultTableModel requestTableModel;
    private DefaultTableModel responseTableModel;

    public ExclusionsPanel(UIContext context) {
        this.context = context;
    }

    /**
     * Creates the exclusions panel with request and response exclusion tables.
     */
    public JPanel create() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        JPanel tablesPanel = new JPanel(new GridLayout(1, 2, 5, 0));

        tablesPanel.add(createExclusionTable(true));
        tablesPanel.add(createExclusionTable(false));

        mainPanel.add(tablesPanel, BorderLayout.CENTER);

        return mainPanel;
    }

    /**
     * Creates an exclusion table panel with Add/Remove and Select/Deselect buttons.
     */
    private JPanel createExclusionTable(boolean isRequest) {
        JPanel panel = new JPanel(new BorderLayout());

        String regexColumnName = isRequest ? "Request Regex" : "Response Regex";
        String[] columnNames = {regexColumnName, "Enabled"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
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
                TargetExclusions exclusions = context.getCurrentTargetExclusions();
                if (exclusions != null) {
                    List<ExclusionRule> rules = isRequest ?
                            exclusions.getRequestExclusions() : exclusions.getResponseExclusions();
                    if (row < rules.size()) {
                        ExclusionRule rule = rules.get(row);
                        if (column == 0) {
                            rule.setRegex((String) value);
                        } else if (column == 1) {
                            rule.setEnabled((Boolean) value);
                        }
                        context.notifyExclusionsChanged();
                    }
                }
            }
        };

        if (isRequest) {
            requestTableModel = tableModel;
        } else {
            responseTableModel = tableModel;
        }

        JTable table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(1).setMaxWidth(70);
        table.getColumnModel().getColumn(1).setMinWidth(70);

        table.setDefaultRenderer(String.class, new TableCellRenderer() {
            private final JTextField textField = new JTextField();

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                textField.setText(value != null ? value.toString() : "");
                textField.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

                TargetExclusions exclusions = context.getCurrentTargetExclusions();
                if (exclusions != null) {
                    List<ExclusionRule> rules = isRequest ?
                            exclusions.getRequestExclusions() : exclusions.getResponseExclusions();
                    if (row < rules.size() && !rules.get(row).isValid()) {
                        textField.setBackground(new Color(255, 200, 200));
                    } else if (isSelected) {
                        textField.setBackground(table.getSelectionBackground());
                    } else {
                        textField.setBackground(table.getBackground());
                    }
                }
                return textField;
            }
        });

        if (isRequest) {
            requestExclusionsTable = table;
        } else {
            responseExclusionsTable = table;
        }

        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));

        JButton addBtn = new JButton("Add");
        addBtn.setMargin(new Insets(1, 8, 1, 8));
        addBtn.addActionListener(e -> addExclusion(isRequest));

        JButton editBtn = new JButton("Edit");
        editBtn.setMargin(new Insets(1, 8, 1, 8));
        editBtn.addActionListener(e -> editExclusion(isRequest));

        JButton removeBtn = new JButton("Remove");
        removeBtn.setMargin(new Insets(1, 8, 1, 8));
        removeBtn.addActionListener(e -> removeExclusion(isRequest));

        JButton selectAllBtn = new JButton("Select All");
        selectAllBtn.setMargin(new Insets(1, 4, 1, 4));
        selectAllBtn.addActionListener(e -> setAllEnabled(isRequest, true));

        JButton deselectAllBtn = new JButton("Deselect All");
        deselectAllBtn.setMargin(new Insets(1, 4, 1, 4));
        deselectAllBtn.addActionListener(e -> setAllEnabled(isRequest, false));

        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(selectAllBtn);
        buttonPanel.add(deselectAllBtn);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Adds a new exclusion rule.
     */
    private void addExclusion(boolean isRequest) {
        TargetExclusions exclusions = context.getCurrentTargetExclusions();
        java.awt.Frame parentFrame = context.getApi() != null
                ? context.getApi().userInterface().swingUtils().suiteFrame() : null;

        if (exclusions == null) {
            JOptionPane.showMessageDialog(parentFrame, "Please select a target first.",
                    "No Target", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String regex = JOptionPane.showInputDialog(parentFrame, "Enter regex pattern:",
                "Add Exclusion", JOptionPane.PLAIN_MESSAGE);
        if (regex != null && !regex.trim().isEmpty()) {
            ExclusionRule rule = new ExclusionRule(regex.trim());
            if (isRequest) {
                exclusions.addRequestExclusion(rule);
            } else {
                exclusions.addResponseExclusion(rule);
            }
            refreshTables();
            context.notifyExclusionsChanged();
        }
    }

    /**
     * Edits the selected exclusion rule via a dialog.
     */
    private void editExclusion(boolean isRequest) {
        TargetExclusions exclusions = context.getCurrentTargetExclusions();
        if (exclusions == null) return;

        JTable table = isRequest ? requestExclusionsTable : responseExclusionsTable;
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) return;

        List<ExclusionRule> rules = isRequest ?
                exclusions.getRequestExclusions() : exclusions.getResponseExclusions();
        if (selectedRow < rules.size()) {
            ExclusionRule rule = rules.get(selectedRow);
            java.awt.Frame parentFrame = context.getApi() != null
                    ? context.getApi().userInterface().swingUtils().suiteFrame() : null;
            String newRegex = (String) JOptionPane.showInputDialog(parentFrame, "Edit regex pattern:",
                    "Edit Exclusion", JOptionPane.PLAIN_MESSAGE, null, null, rule.getRegex());
            if (newRegex != null && !newRegex.trim().isEmpty()) {
                rule.setRegex(newRegex.trim());
                refreshTables();
                context.notifyExclusionsChanged();
            }
        }
    }

    /**
     * Removes the selected exclusion rule.
     */
    private void removeExclusion(boolean isRequest) {
        TargetExclusions exclusions = context.getCurrentTargetExclusions();
        if (exclusions == null) return;

        JTable table = isRequest ? requestExclusionsTable : responseExclusionsTable;
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) return;

        List<ExclusionRule> rules = isRequest ?
                exclusions.getRequestExclusions() : exclusions.getResponseExclusions();
        if (selectedRow < rules.size()) {
            rules.remove(selectedRow);
            refreshTables();
            context.notifyExclusionsChanged();
        }
    }

    /**
     * Sets all exclusions enabled or disabled.
     */
    private void setAllEnabled(boolean isRequest, boolean enabled) {
        TargetExclusions exclusions = context.getCurrentTargetExclusions();
        if (exclusions == null) return;

        List<ExclusionRule> rules = isRequest ?
                exclusions.getRequestExclusions() : exclusions.getResponseExclusions();
        for (ExclusionRule rule : rules) {
            rule.setEnabled(enabled);
        }
        refreshTables();
        context.notifyExclusionsChanged();
    }

    /**
     * Refreshes the tables to show current exclusions for the selected target.
     */
    public void refreshTables() {
        TargetExclusions exclusions = context.getCurrentTargetExclusions();

        requestTableModel.setRowCount(0);
        responseTableModel.setRowCount(0);

        if (exclusions == null) {
            return;
        }

        for (ExclusionRule rule : exclusions.getRequestExclusions()) {
            requestTableModel.addRow(new Object[]{rule.getRegex(), rule.isEnabled()});
        }

        for (ExclusionRule rule : exclusions.getResponseExclusions()) {
            responseTableModel.addRow(new Object[]{rule.getRegex(), rule.isEnabled()});
        }
    }
}

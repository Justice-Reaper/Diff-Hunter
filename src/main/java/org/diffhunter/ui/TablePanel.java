package org.diffhunter.ui;

import org.diffhunter.model.HttpLogEntry;
import org.diffhunter.model.RowDiffType;
import org.diffhunter.util.Constants;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel containing the main request table.
 */
public class TablePanel {

    private final UIContext context;
    private final Runnable displaySelectedRequestCallback;
    private final Runnable updateHostAndEndpointCombosCallback;

    /**
     * Creates a new TablePanel with the specified context and callbacks.
     */
    public TablePanel(UIContext context, Runnable displaySelectedRequestCallback,
                      Runnable updateHostAndEndpointCombosCallback) {
        this.context = context;
        this.displaySelectedRequestCallback = displaySelectedRequestCallback;
        this.updateHostAndEndpointCombosCallback = updateHostAndEndpointCombosCallback;
    }

    /**
     * Creates and configures the main request table with custom renderers and editors.
     */
    public JScrollPane create() {
        DefaultTableModel tableModel = new DefaultTableModel(Constants.TABLE_COLUMN_NAMES, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return switch (column) {
                    case 0, 7, 8 -> Integer.class;
                    case 9 -> Long.class;
                    case 10 -> Boolean.class;
                    default -> String.class;
                };
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 10;
            }

            @Override
            public void setValueAt(Object value, int row, int column) {
                super.setValueAt(value, row, column);
                if (column == 10) {
                    try {
                        int requestNum = (Integer) getValueAt(row, 0);
                        HttpLogEntry entry = context.getLogEntriesMap().get(requestNum);
                        if (entry != null) {
                            entry.setMarked((Boolean) value);
                            if (entry.isMarked()) {
                                context.getTargetEntries().put(entry.getNumber(), entry);
                            } else {
                                context.removeExclusionsForTarget(entry);
                                context.getTargetEntries().remove(entry.getNumber());
                            }
                            updateHostAndEndpointCombosCallback.run();
                        }
                    } catch (Exception e) {
                        if (context.getApi() != null) {
                            context.getApi().logging().logToError("[DiffHunter] Error updating target selection: " + e.getMessage());
                        }
                    }
                }
            }
        };
        context.setTableModel(tableModel);

        JTable requestTable = new JTable(tableModel);
        requestTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        context.setRequestTable(requestTable);

        TableRowSorter<DefaultTableModel> tableSorter = createThreeStateSorter(tableModel);
        requestTable.setRowSorter(tableSorter);
        context.setTableSorter(tableSorter);

        requestTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !context.isEditingCheckbox() && !context.isFiltering()) {
                displaySelectedRequestCallback.run();
            }
        });

        DefaultTableCellRenderer rowColorRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                applyRowColoring(c, table, isSelected, row);
                return c;
            }
        };

        JCheckBox rendererCheckBox = new JCheckBox();
        rendererCheckBox.setHorizontalAlignment(JLabel.CENTER);
        rendererCheckBox.setOpaque(true);
        context.setTableCheckBoxRenderer(rendererCheckBox);

        javax.swing.table.TableCellRenderer booleanRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                rendererCheckBox.setSelected(value != null && (Boolean) value);
                applyRowColoring(rendererCheckBox, table, isSelected, row);
                return rendererCheckBox;
            }
        };

        requestTable.setDefaultRenderer(Object.class, rowColorRenderer);
        requestTable.setDefaultRenderer(Integer.class, rowColorRenderer);
        requestTable.setDefaultRenderer(Long.class, rowColorRenderer);
        requestTable.setDefaultRenderer(Boolean.class, booleanRenderer);

        javax.swing.table.TableCellEditor checkboxEditor = new javax.swing.DefaultCellEditor(new JCheckBox()) {
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                                                          boolean isSelected, int row, int column) {
                context.setEditingCheckbox(true);
                int currentSelection = table.getSelectedRow();

                JCheckBox checkbox = (JCheckBox) super.getTableCellEditorComponent(table, value, isSelected, row, column);
                checkbox.setSelected(value != null && (Boolean) value);
                checkbox.setHorizontalAlignment(JLabel.CENTER);

                SwingUtilities.invokeLater(() -> {
                    if (currentSelection >= 0 && currentSelection < table.getRowCount()) {
                        table.setRowSelectionInterval(currentSelection, currentSelection);
                    }
                    context.setEditingCheckbox(false);
                });

                return checkbox;
            }
        };
        requestTable.getColumnModel().getColumn(10).setCellEditor(checkboxEditor);

        return new JScrollPane(requestTable);
    }

    /**
     * Applies row coloring based on selection state and difference status.
     */
    private void applyRowColoring(Component c, JTable table, boolean isSelected, int row) {
        if (isSelected) {
            c.setBackground(table.getSelectionBackground());
            c.setForeground(table.getSelectionForeground());
        } else {
            c.setBackground(table.getBackground());
            c.setForeground(table.getForeground());
        }

        if (!isSelected && context.getCurrentTargetEntry() != null) {
            int modelRow = table.convertRowIndexToModel(row);
            Object requestNumObj = context.getTableModel().getValueAt(modelRow, 0);
            if (requestNumObj != null) {
                HttpLogEntry entry = context.getLogEntriesMap().get((Integer) requestNumObj);
                if (entry != null && entry.getRowDiffType() != RowDiffType.NONE) {
                    c.setBackground(getRowColorForDiffType(entry.getRowDiffType()));
                }
            }
        }
    }

    /**
     * Returns the appropriate background color based on the difference type.
     * REQUEST_ONLY: red (deleted color)
     * RESPONSE_ONLY: orange (modified color)
     * BOTH: green (added color)
     */
    private java.awt.Color getRowColorForDiffType(RowDiffType diffType) {
        boolean isDark = context.isDarkTheme();
        return switch (diffType) {
            case REQUEST_ONLY -> isDark ? Constants.COLOR_DELETED_REQUEST_DARK : Constants.COLOR_DELETED_REQUEST_LIGHT;
            case RESPONSE_ONLY -> isDark ? Constants.COLOR_MODIFIED_RESPONSE_DARK : Constants.COLOR_MODIFIED_RESPONSE_LIGHT;
            case BOTH -> isDark ? Constants.COLOR_ADDED_BOTH_DARK : Constants.COLOR_ADDED_BOTH_LIGHT;
            default -> isDark ? Constants.COLOR_DARK_BACKGROUND : Constants.COLOR_LIGHT_BACKGROUND;
        };
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

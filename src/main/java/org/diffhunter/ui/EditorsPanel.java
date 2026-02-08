package org.diffhunter.ui;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import org.diffhunter.model.HttpLogEntry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

/**
 * Panel containing the request/response text editors.
 */
public class EditorsPanel {

    private final UIContext context;
    private SearchBar leftSearchBar;
    private SearchBar rightSearchBar;

    /**
     * Creates a new EditorsPanel with the specified context.
     */
    public EditorsPanel(UIContext context) {
        this.context = context;
    }

    /**
     * Creates the editors panel with request/response text panes for selected and target entries.
     */
    public JPanel create() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Selected Request"));

        JTextPane requestPane = createTextPane(context::getCurrentSelectedEntry, true);
        JTextPane responsePane = createTextPane(context::getCurrentSelectedEntry, false);
        context.setRequestPane(requestPane);
        context.setResponsePane(responsePane);

        JTabbedPane tabsLeft = new JTabbedPane();
        tabsLeft.addTab("Request", new JScrollPane(requestPane));
        tabsLeft.addTab("Response", new JScrollPane(responsePane));
        leftPanel.add(tabsLeft, BorderLayout.CENTER);

        leftSearchBar = new SearchBar(context, tabsLeft, requestPane, responsePane);
        leftPanel.add(leftSearchBar, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Target"));

        JTextPane requestPaneEndpoint = createTextPane(context::getCurrentTargetEntry, true);
        JTextPane responsePaneEndpoint = createTextPane(context::getCurrentTargetEntry, false);
        context.setRequestPaneEndpoint(requestPaneEndpoint);
        context.setResponsePaneEndpoint(responsePaneEndpoint);

        JTabbedPane tabsRight = new JTabbedPane();
        tabsRight.addTab("Request", new JScrollPane(requestPaneEndpoint));
        tabsRight.addTab("Response", new JScrollPane(responsePaneEndpoint));
        rightPanel.add(tabsRight, BorderLayout.CENTER);

        rightSearchBar = new SearchBar(context, tabsRight, requestPaneEndpoint, responsePaneEndpoint);
        rightPanel.add(rightSearchBar, BorderLayout.SOUTH);

        JSplitPane editorsSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        editorsSplit.setResizeWeight(0.5);

        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(editorsSplit, BorderLayout.CENTER);

        return wrapperPanel;
    }

    /**
     * Clears the search in both search bars.
     */
    public void clearSearch() {
        if (leftSearchBar != null) {
            leftSearchBar.clearSearch();
        }
        if (rightSearchBar != null) {
            rightSearchBar.clearSearch();
        }
    }

    /**
     * Creates a styled text pane with context menu support.
     */
    private JTextPane createTextPane(Supplier<HttpLogEntry> entrySupplier, boolean isRequest) {
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(context.getEditorFont());
        textPane.setOpaque(true);
        textPane.setBackground(context.getColorBackground());
        textPane.setForeground(context.getColorForeground());
        textPane.setCaretColor(context.getColorForeground());

        JPopupMenu contextMenu = createContextMenu(entrySupplier, isRequest);
        textPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopupIfTriggered(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopupIfTriggered(e);
            }

            private void showPopupIfTriggered(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        return textPane;
    }

    /**
     * Creates the right-click context menu for sending requests to Burp tools.
     */
    private JPopupMenu createContextMenu(Supplier<HttpLogEntry> entrySupplier, boolean isRequest) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem sendToRepeater = new JMenuItem("Send to Repeater");
        sendToRepeater.addActionListener(e -> sendToTool(entrySupplier, "repeater", isRequest));
        menu.add(sendToRepeater);

        JMenuItem sendToIntruder = new JMenuItem("Send to Intruder");
        sendToIntruder.addActionListener(e -> sendToTool(entrySupplier, "intruder", isRequest));
        menu.add(sendToIntruder);

        JMenuItem sendToComparer = new JMenuItem("Send to Comparer");
        sendToComparer.addActionListener(e -> sendToTool(entrySupplier, "comparer", isRequest));
        menu.add(sendToComparer);

        if (context.getApi() != null) {
            context.getApi().userInterface().applyThemeToComponent(menu);
        }

        context.registerContextMenu(menu);
        return menu;
    }

    /**
     * Sends the current request or response to the specified Burp tool.
     */
    private void sendToTool(Supplier<HttpLogEntry> entrySupplier, String tool, boolean isRequest) {
        HttpLogEntry entry = entrySupplier.get();
        if (entry == null || context.getApi() == null) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.httpRequest(entry.getHttpService(), ByteArray.byteArray(entry.getRequestBytes()));

            switch (tool) {
                case "repeater":
                    context.getApi().repeater().sendToRepeater(request);
                    break;
                case "intruder":
                    context.getApi().intruder().sendToIntruder(request);
                    break;
                case "comparer":
                    byte[] dataToSend = isRequest ? entry.getRequestBytes() : entry.getResponseBytes();
                    context.getApi().comparer().sendToComparer(ByteArray.byteArray(dataToSend));
                    break;
            }
        } catch (Exception ex) {
            context.getApi().logging().logToError("Error sending to " + tool + ": " + ex.getMessage());
        }
    }
}

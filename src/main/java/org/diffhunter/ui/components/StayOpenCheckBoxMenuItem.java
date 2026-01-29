package org.diffhunter.ui.components;

import javax.swing.*;

/**
 * A JCheckBoxMenuItem that doesn't close the parent popup menu when clicked.
 */
public class StayOpenCheckBoxMenuItem extends JCheckBoxMenuItem {

    /**
     * Creates a checkbox menu item with the specified text.
     */
    public StayOpenCheckBoxMenuItem(String text) {
        super(text);
    }

    /**
     * Creates a checkbox menu item with the specified text and selection state.
     */
    public StayOpenCheckBoxMenuItem(String text, boolean selected) {
        super(text, selected);
    }

    /**
     * Processes mouse events to prevent the popup menu from closing on click.
     */
    @Override
    protected void processMouseEvent(java.awt.event.MouseEvent e) {
        if (e.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED && contains(e.getPoint())) {
            doClick(0);
            setArmed(true);
        } else {
            super.processMouseEvent(e);
        }
    }
}

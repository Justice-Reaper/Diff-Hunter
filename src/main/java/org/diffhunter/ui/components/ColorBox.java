package org.diffhunter.ui.components;

import javax.swing.*;
import java.awt.*;

/**
 * Colored box component for the legend display.
 */
public class ColorBox extends JPanel {

    /**
     * Creates a colored box with the specified color and dimensions.
     */
    public ColorBox(Color color, int width, int height) {
        setBackground(color);
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(width, height));
        setMaximumSize(new Dimension(width, height));
    }
}

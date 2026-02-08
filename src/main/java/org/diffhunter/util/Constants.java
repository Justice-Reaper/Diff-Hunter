package org.diffhunter.util;

import java.awt.Color;
import java.text.SimpleDateFormat;

/**
 * Constants used throughout the Diff Hunter extension.
 */
public final class Constants {

    /** Private constructor to prevent instantiation. */
    private Constants() {}

    public static final String EXTENSION_NAME = "Diff Hunter";
    public static final String EXTENSION_VERSION = "1.0.0";
    public static final String AUTHOR = "Sergio Aledo Bernal";
    public static final String EMAIL = "justice.reaper.io@gmail.com";
    public static final String LINKTREE = "https://linktr.ee/Justice_Reaper";

    public static final int DIVIDER_LOCATION_VERTICAL = 350;
    public static final double RESIZE_WEIGHT = 0.6;

    public static final int DEFAULT_MAX_LOG_ENTRIES = 100000;
    public static final int MIN_LOG_ENTRIES = 100;
    public static final int MAX_LOG_ENTRIES = Integer.MAX_VALUE;
    public static final int LOG_ENTRIES_STEP = 1000;
    public static final int BATCH_UPDATE_INTERVAL_MS = 100;

    public static final Color COLOR_DELETED_REQUEST_DARK = new Color(106, 26, 26);
    public static final Color COLOR_ADDED_BOTH_DARK = new Color(85, 115, 35);
    public static final Color COLOR_MODIFIED_RESPONSE_DARK = new Color(140, 90, 30);

    public static final Color COLOR_DELETED_REQUEST_LIGHT = new Color(215, 90, 90);
    public static final Color COLOR_ADDED_BOTH_LIGHT = new Color(140, 215, 120);
    public static final Color COLOR_MODIFIED_RESPONSE_LIGHT = new Color(230, 170, 90);
    public static final Color COLOR_SEARCH_ERROR = new Color(255, 200, 200);
    public static final Color COLOR_BUTTON_ACTIVE = new Color(100, 150, 220);

    public static final Color COLOR_DARK_BACKGROUND = new Color(43, 43, 43);
    public static final Color COLOR_LIGHT_BACKGROUND = Color.WHITE;
    public static final Color COLOR_DARK_FOREGROUND = new Color(187, 187, 187);
    public static final Color COLOR_LIGHT_FOREGROUND = Color.BLACK;

    public static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm:ss"));

    public static final String[] TABLE_COLUMN_NAMES = {
            "#", "Time", "Tool", "Method", "Host", "Path", "Query", "Status", "Length", "Response Time", "Target"
    };
}

package org.diffhunter.diff;

import java.nio.charset.StandardCharsets;

/**
 * Converts strings to hexdump format.
 */
public final class HexDumpConverter {

    /** Private constructor to prevent instantiation. */
    private HexDumpConverter() {}

    /**
     * Converts a string to hexdump format.
     */
    public static String toHexDump(String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < bytes.length; i += 16) {
            sb.append(String.format("%08X  ", i));

            StringBuilder ascii = new StringBuilder();
            for (int j = 0; j < 16; j++) {
                if (i + j < bytes.length) {
                    byte b = bytes[i + j];
                    sb.append(String.format("%02X ", b));
                    ascii.append(b >= 32 && b <= 126 ? (char) b : '.');
                } else {
                    sb.append("   ");
                }
            }

            sb.append("  ").append(ascii).append("\n");
        }
        return sb.toString();
    }
}

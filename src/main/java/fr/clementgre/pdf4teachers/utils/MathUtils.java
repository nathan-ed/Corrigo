/*
 * Copyright (c) 2022. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 * Modified by Nathan, 2026.
 */

package fr.clementgre.pdf4teachers.utils;

import javafx.scene.input.KeyEvent;

public final class MathUtils {

    // Returns in function of the sign of val
    public static double selectValueBasedOnSign(double val, double negative, double positive) {
        return val < 0 ? negative : positive;
    }

    public static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    public static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public static Double parseDoubleOrNull(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseIntOrNull(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long parseLongOrNull(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static long parseLongOrDefault(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double parseDoubleOrDefault(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    // The digit typed (Modified by Nathan, 2026): the character of the key, so that Shift+3 or AltGr+3 typing "#" is not
    // a 3; the key code only when the key types nothing (keypad on some systems)
    public static Integer parseDigitTypedOrNull(KeyEvent e){
        String text = e.getText();
        if(text != null && !text.isEmpty()) return text.length() == 1 && Character.isDigit(text.charAt(0)) ? parseIntOrNull(text) : null;
        if(e.isShiftDown() || e.isAltDown() || e.isShortcutDown()) return null;
        return parseIntOrNull(e.getCode().getChar());
    }
    
    public static Integer parseIntFromKeyEventOrNull(KeyEvent e){
        Integer first = parseIntOrNull(e.getCode().getChar());
        Integer second = parseIntOrNull(e.getText());
        return first != null ? first : second;
    }
}

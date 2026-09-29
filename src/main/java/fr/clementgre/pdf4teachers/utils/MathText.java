/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a text element into plain text and LaTeX parts:
 * - $…$ is inline math, as in a LaTeX document. A $ with no closing $ is a plain dollar.
 * - $$…$$ is also math. A $$ with no closing $$ makes the rest of the text math, as in the texts written before $…$ existed.
 * - \$ is a plain dollar.
 * The old LibreOffice math (&&) texts are not split: they keep their former rendering (see isLegacy).
 */
public final class MathText {

    public static final String LEGACY_STARMATH = "&&";

    public enum Type { TEXT, MATH }
    public record Segment(Type type, String content) {}

    private MathText(){
    }

    public static boolean isLegacy(String text){
        return text != null && text.contains(LEGACY_STARMATH);
    }

    public static boolean hasMath(String text){
        if(text == null) return false;
        if(isLegacy(text)) return true;
        return parse(text).stream().anyMatch(segment -> segment.type() == Type.MATH);
    }

    // The whole text is one formula (spaces around excepted).
    public static boolean isWholeMath(List<Segment> segments){
        List<Segment> meaningful = segments.stream().filter(segment -> segment.type() == Type.MATH || !segment.content().isBlank()).toList();
        return meaningful.size() == 1 && meaningful.getFirst().type() == Type.MATH;
    }

    public static List<Segment> parse(String text){
        ArrayList<Segment> segments = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while(i < text.length()){
            char c = text.charAt(i);
            if(c == '\\' && i + 1 < text.length() && text.charAt(i + 1) == '$'){
                plain.append('$');
                i += 2;
            }else if(text.startsWith("$$", i)){
                int end = text.indexOf("$$", i + 2);
                String math = end == -1 ? text.substring(i + 2) : text.substring(i + 2, end);
                flush(segments, plain);
                if(!math.isBlank()) segments.add(new Segment(Type.MATH, math));
                i = end == -1 ? text.length() : end + 2;
            }else if(c == '$'){
                int end = findClosingDollar(text, i + 1);
                if(end == -1 || text.substring(i + 1, end).isBlank()){
                    plain.append('$');
                    i++;
                }else{
                    flush(segments, plain);
                    segments.add(new Segment(Type.MATH, text.substring(i + 1, end)));
                    i = end + 1;
                }
            }else{
                plain.append(c);
                i++;
            }
        }
        flush(segments, plain);
        return segments;
    }

    // Next $ that is not escaped (\$ is a dollar in LaTeX too), or -1.
    private static int findClosingDollar(String text, int from){
        for(int i = from; i < text.length(); i++){
            if(text.charAt(i) == '$' && text.charAt(i - 1) != '\\') return i;
        }
        return -1;
    }
    private static void flush(List<Segment> segments, StringBuilder plain){
        if(!plain.isEmpty()) segments.add(new Segment(Type.TEXT, plain.toString()));
        plain.setLength(0);
    }
}

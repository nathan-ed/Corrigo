/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.document.editions.elements;

import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.utils.MathText;
import fr.clementgre.pdf4teachers.utils.fonts.FontUtils;
import org.scilab.forge.jlatexmath.ParseException;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders a text mixing plain text and LaTeX ($…$) into one image: the plain text in the font, size and color of the text element,
 * the formulas with jlatexmath on the same baseline, and the lines wrapped at the max width of the element.
 * The same image is displayed and exported, so the copy looks the same in the app and in the PDF.
 */
public final class MixedTextRenderer {

    // Result of a render. error is the first LaTeX error, or null.
    public record Result(BufferedImage image, String error) {}

    private static final Map<String, java.awt.Font> fonts = new ConcurrentHashMap<>();

    private MixedTextRenderer(){
    }

    // A piece of a line: a word or a space, a formula, or a line break.
    private sealed interface Piece permits Word, Formula, Break {}
    private record Word(String text, boolean space) implements Piece {}
    private record Formula(TeXIcon icon) implements Piece {}
    private record Break() implements Piece {}

    /**
     * @param size Font size in layout pixels (the image is scale times larger).
     * @param maxWidth Max width of a line in layout pixels, 0 for no limit.
     */
    public static Result render(List<MathText.Segment> segments, String family, boolean bold, boolean italic, double size,
                                Color color, double maxWidth, float scale){
        java.awt.Font font = getFont(family, italic, bold).deriveFont((float) (size * scale));
        java.awt.Font fallback = new java.awt.Font(java.awt.Font.SANS_SERIF, font.getStyle(), 1).deriveFont(font.getSize2D());
        FontRenderContext frc = new FontRenderContext(null, true, true);

        int texStyle = 0;
        if(bold) texStyle |= TeXFormula.BOLD;
        if(italic) texStyle |= TeXFormula.ITALIC;

        // PIECES
        String error = null;
        ArrayList<Piece> pieces = new ArrayList<>();
        for(MathText.Segment segment : segments){
            if(segment.type() == MathText.Type.MATH){
                try{
                    TeXFormula formula = new TeXFormula(segment.content().replace("\n", " "));
                    formula.setColor(color);
                    TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, (float) (size * scale * TextElement.SIZE_FACTOR), texStyle);
                    icon.setForeground(color);
                    pieces.add(new Formula(icon));
                }catch(ParseException | IllegalArgumentException e){
                    if(error == null) error = e.getMessage();
                    addWords(pieces, "$" + segment.content() + "$"); // Shown as written, to be corrected
                }
            }else{
                addWords(pieces, segment.content());
            }
        }

        // LINES
        LineMetrics metrics = font.getLineMetrics("Ag", frc);
        float textAscent = metrics.getAscent();
        float textDescent = metrics.getDescent() + metrics.getLeading();
        double limit = maxWidth <= 0 ? Double.MAX_VALUE : maxWidth * scale;

        ArrayList<ArrayList<Piece>> lines = new ArrayList<>();
        ArrayList<Piece> line = new ArrayList<>();
        double lineWidth = 0;
        for(Piece piece : pieces){
            if(piece instanceof Break){
                lines.add(line);
                line = new ArrayList<>();
                lineWidth = 0;
                continue;
            }
            if(piece instanceof Word word && word.space() && line.isEmpty()) continue; // No space at the start of a wrapped line
            double width = width(piece, font, fallback, frc);
            if(lineWidth + width > limit && !line.isEmpty() && !(piece instanceof Word word && word.space())){
                trimTrailingSpaces(line);
                lines.add(line);
                line = new ArrayList<>();
                lineWidth = 0;
            }
            line.add(piece);
            lineWidth += width;
        }
        trimTrailingSpaces(line);
        lines.add(line);

        // SIZE
        double imageWidth = 1;
        double imageHeight = 0;
        double[] ascents = new double[lines.size()];
        double[] descents = new double[lines.size()];
        for(int i = 0; i < lines.size(); i++){
            double ascent = textAscent, descent = textDescent, width = 0;
            for(Piece piece : lines.get(i)){
                width += width(piece, font, fallback, frc);
                if(piece instanceof Formula formula){
                    ascent = Math.max(ascent, formula.icon().getIconHeight() - formula.icon().getIconDepth());
                    descent = Math.max(descent, formula.icon().getIconDepth());
                }
            }
            ascents[i] = ascent;
            descents[i] = descent;
            imageWidth = Math.max(imageWidth, width);
            imageHeight += ascent + descent;
        }

        // DRAW
        BufferedImage image = new BufferedImage((int) Math.ceil(imageWidth), (int) Math.max(1, Math.ceil(imageHeight)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(color);
        double y = 0;
        for(int i = 0; i < lines.size(); i++){
            double baseline = y + ascents[i];
            double x = 0;
            for(Piece piece : lines.get(i)){
                if(piece instanceof Word word){
                    x += drawText(g, word.text(), font, fallback, frc, x, baseline);
                }else if(piece instanceof Formula formula){
                    TeXIcon icon = formula.icon();
                    icon.paintIcon(null, g, (int) Math.round(x), (int) Math.round(baseline - (icon.getIconHeight() - icon.getIconDepth())));
                    x += icon.getIconWidth();
                }
            }
            y += ascents[i] + descents[i];
        }
        g.dispose();
        return new Result(image, error);
    }

    // First LaTeX error of the text, or null.
    public static String getError(List<MathText.Segment> segments){
        for(MathText.Segment segment : segments){
            if(segment.type() != MathText.Type.MATH) continue;
            try{
                new TeXFormula(segment.content().replace("\n", " "));
            }catch(ParseException | IllegalArgumentException e){
                return e.getMessage();
            }
        }
        return null;
    }

    private static void addWords(List<Piece> pieces, String text){
        String[] lines = text.split("\n", -1);
        for(int l = 0; l < lines.length; l++){
            if(l > 0) pieces.add(new Break());
            StringBuilder word = new StringBuilder();
            for(char c : lines[l].toCharArray()){
                if(c == ' '){
                    if(!word.isEmpty()) pieces.add(new Word(word.toString(), false));
                    word.setLength(0);
                    pieces.add(new Word(" ", true));
                }else word.append(c);
            }
            if(!word.isEmpty()) pieces.add(new Word(word.toString(), false));
        }
    }
    private static void trimTrailingSpaces(List<Piece> line){
        while(!line.isEmpty() && line.getLast() instanceof Word word && word.space()) line.removeLast();
    }

    private static double width(Piece piece, java.awt.Font font, java.awt.Font fallback, FontRenderContext frc){
        if(piece instanceof Word word){
            double width = 0;
            for(String run : splitByFont(word.text(), font)){
                java.awt.Font runFont = font.canDisplay(run.codePointAt(0)) ? font : fallback;
                width += runFont.getStringBounds(run, frc).getWidth();
            }
            return width;
        }
        if(piece instanceof Formula formula) return formula.icon().getIconWidth();
        return 0;
    }
    // Draws with the fallback font the characters the font does not have. Returns the width.
    private static double drawText(Graphics2D g, String text, java.awt.Font font, java.awt.Font fallback, FontRenderContext frc, double x, double baseline){
        double start = x;
        for(String run : splitByFont(text, font)){
            java.awt.Font runFont = font.canDisplay(run.codePointAt(0)) ? font : fallback;
            g.setFont(runFont);
            g.drawString(run, (float) x, (float) baseline);
            x += runFont.getStringBounds(run, frc).getWidth();
        }
        return x - start;
    }
    // Splits the text in runs of characters the font can display, and runs it can't.
    private static List<String> splitByFont(String text, java.awt.Font font){
        ArrayList<String> runs = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        Boolean displayable = null;
        for(int i = 0; i < text.length(); ){
            int codePoint = text.codePointAt(i);
            boolean can = font.canDisplay(codePoint);
            if(displayable != null && can != displayable){
                runs.add(run.toString());
                run.setLength(0);
            }
            displayable = can;
            run.appendCodePoint(codePoint);
            i += Character.charCount(codePoint);
        }
        if(!run.isEmpty()) runs.add(run.toString());
        return runs;
    }

    // Whether the font of a text has this character (the export can only write the characters of the font).
    public static boolean canDisplay(String family, boolean italic, boolean bold, int codePoint){
        if(family == null) return false;
        return getFont(family, italic, bold).canDisplay(codePoint);
    }
    
    // The font files of the app and of the system, as for the export.
    private static java.awt.Font getFont(String family, boolean italic, boolean bold){
        return fonts.computeIfAbsent(family + "|" + italic + "|" + bold, key -> {
            try(InputStream stream = FontUtils.getFontFile(family, italic, bold)){
                if(stream != null) return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream);
            }catch(Exception e){
                Log.w("Unable to load the font " + family + " for a text with LaTeX: " + e.getMessage());
            }
            int style = (bold ? java.awt.Font.BOLD : 0) | (italic ? java.awt.Font.ITALIC : 0);
            return new java.awt.Font(java.awt.Font.SANS_SERIF, style, 1);
        });
    }
}

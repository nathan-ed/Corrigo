/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation;

import fr.clementgre.pdf4teachers.document.editions.elements.MixedTextRenderer;
import fr.clementgre.pdf4teachers.document.render.display.PageRenderer;
import fr.clementgre.pdf4teachers.utils.MathText;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Image of the page of a copy where a comment is written: the page of the PDF, with the comment drawn
 * as on the copy (its font, color and formulas) and highlighted. The other annotations of the copy are not drawn.
 */
public final class PagePreview {

    // Grid size of a page (Element.GRID_WIDTH / GRID_HEIGHT)
    private static final double GRID_WIDTH = 165400;
    private static final double GRID_HEIGHT = 233900;
    private static final float DPI = 130; // Sharp up to about 1100 pixels wide, ~6 MB per page

    // The page, and where the comment is on it (pixels), to zoom on it.
    public record Preview(BufferedImage image, Rectangle comment) {}
    
    private PagePreview(){
    }

    // Previews of usages of the same copy (the PDF is read once). An image is null if its page could not be rendered.
    public static List<Preview> render(File pdf, List<CommentUsages.Usage> usages) throws IOException{
        ArrayList<Preview> images = new ArrayList<>();
        try(PDDocument document = Loader.loadPDF(new RandomAccessReadBufferedFile(pdf))){
            PDFRenderer renderer = new PDFRenderer(document);
            for(CommentUsages.Usage usage : usages){
                if(usage.page() < 0 || usage.page() >= document.getNumberOfPages()){
                    images.add(null);
                    continue;
                }
                images.add(render(renderer.renderImageWithDPI(usage.page(), DPI), usage));
            }
        }
        return images;
    }

    private static Preview render(BufferedImage page, CommentUsages.Usage usage){
        int width = page.getWidth(), height = page.getHeight();
        double scale = width / (double) PageRenderer.PAGE_WIDTH; // Layout pixels -> image pixels
        int x = (int) Math.round(usage.x() / GRID_WIDTH * width);
        int y = (int) Math.round(usage.y() / GRID_HEIGHT * height);

        BufferedImage comment = renderComment(usage, scale);
        int commentHeight = comment == null ? (int) (20 * scale) : comment.getHeight();
        int commentWidth = comment == null ? (int) (100 * scale) : comment.getWidth();

        Graphics2D g = page.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int pad = (int) (3 * scale);
        g.setColor(new Color(255, 214, 0, 70));
        g.fillRoundRect(x - pad, y - pad, commentWidth + 2 * pad, commentHeight + 2 * pad, 3 * pad, 3 * pad);
        g.setColor(new Color(255, 160, 0, 230));
        g.setStroke(new BasicStroke((float) Math.max(1.5, scale)));
        g.drawRoundRect(x - pad, y - pad, commentWidth + 2 * pad, commentHeight + 2 * pad, 3 * pad, 3 * pad);
        if(comment != null) g.drawImage(comment, x, y, null);
        g.dispose();

        return new Preview(page, new Rectangle(x - pad, y - pad, commentWidth + 2 * pad, commentHeight + 2 * pad));
    }

    // The comment as written on the copy, or null if it has no style.
    private static BufferedImage renderComment(CommentUsages.Usage usage, double scale){
        CommentBank.Style style = usage.style();
        if(style == null) return null;
        // Old LibreOffice math texts: shown as text
        String text = MathText.isLegacy(usage.text()) ? usage.text().replace(MathText.LEGACY_STARMATH, "") : usage.text();
        double maxWidth = PageRenderer.PAGE_WIDTH * (style.maxWidth() <= 0 ? 90 : style.maxWidth()) / 100d;
        return MixedTextRenderer.render(MathText.parse(text), style.font(), style.bold(), style.italic(), style.size(),
                parseColor(style.color()), maxWidth, (float) scale).image();
    }

    // "0xrrggbbaa" (JavaFX Color.toString)
    static Color parseColor(String color){
        try{
            String hex = color.startsWith("0x") ? color.substring(2) : color.replace("#", "");
            long value = Long.parseLong(hex, 16);
            if(hex.length() == 8) return new Color((int) (value >> 24) & 255, (int) (value >> 16) & 255, (int) (value >> 8) & 255, (int) value & 255);
            return new Color((int) value);
        }catch(RuntimeException e){
            return Color.RED;
        }
    }
}

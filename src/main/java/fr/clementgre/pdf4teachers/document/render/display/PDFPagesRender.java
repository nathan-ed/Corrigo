/*
 * Copyright (c) 2019-2025. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.document.render.display;

import fr.clementgre.pdf4teachers.interfaces.windows.MainWindow;
import fr.clementgre.pdf4teachers.interfaces.windows.language.TR;
import fr.clementgre.pdf4teachers.interfaces.windows.log.Log;
import fr.clementgre.pdf4teachers.utils.PlatformUtils;
import fr.clementgre.pdf4teachers.utils.dialogs.AlertIconType;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ButtonPosition;
import fr.clementgre.pdf4teachers.utils.dialogs.alerts.ErrorAlert;
import fr.clementgre.pdf4teachers.utils.interfaces.CallBackArg;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class PDFPagesRender {
    
    private record RenderCacheKey(String filePath, long lastModified, int page, int width) {}
    private record RenderPending(PageRenderer page, int width, CallBackArg<Image> callBack) {}
    private static final int MAX_PRELOADED_PAGES = 8;
    private static final Map<RenderCacheKey, Image> preloadedPages = new LinkedHashMap<>(16, .75f, true){
        @Override
        protected boolean removeEldestEntry(Map.Entry<RenderCacheKey, Image> eldest){
            return size() > MAX_PRELOADED_PAGES;
        }
    };
    private static final AtomicLong preloadGeneration = new AtomicLong();
    private static final ExecutorService preloadExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "PDF Exercise Page Preloader");
        thread.setDaemon(true);
        return thread;
    });
    
    
    private final File file;
    public PDFPagesEditor editor;
    private PDDocument document;
    private PDFRenderer pdfRenderer;
    
    private final ArrayList<RenderPending> rendersPending = new ArrayList<>();
    
    public boolean advertisement;
    private boolean pauseRendering;
    private boolean pauseRenderingInner;
    private boolean shouldClose;
    private boolean isClosed;
    
    public PDFPagesRender(File file) throws IOException {
        this.file = file;
        
        loadDocument();
        editor = new PDFPagesEditor(this);
        setupThread();
    }
    private void loadDocument() throws IOException{
        Log.d("Loading PDF Document at path " + file.toPath());
        document = Loader.loadPDF(new RandomAccessReadBufferedFile(file));
        pdfRenderer = new PDFRenderer(document);
        resumeRendering();
        pauseRenderingInner = false;
    }
    /**
     * Since PDFBox v3.0, it is unsafe to overwrite a file directly using document.save(file).
     * This method saves the document to a temporary file, replaces the original file with the temporary file, and reopens the document.
     *
     * @param file The file to save the document to
     * @return True if the document was saved successfully, false if an error occurred
     */
    public boolean saveDocumentTo(File file){
        try{
            pauseRenderingInner = true;
            Log.i("Saving document to file " + file.toPath() + " using temporary file.");
            
            File tempFile = File.createTempFile("tempFile", ".pdf");
            document.save(tempFile);
            document.close();
            
            Files.move(tempFile.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            loadDocument();
        }catch(IOException e){
            Log.e(e);
            ErrorAlert alert = new ErrorAlert(TR.tr("dialog.error.unableToSavePDFPagesEdits"), e.getMessage(), false);
            alert.getButtonTypes().clear();
            alert.addIgnoreButton(ButtonPosition.CLOSE);
            alert.addDefaultButton(TR.tr("actions.retry"));
            
            if(alert.getShowAndWaitIsDefaultButton()) return saveDocumentTo(file);
            
            MainWindow.mainScreen.closeFile(false, false, true);
            return false;
        }
        return true;
    }
    private void setupThread(){
        new Thread(() -> {
            while(!shouldClose){ // not closed
                RenderPending renderPending = pollRenderPending();
                if(!pauseRendering && !pauseRenderingInner && renderPending != null){ // Render
                    if(renderPending.page.getPage() < getNumberOfPages()){
                        renderPage(renderPending);
                    }else
                        Log.w("Unable to render page " + renderPending.page.getPage() + " (index out of bounds : page doesn't exist)");
                }else{ // Wait
                    PlatformUtils.sleepThread(100);
                }
            }
            
            // Close
            Log.d("Closing Page Renderer Thread and closing document...");
            pdfRenderer = null;
            while(editor.isEdited()){ // wait until document pages are saved
                PlatformUtils.sleepThread(100);
            }
            try{
                document.close();
            }catch(IOException e){ Log.eNotified(e); }
            document = null;
            isClosed = true;
            
        }, "Page Renderer").start();
        
        // Save document pages each 10 seconds if needed
        new Thread(() -> {
            PlatformUtils.sleepThread(10000);
            while(!shouldClose){
                PlatformUtils.runAndWait(() -> {
                    editor.saveEditsIfNeeded();
                    return null;
                });
                PlatformUtils.sleepThread(10000);
            }
        }, "Page Editor Saver").start();
    }
    private RenderPending pollRenderPending(){
        if(pauseRendering || pauseRenderingInner) return null;
        
        synchronized(rendersPending){
            while(!rendersPending.isEmpty()){
                RenderPending renderPending = rendersPending.removeFirst();
                if(!renderPending.page.isRemoved()) return renderPending;
            }
        }
        return null;
    }
    public void clearPendingRenders(){
        synchronized(rendersPending){
            rendersPending.clear();
        }
    }
    
    private void renderPage(RenderPending renderPending){
        RenderCacheKey cacheKey = cacheKey(file, renderPending.page.getPage(), renderPending.width);
        Image cachedImage = getPreloadedPage(cacheKey);
        if(cachedImage != null){
            Platform.runLater(() -> renderPending.callBack.call(cachedImage));
            return;
        }
    
        BufferedImage renderImage = null;
        try{
            renderImage = renderPageToImage(document, pdfRenderer, renderPending.page.getPage(), renderPending.width);
            
            if(renderPending.page.isRemoved()){
                // Nothing
            }else if(document == null){
                Platform.runLater(() -> renderPending.callBack.call(null));
            }else{
                Image image = SwingFXUtils.toFXImage(renderImage, null);
                Platform.runLater(() -> renderPending.callBack.call(image));
            }
        }catch(Exception e){
            Log.eNotified(e);
            Platform.runLater(() -> renderPending.callBack.call(null));
        }
        
        if(renderImage != null) renderImage.flush();
    }
    
    public void renderPage(PageRenderer page, double size, CallBackArg<Image> callBack){
        renderPage(page, size, callBack, true);
    }
    public void renderPage(PageRenderer page, double size, CallBackArg<Image> callBack, boolean priority){
        // *1=595 | *1.5=892 |*2=1190
        RenderPending renderPending = new RenderPending(page, PageRenderer.getRenderWidth(size), callBack);
        Image cachedImage = getPreloadedPage(cacheKey(file, page.getPage(), renderPending.width));
        if(cachedImage != null){
            Platform.runLater(() -> callBack.call(cachedImage));
            return;
        }
        synchronized(rendersPending){
            // A page has only one pending render: merge with the previous request, keeping its callback.
            for(RenderPending pending : new ArrayList<>(rendersPending)){
                if(pending.page != page) continue;
                rendersPending.remove(pending);
                CallBackArg<Image> newCallBack = renderPending.callBack;
                renderPending = new RenderPending(page, renderPending.width, image -> {
                    pending.callBack.call(image);
                    newCallBack.call(image);
                });
            }
            if(priority) rendersPending.addFirst(renderPending);
            else rendersPending.add(renderPending);
        }
    }
    public void prioritizePage(PageRenderer page){
        synchronized(rendersPending){
            for(RenderPending pending : rendersPending){
                if(pending.page == page){
                    rendersPending.remove(pending);
                    rendersPending.addFirst(pending);
                    return;
                }
            }
        }
    }
    
    // Renders pages of other files in background. A new call cancels the files not yet started by the previous one,
    // so that quickly switching files doesn't leave a queue of preloads for files that are no longer neighbors.
    public static void preloadPages(List<File> files, int firstPage, int lastPage, int width){
        if(files.isEmpty() || width <= 0) return;
        long generation = preloadGeneration.incrementAndGet();
        
        preloadExecutor.submit(() -> {
            for(File file : files){
                if(preloadGeneration.get() != generation) return;
                if(file == null || isPreloaded(file, firstPage, lastPage, width)) continue;
                
                try(PDDocument document = Loader.loadPDF(new RandomAccessReadBufferedFile(file))){
                    PDFRenderer renderer = new PDFRenderer(document);
                    int from = Math.max(0, firstPage);
                    int to = Math.min(document.getNumberOfPages() - 1, lastPage);
                    for(int page = from; page <= to; page++){
                        if(preloadGeneration.get() != generation) return;
                        RenderCacheKey cacheKey = cacheKey(file, page, width);
                        if(getPreloadedPage(cacheKey) != null) continue;
                        BufferedImage renderImage = renderPageToImage(document, renderer, page, width);
                        putPreloadedPage(cacheKey, SwingFXUtils.toFXImage(renderImage, null));
                        renderImage.flush();
                    }
                }catch(Exception e){
                    Log.eNotified(e);
                }
            }
        });
    }
    private static boolean isPreloaded(File file, int firstPage, int lastPage, int width){
        for(int page = Math.max(0, firstPage); page <= lastPage; page++){
            if(getPreloadedPage(cacheKey(file, page, width)) == null) return false;
        }
        return true;
    }
    
    private static BufferedImage renderPageToImage(PDDocument document, PDFRenderer renderer, int pageNumber, int width) throws IOException{
        PDRectangle pageSize = getPageRotatedCropBox(document, pageNumber);
        BufferedImage renderImage = new BufferedImage(Math.max(1, width), (int) Math.max(1, pageSize.getHeight() / pageSize.getWidth() * ((double) width)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = renderImage.createGraphics();
        graphics.setBackground(Color.WHITE);
        try{
            renderer.renderPageToGraphics(pageNumber, graphics,
                    (float) width / pageSize.getWidth(),
                    (float) width / pageSize.getWidth(),
                    RenderDestination.VIEW);
            return renderImage;
        }finally{
            graphics.dispose();
        }
    }
    
    private static RenderCacheKey cacheKey(File file, int page, int width){
        return new RenderCacheKey(file.getAbsolutePath(), file.lastModified(), page, width);
    }
    private static Image getPreloadedPage(RenderCacheKey key){
        synchronized(preloadedPages){
            return preloadedPages.get(key);
        }
    }
    private static void putPreloadedPage(RenderCacheKey key, Image image){
        synchronized(preloadedPages){
            preloadedPages.put(key, image);
        }
    }
    
    public BufferedImage renderPageBasic(int pageNumber, int width, int height){
        
        PDRectangle pageSize = getPageRotatedCropBox(pageNumber);
        
        BufferedImage renderImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = renderImage.createGraphics();
        graphics.setBackground(Color.WHITE);
        
        try{
            pdfRenderer.renderPageToGraphics(pageNumber, graphics, width / pageSize.getWidth(), width / pageSize.getWidth(), RenderDestination.VIEW);
            scale(pdfRenderer.renderImage(pageNumber, 3, ImageType.RGB), 1800);
            graphics.dispose();
            
            return renderImage;
        }catch(Exception e){
            Log.eNotified(e);
            return null;
        }
    }
    
    public static void renderAdvertisement(){
        if(MainWindow.mainScreen.hasDocument(false)){
            if(!MainWindow.mainScreen.document.pdfPagesRender.advertisement){ // not already sended
                MainWindow.mainScreen.document.pdfPagesRender.advertisement = true;
                
                // send notifications here
                MainWindow.showNotification(AlertIconType.WARNING, TR.tr("document.loadErrorNotification"), 20);
            }
        }
    }
    
    public void close(){
        shouldClose = true;
        editor.saveEditsIfNeeded();
    }
    public boolean isClosed(){
        return isClosed;
    }
    
    public PDDocument getDocument(){
        return document;
    }
    
    public int getNumberOfPages(){
        return document.getNumberOfPages();
    }
    
    public void pauseRendering(){
        pauseRenderingInner = true;
    }
    public void resumeRendering(){
        pauseRenderingInner = false;
    }
    
    public File getFile(){
        return file;
    }
    
    public PDRectangle getPageRotatedCropBox(int pageNumber){
        return getPageRotatedCropBox(document, pageNumber);
    }
    private static PDRectangle getPageRotatedCropBox(PDDocument document, int pageNumber){
        PDPage page = document.getPage(pageNumber);
        PDRectangle pageSize;
        if(page.getRotation() == 90 || page.getRotation() == 270)
            pageSize = new PDRectangle(page.getCropBox().getHeight(), page.getCropBox().getWidth());
        else pageSize = page.getCropBox();
        
        return pageSize;
    }
    
    public BufferedImage scale(BufferedImage img, double width){
        
        if(img.getWidth() < width){
            return img;
        }
        
        int destWidth = (int) (width);
        int destHeight = (int) (img.getHeight() / ((double) img.getWidth()) * width);
        
        //créer l'image de destination
        GraphicsConfiguration configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        BufferedImage newImg = configuration.createCompatibleImage(destWidth, destHeight);
        
        Graphics2D graphics = newImg.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        
        //dessiner l'image de destination
        graphics.drawImage(img, 0, 0, destWidth, destHeight, 0, 0, img.getWidth(), img.getHeight(), null);
        graphics.dispose();
        
        return newImg;
    }
}

/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.scoredcomments;

import java.util.*;
import java.util.regex.Pattern;

// The scored comments of one evaluation, and the starting value of each of its sub-grades.
public class ScoredCommentCatalog {

    // Value a sub-grade starts from before adding the points of its comments.
    public enum Base { FULL, ZERO }

    private final ArrayList<ScoredComment> comments = new ArrayList<>();
    // Only the sub-grades that don't use the default base (FULL) are stored.
    private final LinkedHashMap<String, Base> bases = new LinkedHashMap<>();

    public List<ScoredComment> getComments(){
        return Collections.unmodifiableList(comments);
    }
    public Optional<ScoredComment> get(String id){
        if(id == null) return Optional.empty();
        return comments.stream().filter(c -> c.getId().equals(id)).findFirst();
    }
    public void add(ScoredComment comment){
        comments.add(comment);
    }
    public void addAfter(ScoredComment comment, ScoredComment after){
        int index = comments.indexOf(after);
        if(index < 0) comments.add(comment);
        else comments.add(index + 1, comment);
    }
    public boolean remove(String id){
        return comments.removeIf(c -> c.getId().equals(id));
    }
    public boolean isEmpty(){
        return comments.isEmpty() && bases.isEmpty();
    }

    public Base getBase(String gradePath){
        return bases.getOrDefault(gradePath, Base.FULL);
    }
    public void setBase(String gradePath, Base base){
        if(base == Base.FULL) bases.remove(gradePath);
        else bases.put(gradePath, base);
    }

    // Updates the paths of the entries of a renamed grade and of its sub-grades. Returns true if something changed.
    public boolean renameGradePath(String oldPath, String newPath){
        if(oldPath.equals(newPath)) return false;
        boolean changed = false;
        for(ScoredComment comment : comments){
            String renamed = renamePath(comment.getGradePath(), oldPath, newPath);
            if(renamed != null){
                comment.setGradePath(renamed);
                changed = true;
            }
        }
        LinkedHashMap<String, Base> renamedBases = new LinkedHashMap<>();
        for(Map.Entry<String, Base> entry : bases.entrySet()){
            String renamed = renamePath(entry.getKey(), oldPath, newPath);
            if(renamed != null) changed = true;
            renamedBases.put(renamed == null ? entry.getKey() : renamed, entry.getValue());
        }
        bases.clear();
        bases.putAll(renamedBases);
        return changed;
    }
    // Returns the renamed path, or null if the path is not oldPath or one of its sub-paths.
    public static String renamePath(String path, String oldPath, String newPath){
        if(path.equals(oldPath)) return newPath;
        if(path.startsWith(oldPath + "\\")) return newPath + path.substring(oldPath.length());
        return null;
    }

    public ScoredCommentCatalog copy(){
        ScoredCommentCatalog copy = new ScoredCommentCatalog();
        comments.forEach(c -> copy.comments.add(c.copy()));
        copy.bases.putAll(bases);
        return copy;
    }

    // READERS AND WRITERS

    public LinkedHashMap<String, Object> toYAML(){
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        LinkedHashMap<String, Object> basesData = new LinkedHashMap<>();
        bases.forEach((path, base) -> basesData.put(path, base.name()));
        data.put("bases", basesData);
        data.put("comments", new ArrayList<>(comments.stream().map(ScoredComment::toYAML).toList()));
        return data;
    }
    public static ScoredCommentCatalog fromYAML(Map<?, ?> data){
        ScoredCommentCatalog catalog = new ScoredCommentCatalog();
        if(data.get("bases") instanceof Map<?, ?> basesData){
            for(Map.Entry<?, ?> entry : basesData.entrySet()){
                try{
                    catalog.setBase(entry.getKey().toString(), Base.valueOf(String.valueOf(entry.getValue())));
                }catch(IllegalArgumentException ignored){
                    // Ignore malformed user config entries.
                }
            }
        }
        if(data.get("comments") instanceof List<?> commentsData){
            for(Object commentData : commentsData){
                if(!(commentData instanceof Map<?, ?> map)) continue;
                ScoredComment comment = ScoredComment.fromYAML(map);
                if(comment != null && catalog.get(comment.getId()).isEmpty()) catalog.add(comment);
            }
        }
        return catalog;
    }

    // EVALUATIONS

    // The catalog of the evaluation sharing the most exercises with this signature, or null if none share any.
    public static ScoredCommentCatalog findClosest(Map<String, ScoredCommentCatalog> catalogs, String signature){
        List<String> keys = splitSignature(signature);
        ScoredCommentCatalog closest = null;
        long closestCount = 0;
        for(Map.Entry<String, ScoredCommentCatalog> entry : catalogs.entrySet()){
            if(entry.getValue().isEmpty()) continue;
            List<String> otherKeys = splitSignature(entry.getKey());
            long count = keys.stream().filter(otherKeys::contains).count();
            if(count > closestCount){
                closest = entry.getValue();
                closestCount = count;
            }
        }
        return closest;
    }
    private static List<String> splitSignature(String signature){
        if(signature == null || signature.isBlank()) return List.of();
        return Arrays.asList(signature.split(Pattern.quote(" | ")));
    }
}

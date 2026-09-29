/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.scoredcomments;

import corrigo.panel.sidebar.grades.ExercisePageMapping;

import java.text.NumberFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Grade computation and edit file (YAML) operations of the scored comments. Does not depend on JavaFX.
public class ScoredCommentGrades {

    // Keys added to the text elements data of the edit files
    public static final String KEY_ID = "scoredCommentId";
    public static final String KEY_GRADE_PATH = "gradePath";
    public static final String KEY_COMMENT = "comment";
    public static final String KEY_POINTS = "points";
    public static final String KEY_LOCAL_TEXT = "localText";
    public static final String KEY_LOCAL_POINTS = "localPoints";
    // A comment only (method or mistake without points): not counted in the grade
    public static final String KEY_NO_POINTS = "noPoints";
    // Key added to the grades data of the edit files
    public static final String KEY_VALUE_SOURCE = "valueSource";
    public static final String VALUE_SOURCE_COMMENTS = "COMMENTS";
    // Value of the grade before it was computed from comments (typed, or -1): given back when no comment counts any more
    public static final String KEY_VALUE_BEFORE = "valueBeforeComments";

    public static final String MINUS = "−";

    // "Comment (−0.5)", "Comment (+1)" or "Comment (0)". Unsigned numbers are not points: "see question (2)".
    private static final Pattern RENDERED_WITH_COMMENT = Pattern.compile("(?s)^(.*?)\\s*\\(\\s*([+\\-−]\\s*\\d+(?:[.,]\\d+)?|0(?:[.,]0+)?)\\s*\\)\\s*$");
    // "−0.5" alone (comment with no text)
    private static final Pattern RENDERED_POINTS_ONLY = Pattern.compile("^\\s*([+\\-−]\\s*\\d+(?:[.,]\\d+)?)\\s*$");

    private ScoredCommentGrades(){
    }

    // LEAF VALUE

    public static double computeLeaf(double total, ScoredCommentCatalog.Base base, Collection<Double> points){
        double value = base == ScoredCommentCatalog.Base.ZERO ? 0 : total;
        for(double p : points) value += p;
        return Math.max(0, Math.min(total, value));
    }

    // TEXT

    public static String formatPoints(double points, NumberFormat format){
        if(points > 0) return "+" + format.format(points);
        if(points < 0) return MINUS + format.format(-points);
        return format.format(0);
    }
    public static String render(String comment, double points, NumberFormat format){
        if(comment == null || comment.isBlank()) return formatPoints(points, format);
        return comment + " (" + formatPoints(points, format) + ")";
    }

    public record Parsed(String comment, double points) {}

    // Reads back a text written by render(). Empty if the text has no points.
    public static Optional<Parsed> parse(String text){
        if(text == null) return Optional.empty();
        Matcher matcher = RENDERED_WITH_COMMENT.matcher(text);
        if(matcher.matches()) return Optional.of(new Parsed(matcher.group(1), parsePoints(matcher.group(2))));
        matcher = RENDERED_POINTS_ONLY.matcher(text);
        if(matcher.matches()) return Optional.of(new Parsed("", parsePoints(matcher.group(1))));
        return Optional.empty();
    }
    private static double parsePoints(String points){
        String normalized = points.replace(MINUS, "-").replace(",", ".").replaceAll("\\s", "");
        return Double.parseDouble(normalized);
    }

    // EDIT FILES (YAML)

    // All the placed scored comments of an edit file.
    public static List<Map<String, Object>> getPlacedComments(Map<String, Object> editBase){
        return getPlacedCommentsWithPage(editBase).stream().map(PlacedData::data).toList();
    }
    
    // A placed scored comment of an edit file, and its page index.
    public record PlacedData(int page, Map<String, Object> data) {
        public String getComment(){
            return getString(data, KEY_COMMENT);
        }
        public double getPoints(){
            return getDouble(data, KEY_POINTS, 0);
        }
        public String getEntryId(){
            return getString(data, KEY_ID);
        }
        public String getGradePath(){
            return getString(data, KEY_GRADE_PATH);
        }
        public boolean hasLocalChanges(){
            return getBoolean(data, KEY_LOCAL_TEXT) || getBoolean(data, KEY_LOCAL_POINTS);
        }
    }
    public static List<PlacedData> getPlacedCommentsWithPage(Map<String, Object> editBase){
        ArrayList<PlacedData> placed = new ArrayList<>();
        if(!(editBase.get("texts") instanceof Map<?, ?> texts)) return placed;
        for(Map.Entry<?, ?> pageTexts : texts.entrySet()){
            if(!(pageTexts.getValue() instanceof List<?> list)) continue;
            int page;
            try{
                page = Integer.parseInt(String.valueOf(pageTexts.getKey()).replaceFirst("page", ""));
            }catch(NumberFormatException e){
                continue;
            }
            for(Object text : list){
                if(text instanceof Map<?, ?> map && map.get(KEY_ID) != null) placed.add(new PlacedData(page, castMap(map)));
            }
        }
        return placed;
    }
    
    // True if the comment or its sub-grade contains all the words of the query (case and accents ignored).
    public static boolean matchesQuery(String comment, String gradePath, String query){
        String haystack = normalize(comment + " " + gradePath);
        for(String word : normalize(query).split("\\s+")){
            if(!word.isEmpty() && !haystack.contains(word)) return false;
        }
        return true;
    }
    private static String normalize(String text){
        return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }
    public static List<Map<String, Object>> getGrades(Map<String, Object> editBase){
        ArrayList<Map<String, Object>> grades = new ArrayList<>();
        if(!(editBase.get("grades") instanceof List<?> list)) return grades;
        for(Object grade : list){
            if(grade instanceof Map<?, ?> map) grades.add(castMap(map));
        }
        return grades;
    }

    public static String getGradePath(Map<String, Object> gradeData){
        return getString(gradeData, "parentPath") + "\\" + getString(gradeData, "name");
    }

    // Signature of the evaluation (see ExercisePageMapping.getSignature) of an edit file.
    public static String getSignature(Map<String, Object> editBase){
        List<Map<String, Object>> exercises = getGrades(editBase).stream()
                .filter(grade -> getTier(getString(grade, "parentPath")) == 1)
                .sorted(Comparator.comparingDouble(grade -> getDouble(grade, "index", 0)))
                .toList();
        return ExercisePageMapping.getSignature(ExercisePageMapping.buildExerciseKeys(exercises.stream().map(g -> getString(g, "name")).toList()));
    }

    // Root grade {value, total}, value being -1 if not filled. Null if there is no root.
    public static double[] getRootGrade(Map<String, Object> editBase){
        return getGrades(editBase).stream()
                .filter(grade -> getTier(getString(grade, "parentPath")) == 0)
                .findFirst()
                .map(grade -> new double[]{getDouble(grade, "value", -1), getDouble(grade, "total", 0)})
                .orElse(null);
    }

    /**
     * Sets the value of the sub-grades computed from comments, then the sums of their parents,
     * following the same rules as GradeTreeItem.makeSum.
     * @param isBonus tells if a grade name is a bonus grade (bonus grades are not counted in the totals).
     */
    public static void recomputeGrades(Map<String, Object> editBase, ScoredCommentCatalog catalog, Predicate<String> isBonus){
        HashMap<String, List<Double>> pointsByGrade = new HashMap<>();
        for(Map<String, Object> comment : getPlacedComments(editBase)){
            if(getBoolean(comment, KEY_NO_POINTS)) continue;
            pointsByGrade.computeIfAbsent(getString(comment, KEY_GRADE_PATH), k -> new ArrayList<>()).add(getDouble(comment, KEY_POINTS, 0));
        }

        List<Map<String, Object>> grades = getGrades(editBase);
        HashMap<String, List<Map<String, Object>>> childrenByPath = new HashMap<>();
        for(Map<String, Object> grade : grades){
            childrenByPath.computeIfAbsent(getString(grade, "parentPath"), k -> new ArrayList<>()).add(grade);
        }

        // Leaves
        for(Map<String, Object> grade : grades){
            String path = getGradePath(grade);
            if(childrenByPath.containsKey(path)) continue;
            if(!VALUE_SOURCE_COMMENTS.equals(grade.get(KEY_VALUE_SOURCE))) continue;

            List<Double> points = pointsByGrade.get(path);
            if(points == null || points.isEmpty()){
                grade.put("value", getDouble(grade, KEY_VALUE_BEFORE, -1));
                grade.remove(KEY_VALUE_SOURCE);
                grade.remove(KEY_VALUE_BEFORE);
            }else{
                ScoredCommentCatalog.Base base = catalog == null ? ScoredCommentCatalog.Base.FULL : catalog.getBase(path);
                grade.put("value", computeLeaf(getDouble(grade, "total", 0), base, points));
            }
        }

        // Parents, the deepest first
        List<Map<String, Object>> parents = grades.stream()
                .filter(grade -> childrenByPath.containsKey(getGradePath(grade)))
                .sorted(Comparator.comparingInt((Map<String, Object> grade) -> getTier(getString(grade, "parentPath"))).reversed())
                .toList();
        for(Map<String, Object> parent : parents){
            boolean hasValue = false;
            double value = 0;
            double total = 0;
            for(Map<String, Object> child : childrenByPath.get(getGradePath(parent))){
                if(!isBonus.test(getString(child, "name"))) total += getDouble(child, "total", 0);
                double childValue = getDouble(child, "value", -1);
                if(childValue >= 0){
                    hasValue = true;
                    value += childValue;
                }
            }
            parent.put("value", hasValue ? value : -1d);
            parent.put("total", total);
        }
    }

    public record ApplyResult(int linked, int withLocalChanges) {}

    // Updates the placed comments linked to this entry. The fields changed on a copy only are kept.
    public static ApplyResult applyEntry(Map<String, Object> editBase, ScoredComment entry, NumberFormat format){
        int linked = 0;
        int withLocalChanges = 0;
        for(Map<String, Object> placed : getPlacedComments(editBase)){
            if(!entry.getId().equals(String.valueOf(placed.get(KEY_ID)))) continue;
            linked++;
            boolean localText = getBoolean(placed, KEY_LOCAL_TEXT);
            boolean localPoints = getBoolean(placed, KEY_LOCAL_POINTS);
            if(localText || localPoints) withLocalChanges++;

            if(!localText) placed.put(KEY_COMMENT, entry.getText());
            if(!localPoints) placed.put(KEY_POINTS, entry.getPoints());
            placed.put(KEY_GRADE_PATH, entry.getGradePath());
            if(entry.getColor() != null) placed.put("color", toYAMLColor(entry.getColor()));
            placed.put("text", render(getString(placed, KEY_COMMENT), getDouble(placed, KEY_POINTS, 0), format));
        }
        return new ApplyResult(linked, withLocalChanges);
    }

    /**
     * Removes the placed comments linked to this entry, or turns them into plain texts. Returns the number of comments.
     * When they are kept as texts, the grades they changed keep their value (they become typed values).
     */
    public static int removeEntry(Map<String, Object> editBase, String id, boolean keepAsText){
        if(keepAsText){
            Set<String> gradePaths = new HashSet<>();
            for(Map<String, Object> placed : getPlacedComments(editBase)){
                if(id.equals(String.valueOf(placed.get(KEY_ID)))) gradePaths.add(getString(placed, KEY_GRADE_PATH));
            }
            for(Map<String, Object> grade : getGrades(editBase)){
                if(gradePaths.contains(getGradePath(grade))) grade.remove(KEY_VALUE_SOURCE);
            }
        }
        int count = 0;
        if(!(editBase.get("texts") instanceof Map<?, ?> texts)) return 0;
        for(Object pageTexts : texts.values()){
            if(!(pageTexts instanceof List<?> list)) continue;
            Iterator<?> iterator = list.iterator();
            while(iterator.hasNext()){
                if(!(iterator.next() instanceof Map<?, ?> map) || !id.equals(String.valueOf(map.get(KEY_ID)))) continue;
                count++;
                if(keepAsText) stripScoredCommentKeys(castMap(map));
                else iterator.remove();
            }
        }
        return count;
    }
    public static void stripScoredCommentKeys(Map<String, Object> textData){
        textData.remove(KEY_ID);
        textData.remove(KEY_GRADE_PATH);
        textData.remove(KEY_COMMENT);
        textData.remove(KEY_POINTS);
        textData.remove(KEY_LOCAL_TEXT);
        textData.remove(KEY_LOCAL_POINTS);
    }

    // JavaFX Color.toString() format, as written by TextElement.
    static String toYAMLColor(String hexColor){
        String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
        if(hex.length() == 6) hex += "ff";
        return "0x" + hex.toLowerCase();
    }

    // UTILS

    public static int getTier(String parentPath){
        if(parentPath == null) return 0;
        return (int) Arrays.stream(parentPath.split(Pattern.quote("\\"))).filter(s -> !s.isEmpty()).count();
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map){
        return (Map<String, Object>) map;
    }
    private static String getString(Map<String, Object> data, String key){
        Object value = data.get(key);
        return value == null ? "" : value.toString();
    }
    private static double getDouble(Map<String, Object> data, String key, double defaultValue){
        Object value = data.get(key);
        if(value instanceof Number number) return number.doubleValue();
        if(value == null) return defaultValue;
        try{
            return Double.parseDouble(value.toString());
        }catch(NumberFormatException e){
            return defaultValue;
        }
    }
    private static boolean getBoolean(Map<String, Object> data, String key){
        Object value = data.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
}

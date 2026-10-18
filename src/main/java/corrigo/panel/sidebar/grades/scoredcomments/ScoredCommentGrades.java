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
    // Start of the points kept from a previous version, so that its grade does not change (see keepStoredValues)
    public static final String KEY_START = "pointsStart";

    public static final String MINUS = "−";
    // A green tick written for a criterion of the grade scale: written alone, without its points
    public static final String CHECK = "$\\Large\\checkmark$";

    // "Comment (−0.5)", "Comment (+1)" or "Comment (0)". Unsigned numbers are not points: "see question (2)".
    private static final Pattern RENDERED_WITH_COMMENT = Pattern.compile("(?s)^(.*?)\\s*\\(\\s*([+\\-−]\\s*\\d+(?:[.,]\\d+)?|0(?:[.,]0+)?)\\s*\\)\\s*$");
    // "−0.5" alone (comment with no text)
    private static final Pattern RENDERED_POINTS_ONLY = Pattern.compile("^\\s*([+\\-−]\\s*\\d+(?:[.,]\\d+)?)\\s*$");

    private ScoredCommentGrades(){
    }

    // LEAF VALUE

    // How the points of an exercise count on a copy (ScoredCommentCatalog.Base): FULL "by mistakes", from the maximum;
    // ZERO "by criteria", from 0. Automatic: the first point placed in the exercise on the copy decides (a mistake: by
    // mistakes, a method or a criterion: by criteria), kept while points count (KEY_AUTO_MODE on the grade of the
    // exercise). It can be changed for the class (catalog, key: path of the exercise) and on a copy (KEY_MODE).
    public static final String KEY_MODE = "pointsMode";
    public static final String KEY_AUTO_MODE = "pointsAutoMode";

    // Mode chosen for a sub-grade: the one of its exercise on this copy, else the one chosen for the sub-grade (older
    // versions), else the one of its exercise for the class. Null: automatic.
    public static ScoredCommentCatalog.Base getChosenMode(ScoredCommentCatalog.Base copyMode, ScoredCommentCatalog catalog, String leafPath){
        if(copyMode != null) return copyMode;
        if(catalog == null) return null;
        ScoredCommentCatalog.Base chosen = catalog.getChosenBase(leafPath);
        return chosen != null ? chosen : catalog.getChosenBase(getExercisePath(leafPath));
    }
    // Mode of a sub-grade: chosen, else automatic (auto, null if no point is placed yet), else by mistakes
    public static ScoredCommentCatalog.Base getMode(ScoredCommentCatalog.Base chosen, ScoredCommentCatalog.Base auto){
        return chosen != null ? chosen : auto != null ? auto : ScoredCommentCatalog.Base.FULL;
    }
    // Exercise of a grade: "\Total\Ex 1" for "\Total\Ex 1\a" (and for itself)
    public static String getExercisePath(String gradePath){
        String[] parts = gradePath == null ? new String[0] : gradePath.split("\\\\");
        if(parts.length <= 3) return gradePath == null ? "" : gradePath;
        return "\\" + parts[1] + "\\" + parts[2];
    }

    /**
     * A sub-grade of an exercise on a copy: its value, whether it is computed from the points of its comments (else
     * typed, or -1), the value typed before (before), the start kept from a previous version (kept, -1 if none), the
     * points counted on it, whether its criterion is ticked, and whether a typed value gives way to the points (a point
     * was just placed on it).
     */
    public record Leaf(String path, double total, double value, boolean computed, double before, double kept,
                       List<Double> points, boolean ticked, boolean switchToComments) {
        public double typed(){
            return computed ? before : value;
        }
    }
    public record LeafValue(double value, boolean computed) {}
    // The values of the sub-grades, and the automatic mode of the exercise to keep (null: no point any more)
    public record ExerciseValues(List<LeafValue> values, ScoredCommentCatalog.Base auto) {}

    // Where the points of the sub-grade start from in this mode: 0 if its criterion is ticked, else the start kept from
    // a previous version (when the mode is not chosen), else the value typed by hand, else the maximum (by mistakes) or
    // 0 (by criteria).
    public static double getStart(Leaf leaf, ScoredCommentCatalog.Base mode, boolean useKept){
        if(leaf.ticked()) return 0;
        if(leaf.kept() >= 0 && useKept) return Math.min(leaf.kept(), leaf.total());
        if(leaf.typed() >= 0) return Math.min(leaf.typed(), leaf.total());
        return mode == ScoredCommentCatalog.Base.ZERO ? 0 : leaf.total();
    }
    // Automatic mode from the points placed, when it was not kept yet: by mistakes if there is a mistake, else by
    // criteria (a point is placed at a time on the open copy: it is the first one)
    private static ScoredCommentCatalog.Base guessAuto(List<Leaf> leaves){
        boolean mistake = leaves.stream().anyMatch(leaf -> leaf.points().stream().anyMatch(p -> p < 0));
        return mistake ? ScoredCommentCatalog.Base.FULL : ScoredCommentCatalog.Base.ZERO;
    }
    /**
     * The values of the sub-grades of an exercise on a copy (same order). With points: the start plus the points,
     * between 0 and the maximum. By criteria, once the exercise is started on the copy (a point placed or a value
     * typed), a sub-grade without anything is 0: what is shown is what counts. A value typed over computed points
     * stays, until a point is placed on it (an empty sub-grade is computed).
     * @param chosenOf the mode chosen for a sub-grade (getChosenMode), or null
     * @param auto the automatic mode kept for the exercise on the copy, or null
     */
    public static ExerciseValues computeExercise(List<Leaf> leaves, java.util.function.Function<String, ScoredCommentCatalog.Base> chosenOf,
                                                 ScoredCommentCatalog.Base auto){
        boolean anyPoints = leaves.stream().anyMatch(leaf -> !leaf.points().isEmpty());
        ScoredCommentCatalog.Base newAuto = !anyPoints ? null : auto != null ? auto : guessAuto(leaves);
        boolean started = anyPoints || leaves.stream().anyMatch(leaf -> !leaf.computed() && leaf.value() >= 0);
        ArrayList<LeafValue> values = new ArrayList<>();
        for(Leaf leaf : leaves){
            ScoredCommentCatalog.Base chosen = chosenOf.apply(leaf.path());
            ScoredCommentCatalog.Base mode = getMode(chosen, newAuto);
            if(!leaf.points().isEmpty() && (leaf.computed() || leaf.switchToComments() || leaf.value() < 0)){
                values.add(new LeafValue(computeLeafFrom(leaf.total(), getStart(leaf, mode, chosen == null), leaf.points()), true));
            }else if(leaf.points().isEmpty() && mode == ScoredCommentCatalog.Base.ZERO && started && leaf.typed() < 0){
                values.add(new LeafValue(0, true));
            }else{
                values.add(new LeafValue(leaf.typed(), false));
            }
        }
        return new ExerciseValues(values, newAuto);
    }
    public static double computeLeafFrom(double total, double start, Collection<Double> points){
        double value = start;
        for(double p : points) value += p;
        return Math.max(0, Math.min(total, value));
    }
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
        if(CHECK.equals(comment)) return CHECK;
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

    // Points counted on each sub-grade of an edit file
    private static HashMap<String, List<Double>> getPointsByGrade(Map<String, Object> editBase){
        HashMap<String, List<Double>> pointsByGrade = new HashMap<>();
        for(Map<String, Object> comment : getPlacedComments(editBase)){
            if(getBoolean(comment, KEY_NO_POINTS)) continue;
            pointsByGrade.computeIfAbsent(getString(comment, KEY_GRADE_PATH), k -> new ArrayList<>()).add(getDouble(comment, KEY_POINTS, 0));
        }
        return pointsByGrade;
    }
    // Sub-grades whose criterion is ticked on the copy of this edit file
    private static Set<String> getTickedGrades(Map<String, Object> editBase){
        Set<String> ticked = new HashSet<>();
        for(Map<String, Object> comment : getPlacedComments(editBase)){
            if(CHECK.equals(getString(comment, KEY_COMMENT)) && !getBoolean(comment, KEY_NO_POINTS)) ticked.add(getString(comment, KEY_GRADE_PATH));
        }
        return ticked;
    }
    // Mode of an exercise changed on the copy of this edit file, or null
    public static ScoredCommentCatalog.Base getCopyMode(Map<String, Object> exerciseGrade){
        return readMode(exerciseGrade, KEY_MODE);
    }
    // Automatic mode kept for an exercise on the copy of this edit file, or null
    public static ScoredCommentCatalog.Base getAutoMode(Map<String, Object> exerciseGrade){
        return readMode(exerciseGrade, KEY_AUTO_MODE);
    }
    private static ScoredCommentCatalog.Base readMode(Map<String, Object> exerciseGrade, String key){
        Object mode = exerciseGrade == null ? null : exerciseGrade.get(key);
        if(mode == null) return null;
        try{
            return ScoredCommentCatalog.Base.valueOf(String.valueOf(mode));
        }catch(IllegalArgumentException e){
            return null;
        }
    }
    private static Leaf toLeaf(Map<String, Object> grade, HashMap<String, List<Double>> pointsByGrade, Set<String> ticked){
        String path = getGradePath(grade);
        return new Leaf(path, getDouble(grade, "total", 0), getDouble(grade, "value", -1),
                VALUE_SOURCE_COMMENTS.equals(grade.get(KEY_VALUE_SOURCE)), getDouble(grade, KEY_VALUE_BEFORE, -1),
                getDouble(grade, KEY_START, -1), pointsByGrade.getOrDefault(path, List.of()), ticked.contains(path), false);
    }
    // The leaves of an edit file, by exercise path
    private static LinkedHashMap<String, List<Map<String, Object>>> getLeavesByExercise(List<Map<String, Object>> grades,
                                                                                   Map<String, List<Map<String, Object>>> childrenByPath){
        LinkedHashMap<String, List<Map<String, Object>>> leaves = new LinkedHashMap<>();
        for(Map<String, Object> grade : grades){
            String path = getGradePath(grade);
            if(childrenByPath.containsKey(path) || getTier(getString(grade, "parentPath")) < 1) continue;
            leaves.computeIfAbsent(getExercisePath(path), k -> new ArrayList<>()).add(grade);
        }
        return leaves;
    }

    /**
     * A copy graded with a previous version has no automatic mode kept for its exercises: they count by mistakes (as
     * these versions did by default), and each sub-grade computed from its comments whose saved value is not the one
     * computed now gets the start that gives it (KEY_START: the maximum, 0 or the typed value), so that its grade does
     * not change while no mode is chosen. To call on an edit file just read, before anything changes. Returns the paths
     * kept.
     */
    public static List<String> keepStoredValues(Map<String, Object> editBase, ScoredCommentCatalog catalog){
        HashMap<String, List<Double>> pointsByGrade = getPointsByGrade(editBase);
        Set<String> ticked = getTickedGrades(editBase);
        List<Map<String, Object>> grades = getGrades(editBase);
        HashMap<String, List<Map<String, Object>>> childrenByPath = new HashMap<>();
        Map<String, Map<String, Object>> byPath = new HashMap<>();
        for(Map<String, Object> grade : grades){
            childrenByPath.computeIfAbsent(getString(grade, "parentPath"), k -> new ArrayList<>()).add(grade);
            byPath.put(getGradePath(grade), grade);
        }
        ArrayList<String> kept = new ArrayList<>();
        getLeavesByExercise(grades, childrenByPath).forEach((exercise, exerciseLeaves) -> {
            Map<String, Object> exerciseGrade = byPath.get(exercise);
            if(exerciseGrade == null || getAutoMode(exerciseGrade) != null) return;
            List<Leaf> leaves = exerciseLeaves.stream().map(grade -> toLeaf(grade, pointsByGrade, ticked)).toList();
            if(leaves.stream().noneMatch(leaf -> leaf.computed() && !leaf.points().isEmpty())) return;
            exerciseGrade.put(KEY_AUTO_MODE, ScoredCommentCatalog.Base.FULL.name());
            ScoredCommentCatalog.Base copyMode = getCopyMode(exerciseGrade);
            for(int i = 0; i < leaves.size(); i++){
                Leaf leaf = leaves.get(i);
                if(!leaf.computed() || leaf.points().isEmpty() || leaf.kept() >= 0) continue;
                if(getChosenMode(copyMode, catalog, leaf.path()) != null) continue;
                double stored = leaf.value();
                if(Math.abs(computeLeafFrom(leaf.total(), getStart(leaf, ScoredCommentCatalog.Base.FULL, false), leaf.points()) - stored) < 1e-9) continue;
                for(double candidate : new double[]{leaf.total(), 0, leaf.typed()}){
                    if(candidate >= 0 && Math.abs(computeLeafFrom(leaf.total(), candidate, leaf.points()) - stored) < 1e-9){
                        exerciseLeaves.get(i).put(KEY_START, candidate);
                        kept.add(leaf.path());
                        break;
                    }
                }
            }
        });
        return kept;
    }

    /**
     * Sets the value of the sub-grades computed from comments (see computeExercise), then the sums of their parents,
     * following the same rules as GradeTreeItem.makeSum.
     * @param isBonus tells if a grade name is a bonus grade (bonus grades are not counted in the totals).
     */
    public static void recomputeGrades(Map<String, Object> editBase, ScoredCommentCatalog catalog, Predicate<String> isBonus){
        HashMap<String, List<Double>> pointsByGrade = getPointsByGrade(editBase);
        Set<String> ticked = getTickedGrades(editBase);

        List<Map<String, Object>> grades = getGrades(editBase);
        HashMap<String, List<Map<String, Object>>> childrenByPath = new HashMap<>();
        Map<String, Map<String, Object>> byPath = new HashMap<>();
        for(Map<String, Object> grade : grades){
            childrenByPath.computeIfAbsent(getString(grade, "parentPath"), k -> new ArrayList<>()).add(grade);
            byPath.put(getGradePath(grade), grade);
        }

        // Leaves, exercise by exercise
        getLeavesByExercise(grades, childrenByPath).forEach((exercise, exerciseLeaves) -> {
            Map<String, Object> exerciseGrade = byPath.get(exercise);
            ScoredCommentCatalog.Base copyMode = getCopyMode(exerciseGrade);
            ExerciseValues result = computeExercise(exerciseLeaves.stream().map(grade -> toLeaf(grade, pointsByGrade, ticked)).toList(),
                    path -> getChosenMode(copyMode, catalog, path), getAutoMode(exerciseGrade));
            List<LeafValue> values = result.values();
            if(exerciseGrade != null){
                if(result.auto() == null) exerciseGrade.remove(KEY_AUTO_MODE);
                else exerciseGrade.put(KEY_AUTO_MODE, result.auto().name());
            }
            for(int i = 0; i < exerciseLeaves.size(); i++){
                Map<String, Object> grade = exerciseLeaves.get(i);
                LeafValue value = values.get(i);
                boolean computed = VALUE_SOURCE_COMMENTS.equals(grade.get(KEY_VALUE_SOURCE));
                if(value.computed()){
                    if(!computed){
                        grade.put(KEY_VALUE_BEFORE, getDouble(grade, "value", -1));
                        grade.put(KEY_VALUE_SOURCE, VALUE_SOURCE_COMMENTS);
                    }
                    grade.put("value", value.value());
                }else if(computed){
                    grade.put("value", value.value());
                    grade.remove(KEY_VALUE_SOURCE);
                    grade.remove(KEY_VALUE_BEFORE);
                    grade.remove(KEY_START);
                }
            }
        });

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

/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades.tags;

import java.util.*;

/**
 * Methods used by the students and mistakes they made in an evaluation, and where they were found: on which copy,
 * in which exercise, for which sub-grade and where on the copy. A tag is created for an exercise but can be used in
 * any exercise, and several times on a copy (each use is an occurrence: the same mistake twice costs points twice).
 * A tag may have points (a method adds them, a mistake removes them) and a comment, written on the copy for each
 * occurrence (see TagScoring). Does not depend on JavaFX.
 */
public class EvaluationTags {

    public enum Kind {
        METHOD, MISTAKE;
        static Kind fromYAML(Object value){
            return "MISTAKE".equals(value) ? MISTAKE : METHOD;
        }
    }

    public static final class Tag {
        private final String id;
        private final String exercise; // Exercise it was created for
        private String name;
        private Kind kind;
        private Double points; // Positive, or null: no points. The sign is given by the kind.
        private String comment; // Written on the copy, or null
        // Points by sub-grade (grade path -> positive points): an occurrence in their exercise counts on each of them,
        // instead of on the sub-grade it was put for with the points above
        private final LinkedHashMap<String, Double> gradePoints = new LinkedHashMap<>();

        Tag(String id, String exercise, String name, Kind kind){
            this.id = id;
            this.exercise = exercise;
            this.name = name;
            this.kind = kind;
        }
        public String getId(){
            return id;
        }
        public String getExercise(){
            return exercise;
        }
        public String getName(){
            return name;
        }
        public Kind getKind(){
            return kind;
        }
        public Double getPoints(){
            return points;
        }
        // Points added (method) or removed (mistake, negative) by each occurrence, or null.
        public Double getSignedPoints(){
            if(points == null) return null;
            return kind == Kind.MISTAKE ? -points : points;
        }
        public String getComment(){
            return comment;
        }
        public boolean hasPoints(){
            return points != null && points != 0;
        }
        public Map<String, Double> getGradePoints(){
            return Collections.unmodifiableMap(gradePoints);
        }
        // Points by sub-grade that apply to this exercise
        public LinkedHashMap<String, Double> getGradePoints(String exercise){
            LinkedHashMap<String, Double> points = new LinkedHashMap<>();
            gradePoints.forEach((path, value) -> {
                if(exercise.equals(exerciseOf(path))) points.put(path, value);
            });
            return points;
        }
        // Something is written on the copy for each occurrence (points or a comment).
        public boolean isWrittenOnCopy(){
            return hasPoints() || comment != null || !gradePoints.isEmpty();
        }
        // Points of an occurrence in this exercise, as shown next to the name: "−1", "+2" (by sub-grade: their sum), or ""
        public String getPointsLabel(String exercise, java.text.NumberFormat format){
            Map<String, Double> byGrade = getGradePoints(exercise);
            if(!byGrade.isEmpty()){
                double total = byGrade.values().stream().mapToDouble(Double::doubleValue).sum();
                return corrigo.panel.sidebar.grades.scoredcomments.ScoredCommentGrades.formatPoints(sign(total), format)
                        + (byGrade.size() > 1 ? " (" + byGrade.size() + ")" : "");
            }
            if(!hasPoints()) return "";
            return corrigo.panel.sidebar.grades.scoredcomments.ScoredCommentGrades.formatPoints(getSignedPoints(), format);
        }
        private double sign(double points){
            return kind == Kind.MISTAKE ? -points : points;
        }
    }

    // Where a tag was put on a copy: page index and position (grid units), and where its pill was moved to on the page
    // (top left corner, grid units; NaN: automatic, in the margin)
    public record Placement(int page, double x, double y, double labelX, double labelY) {
        public Placement(int page, double x, double y){
            this(page, x, y, Double.NaN, Double.NaN);
        }
        public boolean hasLabelPosition(){
            return !Double.isNaN(labelX) && !Double.isNaN(labelY);
        }
        public Placement withSpot(double x, double y){
            return new Placement(page, x, y, labelX, labelY);
        }
        public Placement withLabel(double labelX, double labelY){
            return new Placement(page, x, y, labelX, labelY);
        }
        // Top of the page of an exercise: a tag added from the panel, not at a spot of the copy
        public static Placement exercise(int page){
            return new Placement(page, EXERCISE_SPOT_X, EXERCISE_SPOT_Y);
        }
        public boolean isExerciseSpot(){
            return x == EXERCISE_SPOT_X && y == EXERCISE_SPOT_Y;
        }
        // Same page, and close (2 % of the width, 1.5 % of the height)
        public boolean isNear(Placement other){
            return page == other.page && Math.abs(x - other.x) < GRID_WIDTH * .02 && Math.abs(y - other.y) < GRID_HEIGHT * .015;
        }
    }
    // Grid size of a page (Element.GRID_WIDTH / GRID_HEIGHT)
    private static final double GRID_WIDTH = 165400, GRID_HEIGHT = 233900;
    private static final double EXERCISE_SPOT_X = GRID_WIDTH * 0.1, EXERCISE_SPOT_Y = GRID_HEIGHT * 0.12;
    /**
     * An occurrence of a tag on a copy: its id, the exercise it was put for, the sub-grade its points count on
     * (grade path, may be empty), and where it is.
     */
    public record Use(String id, String copy, String exercise, String tag, String grade, Placement placement) {
        Use with(Placement placement){
            return new Use(id, copy, exercise, tag, grade, placement);
        }
        Use with(String tag){
            return new Use(id, copy, exercise, tag, grade, placement);
        }
    }

    private final LinkedHashMap<String, Tag> tags = new LinkedHashMap<>();
    // Copy file name -> use id -> use, in the order they were added
    private final TreeMap<String, LinkedHashMap<String, Use>> copies = new TreeMap<>();

    public boolean isEmpty(){
        return tags.isEmpty();
    }

    // A name typed with its points: "Sign error -1", "Nice method +0,5" (a sign is needed: "Question 3" is a name).
    // points: positive or null; negative: true if the sign is a minus, null without points.
    public record Typed(String name, Double points, Boolean negative) {}
    private static final java.util.regex.Pattern TYPED_POINTS = java.util.regex.Pattern.compile("^(.*?\\S)\\s+([+\\-−])\\s?(\\d+(?:[.,]\\d+)?)\\s*$");
    public static Typed parseTyped(String text){
        String clean = text.strip().replaceAll("\\s+", " ");
        java.util.regex.Matcher matcher = TYPED_POINTS.matcher(clean);
        if(!matcher.matches()) return new Typed(clean, null, null);
        double points = Double.parseDouble(matcher.group(3).replace(',', '.'));
        if(points == 0) return new Typed(matcher.group(1), null, null);
        return new Typed(matcher.group(1), points, !"+".equals(matcher.group(2)));
    }

    // TAGS

    // All the tags: those created for the exercise or already used in it first, then the others.
    // In each group, the methods then the mistakes, in their creation order.
    public List<Tag> getTags(String exercise){
        Set<String> used = usedIn(exercise);
        ArrayList<Tag> list = new ArrayList<>();
        for(boolean own : new boolean[]{true, false}){
            for(Kind kind : Kind.values()){
                for(Tag tag : tags.values()){
                    if(tag.kind == kind && (tag.exercise.equals(exercise) || used.contains(tag.id)) == own) list.add(tag);
                }
            }
        }
        return list;
    }
    // Tags created for the exercise or used in it.
    public List<Tag> getExerciseTags(String exercise){
        Set<String> used = usedIn(exercise);
        return getTags(exercise).stream().filter(tag -> tag.exercise.equals(exercise) || used.contains(tag.id)).toList();
    }
    private Set<String> usedIn(String exercise){
        HashSet<String> used = new HashSet<>();
        for(LinkedHashMap<String, Use> uses : copies.values()){
            for(Use use : uses.values()) if(use.exercise().equals(exercise)) used.add(use.tag());
        }
        return used;
    }
    // All the tags, the methods then the mistakes.
    public List<Tag> getAllTags(){
        ArrayList<Tag> list = new ArrayList<>();
        for(Kind kind : Kind.values()){
            for(Tag tag : tags.values()) if(tag.kind == kind) list.add(tag);
        }
        return list;
    }
    public Tag getTag(String id){
        return tags.get(id);
    }
    public Optional<Tag> find(String name){
        String key = normalize(name);
        return tags.values().stream().filter(tag -> normalize(tag.name).equals(key)).findFirst();
    }
    // The existing tag with this name (whatever its exercise), or a new one for this exercise.
    public Tag create(String exercise, String name, Kind kind){
        String clean = name.strip().replaceAll("\\s+", " ");
        if(clean.isEmpty()) throw new IllegalArgumentException("Empty tag name");
        Optional<Tag> existing = find(clean);
        if(existing.isPresent()) return existing.get();
        Tag tag = new Tag(UUID.randomUUID().toString(), exercise, clean, kind);
        tags.put(tag.id, tag);
        return tag;
    }
    // Returns false if another tag has this name.
    public boolean rename(Tag tag, String name){
        String clean = name.strip().replaceAll("\\s+", " ");
        if(clean.isEmpty()) return false;
        Optional<Tag> other = find(clean);
        if(other.isPresent() && other.get() != tag) return false;
        tag.name = clean;
        return true;
    }
    public void setKind(Tag tag, Kind kind){
        tag.kind = kind;
    }
    // points: positive (the sign is given by the kind), or null. comment: blank for none.
    public void setScoring(Tag tag, Double points, String comment){
        tag.points = points == null ? null : Math.abs(points);
        tag.comment = comment == null || comment.isBlank() ? null : comment.strip();
    }
    // Points by sub-grade for an exercise (replaces those of this exercise; empty: the occurrences count on their sub-grade)
    public void setGradePoints(Tag tag, String exercise, Map<String, Double> points){
        tag.gradePoints.keySet().removeIf(path -> exercise.equals(exerciseOf(path)));
        points.forEach((path, value) -> {
            if(value != null && value != 0) tag.gradePoints.put(path, Math.abs(value));
        });
    }

    // "\Total\Ex 2\a" -> "Ex 2" (see ExerciseLocator.getExercise)
    static String exerciseOf(String gradePath){
        if(gradePath == null) return null;
        String[] parts = gradePath.split(java.util.regex.Pattern.quote("\\"));
        return parts.length >= 3 ? parts[2] : null;
    }

    /**
     * What an occurrence writes on the copy, for each sub-grade it counts on: the path of the grade, the points (signed,
     * or null: a comment only) and the comment (on the first one only). Empty if nothing is written.
     */
    public record Target(String key, String grade, Double points, String comment) {}
    public List<Target> getTargets(Use use){
        Tag tag = tags.get(use.tag());
        if(tag == null || !tag.isWrittenOnCopy()) return List.of();
        LinkedHashMap<String, Double> byGrade = tag.getGradePoints(use.exercise());
        if(byGrade.isEmpty()){
            if(!tag.hasPoints() && tag.comment == null) return List.of(); // Points by sub-grade for other exercises only
            return List.of(new Target("", use.grade(), tag.getSignedPoints() == null || !tag.hasPoints() ? null : tag.getSignedPoints(), tag.comment));
        }
        ArrayList<Target> targets = new ArrayList<>();
        for(Map.Entry<String, Double> entry : byGrade.entrySet()){
            targets.add(new Target(targets.isEmpty() ? "" : entry.getKey(), entry.getKey(), tag.sign(entry.getValue()), targets.isEmpty() ? tag.comment : null));
        }
        return targets;
    }
    public void delete(Tag tag){
        tags.remove(tag.id);
        copies.values().forEach(uses -> uses.values().removeIf(use -> use.tag().equals(tag.id)));
        copies.values().removeIf(Map::isEmpty);
    }
    private static String normalize(String name){
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // OCCURRENCES

    // Adds an occurrence of the tag on the copy. grade: the sub-grade its points count on.
    public Use add(String copy, String exercise, Tag tag, String grade, Placement placement){
        return put(new Use(UUID.randomUUID().toString(), copy, exercise, tag.id, grade == null ? "" : grade, placement));
    }
    private Use put(Use use){
        copies.computeIfAbsent(use.copy(), k -> new LinkedHashMap<>()).put(use.id(), use);
        return use;
    }
    public Use getUse(String copy, String id){
        LinkedHashMap<String, Use> uses = copies.get(copy);
        return uses == null ? null : uses.get(id);
    }
    // Returns the removed occurrence, or null.
    public Use remove(String copy, String id){
        LinkedHashMap<String, Use> uses = copies.get(copy);
        if(uses == null) return null;
        Use removed = uses.remove(id);
        if(uses.isEmpty()) copies.remove(copy);
        return removed;
    }
    // Removes all the occurrences of the tag on the copy for this exercise. Returns them.
    public List<Use> removeAll(String copy, String exercise, Tag tag){
        List<Use> removed = getUses(copy, exercise, tag);
        removed.forEach(use -> remove(copy, use.id()));
        return removed;
    }
    // Sets the sub-grade of the occurrences that have none (made before the points existed). Returns true if some had none.
    public boolean fillGrades(String copy, java.util.function.Function<String, String> gradeOfExercise){
        boolean changed = false;
        for(Use use : getUses(copy)){
            if(!use.grade().isEmpty()) continue;
            String grade = gradeOfExercise.apply(use.exercise());
            if(grade == null || grade.isEmpty()) continue;
            put(new Use(use.id(), use.copy(), use.exercise(), use.tag(), grade, use.placement()));
            changed = true;
        }
        return changed;
    }

    // Puts back an occurrence (undo of a removal).
    public void restore(Use use){
        put(use);
    }
    public void move(String copy, String id, Placement placement){
        Use use = getUse(copy, id);
        if(use != null) put(use.with(placement));
    }
    // The occurrence becomes an occurrence of another tag, at the same place and for the same sub-grade.
    public void change(String copy, String id, Tag tag){
        Use use = getUse(copy, id);
        if(use != null) put(use.with(tag.id));
    }

    public boolean has(String copy, String exercise, Tag tag){
        return count(copy, exercise, tag) > 0;
    }
    public int count(String copy, String exercise, Tag tag){
        return getUses(copy, exercise, tag).size();
    }
    // Occurrences of the tag on the copy for this exercise, in the order they were added.
    public List<Use> getUses(String copy, String exercise, Tag tag){
        LinkedHashMap<String, Use> uses = copies.get(copy);
        if(uses == null) return new ArrayList<>();
        return new ArrayList<>(uses.values().stream().filter(use -> use.exercise().equals(exercise) && use.tag().equals(tag.id)).toList());
    }
    // All the occurrences on the copy.
    public List<Use> getUses(String copy){
        LinkedHashMap<String, Use> uses = copies.get(copy);
        return uses == null ? List.of() : List.copyOf(uses.values());
    }
    // All the occurrences of the tag, by copy name then in the order they were added.
    public List<Use> getUses(Tag tag){
        ArrayList<Use> list = new ArrayList<>();
        for(LinkedHashMap<String, Use> uses : copies.values()){
            for(Use use : uses.values()) if(use.tag().equals(tag.id)) list.add(use);
        }
        return list;
    }
    // Copies that have this tag in this exercise, in the order of their names.
    public List<String> getCopies(String exercise, Tag tag){
        return getUses(tag).stream().filter(use -> use.exercise().equals(exercise)).map(Use::copy).distinct().toList();
    }
    // Copies that have this tag in any exercise, in the order of their names.
    public List<String> getCopies(Tag tag){
        return getUses(tag).stream().map(Use::copy).distinct().toList();
    }
    // Number of copies having the tag in each exercise it is used in.
    public LinkedHashMap<String, Integer> countByExercise(Tag tag){
        LinkedHashMap<String, Set<String>> copiesByExercise = new LinkedHashMap<>();
        for(Use use : getUses(tag)) copiesByExercise.computeIfAbsent(use.exercise(), k -> new HashSet<>()).add(use.copy());
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        copiesByExercise.forEach((exercise, set) -> counts.put(exercise, set.size()));
        return counts;
    }
    // Copies among these that have no method for this exercise.
    public List<String> getWithoutMethod(String exercise, Collection<String> allCopies){
        List<Tag> methods = tags.values().stream().filter(tag -> tag.kind == Kind.METHOD).toList();
        return allCopies.stream().filter(copy -> methods.stream().noneMatch(method -> has(copy, exercise, method))).toList();
    }
    // Copies that do not exist any more (renamed or deleted) are forgotten. Returns true if some were.
    public boolean retainCopies(Set<String> existingCopies){
        return copies.keySet().retainAll(existingCopies);
    }

    // YAML

    public LinkedHashMap<String, Object> toYAML(){
        ArrayList<Object> tagsData = new ArrayList<>();
        for(Tag tag : tags.values()){
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("id", tag.id);
            data.put("exercise", tag.exercise);
            data.put("name", tag.name);
            data.put("kind", tag.kind.name());
            if(tag.points != null) data.put("points", tag.points);
            if(tag.comment != null) data.put("comment", tag.comment);
            if(!tag.gradePoints.isEmpty()) data.put("gradePoints", new LinkedHashMap<>(tag.gradePoints));
            tagsData.add(data);
        }
        LinkedHashMap<String, Object> copiesData = new LinkedHashMap<>();
        copies.forEach((copy, uses) -> {
            ArrayList<Object> list = new ArrayList<>();
            for(Use use : uses.values()){
                LinkedHashMap<String, Object> data = new LinkedHashMap<>();
                data.put("id", use.id());
                data.put("tag", use.tag());
                data.put("exercise", use.exercise());
                if(!use.grade().isEmpty()) data.put("grade", use.grade());
                data.put("page", use.placement().page());
                data.put("x", use.placement().x());
                data.put("y", use.placement().y());
                if(use.placement().hasLabelPosition()){
                    data.put("labelX", use.placement().labelX());
                    data.put("labelY", use.placement().labelY());
                }
                list.add(data);
            }
            copiesData.put(copy, list);
        });
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("tags", tagsData);
        data.put("copies", copiesData);
        return data;
    }

    public static EvaluationTags fromYAML(Map<?, ?> data){
        EvaluationTags result = new EvaluationTags();
        // Tag id -> tag, and exercise it was created for (tags used to be in one exercise only: two exercises could
        // have a tag with the same name. They are merged, the uses without exercise are for the exercise of their tag).
        HashMap<String, Tag> byId = new HashMap<>();
        HashMap<String, String> exercises = new HashMap<>();
        if(data.get("tags") instanceof List<?> list){
            for(Object item : list){
                if(!(item instanceof Map<?, ?> tag)) continue;
                if(!(tag.get("id") instanceof String id) || !(tag.get("exercise") instanceof String exercise)
                        || !(tag.get("name") instanceof String name) || name.isBlank()) continue;
                Tag existing = result.find(name).orElse(null);
                if(existing == null){
                    existing = new Tag(id, exercise, name, Kind.fromYAML(tag.get("kind")));
                    result.setScoring(existing, tag.get("points") instanceof Number points ? points.doubleValue() : null,
                            tag.get("comment") instanceof String comment ? comment : null);
                    if(tag.get("gradePoints") instanceof Map<?, ?> gradePoints){
                        for(Map.Entry<?, ?> entry : gradePoints.entrySet()){
                            if(entry.getValue() instanceof Number value && value.doubleValue() != 0)
                                existing.gradePoints.put(String.valueOf(entry.getKey()), Math.abs(value.doubleValue()));
                        }
                    }
                    result.tags.put(id, existing);
                }
                byId.put(id, existing);
                exercises.put(id, exercise);
            }
        }
        if(data.get("copies") instanceof Map<?, ?> copies){
            for(Map.Entry<?, ?> copy : copies.entrySet()){
                if(!(copy.getValue() instanceof List<?> list)) continue;
                for(Object item : list){
                    if(!(item instanceof Map<?, ?> use) || !(use.get("tag") instanceof String tagId)) continue;
                    Tag tag = byId.get(tagId);
                    if(tag == null) continue;
                    String exercise = use.get("exercise") instanceof String name ? name : exercises.get(tagId);
                    String id = use.get("id") instanceof String value && !value.isBlank() ? value : UUID.randomUUID().toString();
                    result.put(new Use(id, String.valueOf(copy.getKey()), exercise, tag.id,
                            use.get("grade") instanceof String grade ? grade : "",
                            new Placement((int) number(use.get("page")), number(use.get("x")), number(use.get("y")),
                                    use.get("labelX") instanceof Number labelX ? labelX.doubleValue() : Double.NaN,
                                    use.get("labelY") instanceof Number labelY ? labelY.doubleValue() : Double.NaN)));
                }
            }
        }
        return result;
    }
    private static double number(Object value){
        return value instanceof Number number ? number.doubleValue() : 0;
    }
}

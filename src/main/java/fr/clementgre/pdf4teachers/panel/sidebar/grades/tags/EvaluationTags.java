/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.tags;

import java.util.*;

/**
 * Methods used by the students and mistakes they made in an evaluation, and where they were found: on which copy,
 * in which exercise and where on the copy. A tag is created for an exercise but can be used in any exercise
 * (the same mistake in several exercises). Never written on the copies. Does not depend on JavaFX.
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
    }
    // A tag put on a copy for an exercise
    public record Use(String copy, String exercise, Placement placement) {}
    // Key of the tags of a copy
    private record Key(String exercise, String tag) {}

    private final LinkedHashMap<String, Tag> tags = new LinkedHashMap<>();
    // Copy file name -> (exercise, tag id) -> placement
    private final TreeMap<String, LinkedHashMap<Key, Placement>> copies = new TreeMap<>();

    public boolean isEmpty(){
        return tags.isEmpty();
    }

    // TAGS

    // All the tags: those created for the exercise or already used in it first, then the others.
    // In each group, the methods then the mistakes, in their creation order.
    public List<Tag> getTags(String exercise){
        ArrayList<Tag> list = new ArrayList<>();
        for(boolean own : new boolean[]{true, false}){
            for(Kind kind : Kind.values()){
                for(Tag tag : tags.values()) if(tag.kind == kind && isOf(tag, exercise) == own) list.add(tag);
            }
        }
        return list;
    }
    // Tags created for the exercise or used in it.
    public List<Tag> getExerciseTags(String exercise){
        return getTags(exercise).stream().filter(tag -> isOf(tag, exercise)).toList();
    }
    private boolean isOf(Tag tag, String exercise){
        return tag.exercise.equals(exercise) || !getCopies(exercise, tag).isEmpty();
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
    public void delete(Tag tag){
        tags.remove(tag.id);
        copies.values().forEach(placements -> placements.keySet().removeIf(key -> key.tag().equals(tag.id)));
        copies.values().removeIf(Map::isEmpty);
    }
    private static String normalize(String name){
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // COPIES

    public boolean has(String copy, String exercise, Tag tag){
        return getPlacement(copy, exercise, tag) != null;
    }
    public Placement getPlacement(String copy, String exercise, Tag tag){
        LinkedHashMap<Key, Placement> placements = copies.get(copy);
        return placements == null ? null : placements.get(new Key(exercise, tag.id));
    }
    public void add(String copy, String exercise, Tag tag, Placement placement){
        copies.computeIfAbsent(copy, k -> new LinkedHashMap<>()).put(new Key(exercise, tag.id), placement);
    }
    public void remove(String copy, String exercise, Tag tag){
        LinkedHashMap<Key, Placement> placements = copies.get(copy);
        if(placements == null) return;
        placements.remove(new Key(exercise, tag.id));
        if(placements.isEmpty()) copies.remove(copy);
    }
    // Adds the tag to the copy for this exercise, or removes it if it has it. Returns true if the copy has it now.
    public boolean toggle(String copy, String exercise, Tag tag, Placement placement){
        if(has(copy, exercise, tag)){
            remove(copy, exercise, tag);
            return false;
        }
        add(copy, exercise, tag, placement);
        return true;
    }

    // Uses of the tag, by copy name then in the order they were added.
    public List<Use> getUses(Tag tag){
        ArrayList<Use> uses = new ArrayList<>();
        copies.forEach((copy, placements) -> placements.forEach((key, placement) -> {
            if(key.tag().equals(tag.id)) uses.add(new Use(copy, key.exercise(), placement));
        }));
        return uses;
    }
    // Copies that have this tag in this exercise, in the order of their names.
    public List<String> getCopies(String exercise, Tag tag){
        Key key = new Key(exercise, tag.id);
        return copies.entrySet().stream().filter(entry -> entry.getValue().containsKey(key)).map(Map.Entry::getKey).toList();
    }
    // Copies that have this tag in any exercise, in the order of their names.
    public List<String> getCopies(Tag tag){
        return getUses(tag).stream().map(Use::copy).distinct().toList();
    }
    // Number of copies having the tag in each exercise it is used in.
    public LinkedHashMap<String, Integer> countByExercise(Tag tag){
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for(Use use : getUses(tag)) counts.merge(use.exercise(), 1, Integer::sum);
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
            tagsData.add(data);
        }
        LinkedHashMap<String, Object> copiesData = new LinkedHashMap<>();
        copies.forEach((copy, placements) -> {
            ArrayList<Object> list = new ArrayList<>();
            placements.forEach((key, placement) -> {
                LinkedHashMap<String, Object> data = new LinkedHashMap<>();
                data.put("tag", key.tag());
                data.put("exercise", key.exercise());
                data.put("page", placement.page());
                data.put("x", placement.x());
                data.put("y", placement.y());
                if(placement.hasLabelPosition()){
                    data.put("labelX", placement.labelX());
                    data.put("labelY", placement.labelY());
                }
                list.add(data);
            });
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
                    if(!(item instanceof Map<?, ?> placement) || !(placement.get("tag") instanceof String tagId)) continue;
                    Tag tag = byId.get(tagId);
                    if(tag == null) continue;
                    String exercise = placement.get("exercise") instanceof String name ? name : exercises.get(tagId);
                    result.add(String.valueOf(copy.getKey()), exercise, tag,
                            new Placement((int) number(placement.get("page")), number(placement.get("x")), number(placement.get("y")),
                                    placement.get("labelX") instanceof Number labelX ? labelX.doubleValue() : Double.NaN,
                                    placement.get("labelY") instanceof Number labelY ? labelY.doubleValue() : Double.NaN));
                }
            }
        }
        return result;
    }
    private static double number(Object value){
        return value instanceof Number number ? number.doubleValue() : 0;
    }
}

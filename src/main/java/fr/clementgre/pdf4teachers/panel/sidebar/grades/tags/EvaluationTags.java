/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.tags;

import java.util.*;

/**
 * Methods used by the students and mistakes they made, for each exercise of an evaluation,
 * and the copies they were found on (with where on the copy). Never written on the copies.
 * Does not depend on JavaFX.
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
        private final String exercise;
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

    // Where a tag was put on a copy: page index and position (grid units)
    public record Placement(int page, double x, double y) {}

    private final LinkedHashMap<String, Tag> tags = new LinkedHashMap<>();
    // Copy file name -> tag id -> placement
    private final TreeMap<String, LinkedHashMap<String, Placement>> copies = new TreeMap<>();

    public boolean isEmpty(){
        return tags.isEmpty();
    }

    // TAGS

    // Tags of an exercise: the methods, then the mistakes, in their creation order.
    public List<Tag> getTags(String exercise){
        ArrayList<Tag> list = new ArrayList<>();
        for(Kind kind : Kind.values()){
            for(Tag tag : tags.values()) if(tag.exercise.equals(exercise) && tag.kind == kind) list.add(tag);
        }
        return list;
    }
    public Tag getTag(String id){
        return tags.get(id);
    }
    public Optional<Tag> find(String exercise, String name){
        String key = normalize(name);
        return tags.values().stream().filter(tag -> tag.exercise.equals(exercise) && normalize(tag.name).equals(key)).findFirst();
    }
    // The existing tag with this name, or a new one.
    public Tag create(String exercise, String name, Kind kind){
        String clean = name.strip().replaceAll("\\s+", " ");
        if(clean.isEmpty()) throw new IllegalArgumentException("Empty tag name");
        Optional<Tag> existing = find(exercise, clean);
        if(existing.isPresent()) return existing.get();
        Tag tag = new Tag(UUID.randomUUID().toString(), exercise, clean, kind);
        tags.put(tag.id, tag);
        return tag;
    }
    // Returns false if another tag of the exercise has this name.
    public boolean rename(Tag tag, String name){
        String clean = name.strip().replaceAll("\\s+", " ");
        if(clean.isEmpty()) return false;
        Optional<Tag> other = find(tag.exercise, clean);
        if(other.isPresent() && other.get() != tag) return false;
        tag.name = clean;
        return true;
    }
    public void setKind(Tag tag, Kind kind){
        tag.kind = kind;
    }
    public void delete(Tag tag){
        tags.remove(tag.id);
        copies.values().forEach(placements -> placements.remove(tag.id));
        copies.values().removeIf(Map::isEmpty);
    }
    private static String normalize(String name){
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // COPIES

    public boolean has(String copy, Tag tag){
        return copies.getOrDefault(copy, new LinkedHashMap<>()).containsKey(tag.id);
    }
    public Placement getPlacement(String copy, Tag tag){
        return copies.getOrDefault(copy, new LinkedHashMap<>()).get(tag.id);
    }
    public void add(String copy, Tag tag, Placement placement){
        copies.computeIfAbsent(copy, k -> new LinkedHashMap<>()).put(tag.id, placement);
    }
    public void remove(String copy, Tag tag){
        LinkedHashMap<String, Placement> placements = copies.get(copy);
        if(placements == null) return;
        placements.remove(tag.id);
        if(placements.isEmpty()) copies.remove(copy);
    }
    // Adds the tag to the copy, or removes it if it has it. Returns true if the copy has it now.
    public boolean toggle(String copy, Tag tag, Placement placement){
        if(has(copy, tag)){
            remove(copy, tag);
            return false;
        }
        add(copy, tag, placement);
        return true;
    }

    // Copies that have this tag, in the order of their names.
    public List<String> getCopies(Tag tag){
        return copies.entrySet().stream().filter(entry -> entry.getValue().containsKey(tag.id)).map(Map.Entry::getKey).toList();
    }
    // Copies among these that have no method for this exercise.
    public List<String> getWithoutMethod(String exercise, Collection<String> allCopies){
        List<Tag> methods = getTags(exercise).stream().filter(tag -> tag.kind == Kind.METHOD).toList();
        return allCopies.stream().filter(copy -> methods.stream().noneMatch(method -> has(copy, method))).toList();
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
            placements.forEach((tagId, placement) -> {
                LinkedHashMap<String, Object> data = new LinkedHashMap<>();
                data.put("tag", tagId);
                data.put("page", placement.page());
                data.put("x", placement.x());
                data.put("y", placement.y());
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
        if(data.get("tags") instanceof List<?> list){
            for(Object item : list){
                if(!(item instanceof Map<?, ?> tag)) continue;
                if(!(tag.get("id") instanceof String id) || !(tag.get("exercise") instanceof String exercise)
                        || !(tag.get("name") instanceof String name) || name.isBlank()) continue;
                result.tags.put(id, new Tag(id, exercise, name, Kind.fromYAML(tag.get("kind"))));
            }
        }
        if(data.get("copies") instanceof Map<?, ?> copies){
            for(Map.Entry<?, ?> copy : copies.entrySet()){
                if(!(copy.getValue() instanceof List<?> list)) continue;
                for(Object item : list){
                    if(!(item instanceof Map<?, ?> placement) || !(placement.get("tag") instanceof String tagId)) continue;
                    Tag tag = result.tags.get(tagId);
                    if(tag == null) continue;
                    result.add(String.valueOf(copy.getKey()), tag,
                            new Placement((int) number(placement.get("page")), number(placement.get("x")), number(placement.get("y"))));
                }
            }
        }
        return result;
    }
    private static double number(Object value){
        return value instanceof Number number ? number.doubleValue() : 0;
    }
}

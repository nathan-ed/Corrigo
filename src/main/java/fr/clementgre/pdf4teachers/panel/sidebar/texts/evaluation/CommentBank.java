/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.texts.evaluation;

import java.util.*;

/**
 * Comments written on the copies of an evaluation, by exercise, with the copies that use them.
 * An entry is identified by its text and the exercise it was found in: the same comment written in two exercises is listed in both.
 * The teacher can move an entry to another exercise, or hide it; this is kept when the copies are scanned again.
 * Entries no longer used by any copy are kept, so that a comment can be reused after it was removed from all the copies.
 */
public class CommentBank {

    // Style of the last placement of a comment, used to write it again.
    public record Style(String font, double size, boolean bold, boolean italic, String color, double maxWidth) {}

    // A comment found on a copy, its page and position (grid units), and the exercise it was located in (null: general).
    public record Occurrence(String text, int page, double x, double y, String exercise, Style style) {}

    public static final class Entry {
        private final String text;
        private final String exercise; // Where it was found (null: general)
        private String movedTo; // Set by the teacher (null: not moved; "" : moved to general)
        private boolean hidden;
        private Style style;
        // Copies using it, and the page where it is written (the first one if written several times)
        private final TreeMap<String, Integer> copies = new TreeMap<>();
        private long created;

        Entry(String text, String exercise, Style style, long created){
            this.text = text;
            this.exercise = exercise;
            this.style = style;
            this.created = created;
        }
        public String getText(){
            return text;
        }
        public String getFoundExercise(){
            return exercise;
        }
        // Exercise the entry is listed in (null: general).
        public String getExercise(){
            if(movedTo != null) return movedTo.isEmpty() ? null : movedTo;
            return exercise;
        }
        public boolean isHidden(){
            return hidden;
        }
        public Style getStyle(){
            return style;
        }
        public Set<String> getCopies(){
            return Collections.unmodifiableSet(copies.keySet());
        }
        // Page index of the comment in this copy, or -1.
        public int getPage(String copy){
            return copies.getOrDefault(copy, -1);
        }
        public int getUses(){
            return copies.size();
        }
        public long getCreated(){
            return created;
        }
    }

    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    // The same comment written on one line or wrapped by hand is the same entry.
    static String key(String exercise, String text){
        return (exercise == null ? "" : exercise) + '\u0000' + normalize(text);
    }
    public static String normalize(String text){
        return text.strip().replaceAll("\\s+", " ");
    }

    public Collection<Entry> getEntries(){
        return Collections.unmodifiableCollection(entries.values());
    }
    public List<Entry> getVisibleEntries(){
        return entries.values().stream().filter(entry -> !entry.hidden).toList();
    }
    public boolean isEmpty(){
        return entries.isEmpty();
    }

    /**
     * Replaces what this copy contributes by its current comments.
     * @return true if the bank changed.
     */
    public boolean updateCopy(String copy, List<Occurrence> occurrences, long now){
        boolean changed = false;
        HashSet<String> found = new HashSet<>();
        // A comment written several times on a copy counts once, at its first page
        for(Occurrence occurrence : occurrences.stream().sorted(Comparator.comparingInt(Occurrence::page)).toList()){
            String text = occurrence.text().strip();
            if(text.isEmpty()) continue;
            String key = key(occurrence.exercise(), text);
            if(!found.add(key)) continue;
            Entry entry = entries.get(key);
            if(entry == null){
                entry = new Entry(text, occurrence.exercise(), occurrence.style(), now);
                entries.put(key, entry);
                changed = true;
            }else if(!Objects.equals(entry.style, occurrence.style()) && occurrence.style() != null){
                entry.style = occurrence.style();
                changed = true;
            }
            Integer previous = entry.copies.put(copy, occurrence.page());
            changed |= previous == null || previous != occurrence.page();
        }
        for(Map.Entry<String, Entry> entry : entries.entrySet()){
            if(!found.contains(entry.getKey())) changed |= entry.getValue().copies.remove(copy) != null;
        }
        return changed;
    }
    
    // Copies that do not exist any more (renamed or deleted) no longer count.
    public boolean retainCopies(Set<String> existingCopies){
        boolean changed = false;
        for(Entry entry : entries.values()) changed |= entry.copies.keySet().retainAll(existingCopies);
        return changed;
    }

    // @param exercise null for general.
    public void move(Entry entry, String exercise){
        entry.movedTo = exercise == null ? "" : exercise;
        if(Objects.equals(entry.movedTo.isEmpty() ? null : entry.movedTo, entry.exercise)) entry.movedTo = null;
    }
    public void hide(Entry entry){
        entry.hidden = true;
    }

    // YAML

    public ArrayList<Object> toYAML(){
        ArrayList<Object> list = new ArrayList<>();
        for(Entry entry : entries.values()){
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("text", entry.text);
            if(entry.exercise != null) data.put("exercise", entry.exercise);
            if(entry.movedTo != null) data.put("movedTo", entry.movedTo);
            if(entry.hidden) data.put("hidden", true);
            data.put("created", entry.created);
            if(entry.style != null){
                data.put("font", entry.style.font());
                data.put("size", entry.style.size());
                data.put("bold", entry.style.bold());
                data.put("italic", entry.style.italic());
                data.put("color", entry.style.color());
                data.put("maxWidth", entry.style.maxWidth());
            }
            data.put("copies", new ArrayList<>(entry.copies.keySet()));
            data.put("pages", new LinkedHashMap<>(entry.copies));
            list.add(data);
        }
        return list;
    }

    public static CommentBank fromYAML(List<Object> list){
        CommentBank bank = new CommentBank();
        for(Object item : list){
            if(!(item instanceof Map<?, ?> data)) continue;
            if(!(data.get("text") instanceof String text) || text.isBlank()) continue;
            String exercise = data.get("exercise") instanceof String value ? value : null;
            Style style = null;
            if(data.get("font") instanceof String font){
                style = new Style(font, number(data.get("size"), 14), Boolean.TRUE.equals(data.get("bold")), Boolean.TRUE.equals(data.get("italic")),
                        data.get("color") instanceof String color ? color : "0x000000ff", number(data.get("maxWidth"), 0));
            }
            Entry entry = new Entry(text, exercise, style, (long) number(data.get("created"), 0));
            if(data.get("movedTo") instanceof String movedTo) entry.movedTo = movedTo;
            entry.hidden = Boolean.TRUE.equals(data.get("hidden"));
            Map<?, ?> pages = data.get("pages") instanceof Map<?, ?> map ? map : Map.of();
            if(data.get("copies") instanceof List<?> copies){
                for(Object copy : copies){
                    if(copy != null) entry.copies.put(copy.toString(), (int) number(pages.get(copy.toString()), -1));
                }
            }
            // Saved before the texts differing only by spaces were merged
            Entry existing = bank.entries.get(key(exercise, text));
            if(existing != null) entry.copies.forEach(existing.copies::putIfAbsent);
            else bank.entries.put(key(exercise, text), entry);
        }
        return bank;
    }
    private static double number(Object value, double defaultValue){
        if(value instanceof Number number) return number.doubleValue();
        if(value != null){
            try{
                return Double.parseDouble(value.toString());
            }catch(NumberFormatException ignored){
            }
        }
        return defaultValue;
    }
}

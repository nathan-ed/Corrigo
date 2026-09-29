/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.texts.evaluation;

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

    // A comment found on a copy, its page and position (grid units), the exercise it was located in (null: general),
    // and the grade it was written for in the grading panel (its path, or null).
    public record Occurrence(String text, int page, double x, double y, String exercise, Style style, String field) {
        public Occurrence(String text, int page, double x, double y, String exercise, Style style){
            this(text, page, x, y, exercise, style, null);
        }
    }

    public static final class Entry {
        private final String text;
        private final String exercise; // Where it was found (null: general)
        private String movedTo; // Set by the teacher (null: not moved; "" : moved to general)
        private boolean hidden;
        private Style style;
        // Copies using it, and the page where it is written (the first one if written several times)
        private final TreeMap<String, Integer> copies = new TreeMap<>();
        // Copies where it was written for a grade in the grading panel, and the path of the grade
        private final TreeMap<String, String> fields = new TreeMap<>();
        private String lastField; // Grade it was last written for (kept when no copy uses it any more)
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
        // Grade it was written for on this copy, or null
        public String getField(String copy){
            return fields.get(copy);
        }
        // Number of copies where it was written for this grade (at least 1 if it was, even if no copy uses it any more).
        public int getUsesInField(String field){
            if(field == null) return 0;
            int uses = (int) fields.values().stream().filter(field::equals).count();
            return uses == 0 && field.equals(lastField) ? 1 : uses;
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
            String field = occurrence.field() == null || occurrence.field().isEmpty() ? null : occurrence.field();
            if(field != null){
                changed |= !field.equals(entry.fields.put(copy, field)) || !field.equals(entry.lastField);
                entry.lastField = field;
            }else changed |= entry.fields.remove(copy) != null;
        }
        Set<String> fieldsOfCopy = new HashSet<>();
        for(Occurrence occurrence : occurrences) if(occurrence.field() != null) fieldsOfCopy.add(occurrence.field());
        for(Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator(); iterator.hasNext(); ){
            Map.Entry<String, Entry> item = iterator.next();
            if(found.contains(item.getKey())) continue;
            Entry entry = item.getValue();
            if(entry.copies.remove(copy) == null) continue;
            changed = true;
            String field = entry.fields.remove(copy);
            // The comment of a grade was edited (it is written while typed): its previous text was only on this copy, forget it
            if(entry.copies.isEmpty() && field != null && fieldsOfCopy.contains(field) && !entry.hidden && entry.movedTo == null) iterator.remove();
        }
        return changed;
    }
    
    // Copies that do not exist any more (renamed or deleted) no longer count.
    public boolean retainCopies(Set<String> existingCopies){
        boolean changed = false;
        for(Entry entry : entries.values()){
            changed |= entry.copies.keySet().retainAll(existingCopies);
            entry.fields.keySet().retainAll(existingCopies);
        }
        return changed;
    }

    // SUGGESTIONS

    /**
     * Comments to suggest in the field of a grade: those written for this grade first, then those of its exercise,
     * then all the others; the most used first. Hidden ones are not suggested, and a text is suggested once.
     * @param field Path of the grade (null: none). @param exercise Exercise of the grade (null: general).
     * @param query Typed text: every word must be in the comment (case and accents ignored). The comment equal to it is not suggested.
     */
    public List<Entry> suggest(String field, String exercise, String query, int limit){
        String[] words = simplify(query).split(" ");
        String typed = simplify(query);
        Comparator<Entry> order = Comparator.<Entry>comparingInt(entry -> getRank(entry, field, exercise))
                .thenComparing(Comparator.<Entry>comparingInt(entry -> entry.getUsesInField(field)).reversed())
                .thenComparing(Comparator.comparingInt(Entry::getUses).reversed())
                .thenComparing(Comparator.comparingLong(Entry::getCreated).reversed());
        LinkedHashMap<String, Entry> byText = new LinkedHashMap<>();
        entries.values().stream()
                .filter(entry -> !entry.hidden)
                .filter(entry -> {
                    String text = simplify(entry.text);
                    return !text.equals(typed) && Arrays.stream(words).allMatch(text::contains);
                })
                .sorted(order)
                .forEach(entry -> byText.putIfAbsent(simplify(entry.text), entry));
        return byText.values().stream().limit(limit).toList();
    }
    // 0: written for this grade, 1: in its exercise, 2: elsewhere
    public static int getRank(Entry entry, String field, String exercise){
        if(entry.getUsesInField(field) > 0) return 0;
        return Objects.equals(entry.getExercise(), exercise) ? 1 : 2;
    }
    // Lower case, no accents, single spaces
    static String simplify(String text){
        String decomposed = java.text.Normalizer.normalize(normalize(text), java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
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
            if(!entry.fields.isEmpty()) data.put("fields", new LinkedHashMap<>(entry.fields));
            if(entry.lastField != null) data.put("field", entry.lastField);
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
            if(data.get("field") instanceof String field) entry.lastField = field;
            if(data.get("fields") instanceof Map<?, ?> fields){
                fields.forEach((copy, field) -> {
                    if(copy != null && field instanceof String path) entry.fields.put(copy.toString(), path);
                });
            }
            Map<?, ?> pages = data.get("pages") instanceof Map<?, ?> map ? map : Map.of();
            if(data.get("copies") instanceof List<?> copies){
                for(Object copy : copies){
                    if(copy != null) entry.copies.put(copy.toString(), (int) number(pages.get(copy.toString()), -1));
                }
            }
            // Saved before the texts differing only by spaces were merged
            Entry existing = bank.entries.get(key(exercise, text));
            if(existing != null){
                entry.copies.forEach(existing.copies::putIfAbsent);
                entry.fields.forEach(existing.fields::putIfAbsent);
                if(existing.lastField == null) existing.lastField = entry.lastField;
            }
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

/*
 * Copyright (c) 2026. Clément Grennerat
 * All rights reserved. You must refer to the licence Apache 2.
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.export;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Feedback files for a Moodle assignment ("Upload multiple feedback files in a zip").
 * Moodle finds the student of a file from the participant id in its name: "<name>_<participant id>_assignsubmission_file_<file name>"
 * (see mod/assign/feedback/file/importziplib.php). The participant ids are read from the grading worksheet of the assignment,
 * and the copies are linked to them by email, through a list of students (name → email) made by the teacher.
 * Does not depend on JavaFX.
 */
public class MoodleFeedback {

    // "Participant 900110" (or "Participant900110" when copied from the web page), in any language
    private static final Pattern PARTICIPANT = Pattern.compile("(?i)(?:participant|teilnehmer|partecipante)[\\p{L}/]*\\s*(\\d+)");
    private static final Pattern EMAIL = Pattern.compile("[^\\s,;\"'<>()\\[\\]]+@[^\\s,;\"'<>()\\[\\]]+\\.[^\\s,;\"'<>()\\[\\]]+");
    private static final Pattern FIELD_SEPARATOR = Pattern.compile("[,;\\t]");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\d+[\\s_.-]*");

    private MoodleFeedback(){
    }

    public record Student(List<String> names, String email) {}
    public record Participant(String id, String email, String fullName) {}

    public enum Problem { NOT_IN_STUDENTS, AMBIGUOUS, NOT_IN_WORKSHEET, SAME_STUDENT }

    /**
     * A copy and the Moodle participant it goes to.
     * @param student null if not found in the list of students.
     * @param participant null if the copy can't be sent (see problem).
     */
    public record Match(File copy, String key, Student student, Participant participant, Problem problem) {
        public boolean isOk(){
            return problem == null;
        }
    }

    // READING

    // Text of a CSV file: UTF-8 (with or without BOM), or Windows-1252 as saved by some spreadsheets.
    public static String readText(File file) throws IOException{
        byte[] bytes = Files.readAllBytes(file.toPath());
        String text;
        try{
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        }catch(CharacterCodingException e){
            text = new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    // Each line with an email is a student; its other fields are names. Lines without email (headers...) are ignored.
    public static List<Student> parseStudents(String text){
        ArrayList<Student> students = new ArrayList<>();
        for(String line : text.split("\\R")){
            Matcher email = EMAIL.matcher(line);
            if(!email.find()) continue;
            ArrayList<String> names = new ArrayList<>();
            for(String field : FIELD_SEPARATOR.split(line.replace(email.group(), ""))){
                String name = normalize(field);
                if(!name.isEmpty()) names.add(name);
            }
            students.add(new Student(names, email.group().toLowerCase(Locale.ROOT)));
        }
        return students;
    }

    // Each line with a participant id is a participant. The columns are not read by their name: the worksheet is in the language of Moodle.
    public static List<Participant> parseWorksheet(String text){
        ArrayList<Participant> participants = new ArrayList<>();
        for(String line : text.split("\\R")){
            Matcher id = PARTICIPANT.matcher(line);
            if(!id.find()) continue;
            Matcher email = EMAIL.matcher(line);
            boolean hasEmail = email.find(id.end());
            String address = hasEmail ? email.group().toLowerCase(Locale.ROOT) : null;
            // The full name is between the identifier and the email
            String between = line.substring(id.end(), hasEmail ? email.start() : line.length());
            String fullName = Arrays.stream(FIELD_SEPARATOR.split(between))
                    .map(field -> field.replace("\"", "").trim())
                    .filter(field -> !field.isEmpty())
                    .findFirst().orElse("");
            participants.add(new Participant(id.group(1), address, fullName));
        }
        return participants;
    }

    // MATCHING

    // Name of a copy in the list of students: "10_FELIX.pdf" → "FELIX".
    public static String getKey(File copy){
        String name = copy.getName().replaceFirst("(?i)\\.pdf$", "");
        return normalize(LEADING_NUMBER.matcher(name).replaceFirst(""));
    }

    private static String toAscii(String text){
        String noAccents = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.replaceAll("[^\\x20-\\x7E]", "");
    }
    
    // Upper case, without accents, underscores are spaces, single spaces.
    public static String normalize(String text){
        String noAccents = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.replace('_', ' ').replace("\"", "").trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /**
     * The student of a copy: the one having a name equal to its key, else a name containing its key as a word.
     * Empty list: not found; several: ambiguous.
     */
    public static List<Student> findStudents(String key, List<Student> students){
        List<Student> exact = students.stream().filter(s -> s.names().contains(key)).toList();
        if(!exact.isEmpty()) return exact;
        return students.stream()
                .filter(s -> s.names().stream().anyMatch(name -> Arrays.asList(name.split("[\\s-]+")).contains(key) || Arrays.asList(name.split(" ")).contains(key)))
                .toList();
    }

    public static List<Match> match(List<File> copies, List<Student> students, List<Participant> participants){
        HashMap<String, Participant> byEmail = new HashMap<>();
        for(Participant participant : participants){
            if(participant.email() != null) byEmail.putIfAbsent(participant.email(), participant);
        }
        ArrayList<Match> matches = new ArrayList<>();
        for(File copy : copies){
            String key = getKey(copy);
            List<Student> found = findStudents(key, students);
            if(found.isEmpty()) matches.add(new Match(copy, key, null, null, Problem.NOT_IN_STUDENTS));
            else if(found.size() > 1) matches.add(new Match(copy, key, null, null, Problem.AMBIGUOUS));
            else{
                Participant participant = byEmail.get(found.getFirst().email());
                if(participant == null) matches.add(new Match(copy, key, found.getFirst(), null, Problem.NOT_IN_WORKSHEET));
                else matches.add(new Match(copy, key, found.getFirst(), participant, null));
            }
        }
        // Two copies for the same participant: none is sent
        Map<String, Long> countById = new HashMap<>();
        matches.stream().filter(Match::isOk).forEach(m -> countById.merge(m.participant().id(), 1L, Long::sum));
        matches.replaceAll(m -> m.isOk() && countById.get(m.participant().id()) > 1
                ? new Match(m.copy(), m.key(), m.student(), m.participant(), Problem.SAME_STUDENT) : m);
        return matches;
    }

    // Participants of the worksheet without a copy (absent students...).
    public static List<Participant> getWithoutCopy(List<Match> matches, List<Participant> participants){
        Set<String> ids = new HashSet<>();
        matches.stream().filter(m -> m.participant() != null).forEach(m -> ids.add(m.participant().id()));
        return participants.stream().filter(p -> !ids.contains(p.id())).toList();
    }

    // ZIP

    /**
     * Name of the file in the zip. Moodle splits it on the first underscores: the name must not have any,
     * and the rest (the file name the student gets) may. Only ASCII characters: accents in zip names are often misread.
     */
    public static String getZipEntryName(String name, String participantId, String fileName){
        String cleanName = toAscii(name).replace('_', ' ').replaceAll("[/\\\\:*?\"<>|]", " ").trim().replaceAll("\\s+", " ");
        if(cleanName.isEmpty()) cleanName = "Participant";
        String cleanFileName = toAscii(fileName).replaceAll("[/\\\\]", "-");
        return cleanName + "_" + participantId + "_assignsubmission_file_" + cleanFileName;
    }
}

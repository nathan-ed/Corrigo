/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package corrigo.panel.sidebar.grades;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a scan of all the copies is cut: the students in the order of the scan, the same number of pages for each copy,
 * and the name of each copy file ("07_DUPONT.pdf"). No JavaFX here.
 */
public final class NewEvaluationPlan {

    // A copy: its file name and its pages in the scan (0-based, inclusive)
    public record Copy(String fileName, String student, int firstPage, int lastPage) {}

    public enum Problem { NO_STUDENT, NOT_ENOUGH_PAGES, EXTRA_PAGES, DUPLICATE_NAMES }

    private NewEvaluationPlan(){
    }

    // The names typed or pasted, one per line: empty lines and spaces around are ignored.
    // A line "12;Dupont" or "Dupont<TAB>Jean" keeps its first non-number column.
    public static List<String> parseStudents(String text){
        ArrayList<String> students = new ArrayList<>();
        for(String line : text.split("\\R")){
            String name = line.strip();
            if(name.isEmpty()) continue;
            String[] columns = name.split("[;\\t]");
            if(columns.length > 1){
                for(String column : columns){
                    String value = column.strip();
                    if(!value.isEmpty() && !value.matches("\\d+")){
                        name = value;
                        break;
                    }
                }
            }
            students.add(name);
        }
        return students;
    }

    // "Dupont Jean" → "07_DUPONT_JEAN.pdf" (number and upper case optional). Characters not allowed in file names are
    // removed, accents are kept.
    public static String fileName(String student, int index, int count, boolean numbered, boolean upperCase){
        String name = Normalizer.normalize(student.strip(), Normalizer.Form.NFC)
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .replaceAll("\\s+", "_");
        if(upperCase) name = name.toUpperCase(Locale.ROOT);
        if(name.isEmpty()) name = "copy";
        if(numbered){
            int digits = Math.max(2, String.valueOf(count).length());
            name = String.format(Locale.ROOT, "%0" + digits + "d", index + 1) + "_" + name;
        }
        return name + ".pdf";
    }

    // The pages of each copy: the scan in order, pagesPerCopy pages each.
    public static List<Copy> plan(List<String> students, int pagesPerCopy, boolean numbered, boolean upperCase){
        ArrayList<Copy> copies = new ArrayList<>();
        for(int i = 0; i < students.size(); i++){
            copies.add(new Copy(fileName(students.get(i), i, students.size(), numbered, upperCase), students.get(i),
                    i * pagesPerCopy, (i + 1) * pagesPerCopy - 1));
        }
        return copies;
    }

    // The number of pages of each copy that fits the scan best (0 if there is no student)
    public static int guessPagesPerCopy(int scanPages, int students){
        if(students <= 0) return 0;
        return Math.max(1, Math.round((float) scanPages / students));
    }

    // What prevents (NO_STUDENT, NOT_ENOUGH_PAGES, DUPLICATE_NAMES) or deserves a warning (EXTRA_PAGES), or null.
    public static Problem check(List<Copy> copies, int scanPages){
        if(copies.isEmpty()) return Problem.NO_STUDENT;
        if(copies.getLast().lastPage() >= scanPages) return Problem.NOT_ENOUGH_PAGES;
        if(copies.stream().map(copy -> copy.fileName().toLowerCase(Locale.ROOT)).distinct().count() != copies.size()) return Problem.DUPLICATE_NAMES;
        if(copies.getLast().lastPage() < scanPages - 1) return Problem.EXTRA_PAGES;
        return null;
    }

    public static boolean isBlocking(Problem problem){
        return problem != null && problem != Problem.EXTRA_PAGES;
    }
}

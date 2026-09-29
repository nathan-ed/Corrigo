/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of a fork of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers.panel.sidebar.grades.export;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoodleFeedbackTest {

    private static final String WORKSHEET_TABS = """
            Identifiant\tNom complet\tAdresse de courriel\tStatut\tNote\tNote maximale\tLa note peut être modifiée\tDernière modification (note)\tFeedback par commentaires
            Participant900110\tTEST TEST\ttest@hello.ch\t' -\t\t100,00\tOui\t-\t
            """;

    @Test
    void readsTheWorksheetPastedFromMoodle(){
        List<MoodleFeedback.Participant> participants = MoodleFeedback.parseWorksheet(WORKSHEET_TABS);
        assertEquals(List.of(new MoodleFeedback.Participant("900110", "test@hello.ch", "TEST TEST")), participants);

        // Tabs turned into spaces
        assertEquals(List.of(new MoodleFeedback.Participant("900110", "test@hello.ch", "TEST TEST")),
                MoodleFeedback.parseWorksheet(WORKSHEET_TABS.replace("\t", "    ")));
    }

    @Test
    void readsTheDownloadedWorksheet(){
        String csv = """
                "Identifiant","Nom complet","Adresse de courriel",Statut,Note
                "Participant 900110","Felix Dupont",Felix.Dupont@School.ch,"Aucun travail remis",
                "Participant 900111","Nora Muller",nora@school.ch,,
                """;
        assertEquals(List.of(
                new MoodleFeedback.Participant("900110", "felix.dupont@school.ch", "Felix Dupont"),
                new MoodleFeedback.Participant("900111", "nora@school.ch", "Nora Muller")), MoodleFeedback.parseWorksheet(csv));
    }

    @Test
    void readsTheStudents(){
        String csv = """
                nom;email
                FELIX;felix.dupont@school.ch
                Müller,Nora,NORA@school.ch
                Marie-Lou Favre\tas@school.ch
                """;
        List<MoodleFeedback.Student> students = MoodleFeedback.parseStudents(csv);
        assertEquals(3, students.size());
        assertEquals(List.of("FELIX"), students.get(0).names());
        assertEquals(List.of("MULLER", "NORA"), students.get(1).names());
        assertEquals("nora@school.ch", students.get(1).email());
        assertEquals(List.of("MARIE-LOU FAVRE"), students.get(2).names());
    }

    @Test
    void copyKeyIsTheNameWithoutNumber(){
        assertEquals("FELIX", MoodleFeedback.getKey(new File("10_FELIX.pdf")));
        assertEquals("MARIE-LOU", MoodleFeedback.getKey(new File("13_MARIE-LOU.pdf")));
        assertEquals("LOIC", MoodleFeedback.getKey(new File("3_Loïc.PDF")));
    }

    @Test
    void matchesCopiesToParticipantsByEmail(){
        List<MoodleFeedback.Student> students = MoodleFeedback.parseStudents("""
                FELIX;felix@school.ch
                Nora Muller;nora@school.ch
                Marie-Lou Favre;as@school.ch
                Rose Blanc;rose1@school.ch
                Rose Noir;rose2@school.ch
                Oscar;oscar@school.ch
                """);
        List<MoodleFeedback.Participant> participants = MoodleFeedback.parseWorksheet("""
                Participant 1,Felix Dupont,felix@school.ch
                Participant 2,Nora Muller,nora@school.ch
                Participant 3,Marie-Lou Favre,as@school.ch
                Participant 4,Absent Student,absent@school.ch
                """);
        List<File> copies = List.of(new File("10_FELIX.pdf"), new File("11_NORA.pdf"), new File("13_MARIE-LOU.pdf"),
                new File("14_ROSE.pdf"), new File("15_OSCAR.pdf"), new File("16_ZOE.pdf"));

        List<MoodleFeedback.Match> matches = MoodleFeedback.match(copies, students, participants);
        assertEquals("1", matches.get(0).participant().id());
        assertEquals("2", matches.get(1).participant().id()); // A word of the name
        assertEquals("3", matches.get(2).participant().id());
        assertEquals(MoodleFeedback.Problem.AMBIGUOUS, matches.get(3).problem());
        assertEquals(MoodleFeedback.Problem.NOT_IN_WORKSHEET, matches.get(4).problem());
        assertEquals(MoodleFeedback.Problem.NOT_IN_STUDENTS, matches.get(5).problem());

        assertEquals(List.of("4"), MoodleFeedback.getWithoutCopy(matches, participants).stream().map(MoodleFeedback.Participant::id).toList());
    }

    @Test
    void twoCopiesForTheSameStudentAreNotSent(){
        List<MoodleFeedback.Student> students = MoodleFeedback.parseStudents("Nora;nora@school.ch\nNora Muller;nora@school.ch");
        List<MoodleFeedback.Participant> participants = MoodleFeedback.parseWorksheet("Participant 2,Nora Muller,nora@school.ch");
        List<MoodleFeedback.Match> matches = MoodleFeedback.match(List.of(new File("1_NORA.pdf"), new File("2_NORA MULLER.pdf")), students, participants);
        assertTrue(matches.stream().allMatch(m -> m.problem() == MoodleFeedback.Problem.SAME_STUDENT));
    }

    @Test
    void zipEntryNameFollowsMoodleFormat(){
        assertEquals("Felix Dupont_900110_assignsubmission_file_10_FELIX.pdf",
                MoodleFeedback.getZipEntryName("Felix Dupont", "900110", "10_FELIX.pdf"));
        // No underscore before the id, Moodle splits on them
        assertEquals("Jean Marc_900110_assignsubmission_file_a.pdf", MoodleFeedback.getZipEntryName("Jean_Marc", "900110", "a.pdf"));
        assertEquals("Participant_900110_assignsubmission_file_a.pdf", MoodleFeedback.getZipEntryName("", "900110", "a.pdf"));
        assertEquals("Lena Test_900110_assignsubmission_file_3_Loic.pdf", MoodleFeedback.getZipEntryName("Léna Test", "900110", "3_Loïc.pdf"));
    }
}

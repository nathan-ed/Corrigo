/*
 * Copyright (c) 2026 Nathan
 * Licensed under the Apache License, Version 2.0: see the LICENSE file.
 * Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
 */

package fr.clementgre.pdf4teachers;

/**
 * Where this version lives, and what it asks the network. This is a modified version of PDF4Teachers: it must not
 * send its users to the releases, the server or the support of PDF4Teachers.
 */
public final class AppLinks {

    // Name of this version, and its author (a modified PDF4Teachers)
    public static final String APP_NAME = "Corrigo";
    public static final String AUTHOR = "Nathan";

    // GitHub repository of this version (owner/name): releases, issues, user guide. GitHub redirects the old name
    // once the repository is renamed
    public static final String REPOSITORY = "nathan-ed/PDF4Teachers";
    public static final String REPOSITORY_URL = "https://github.com/" + REPOSITORY;
    public static final String ISSUES_URL = REPOSITORY_URL + "/issues";
    public static final String USER_GUIDE_URL = REPOSITORY_URL + "/tree/master/docs";
    public static final String RELEASES_URL = REPOSITORY_URL + "/releases";

    // Update check against the releases of REPOSITORY. Off until this version publishes its own releases:
    // the repository also has the tags of PDF4Teachers, which would be offered as updates.
    public static final boolean CHECK_UPDATES = false;

    // The server of PDF4Teachers (statistics, translation updates) is not used: its translations would replace
    // those of this version.
    public static final boolean USE_PDF4TEACHERS_SERVER = false;

    private AppLinks(){
    }
}

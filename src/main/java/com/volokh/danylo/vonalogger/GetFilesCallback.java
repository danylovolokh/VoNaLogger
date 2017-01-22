package com.volokh.danylo.vonalogger;

import java.io.File;

/**
 * Created by danylo.volokh on 1/20/17.
 *
 * This is the callback that should be used to get the list of files from {@link VoNaLogger}
 */
public interface GetFilesCallback {

    void onFilesReady(File[] logFiles);
}

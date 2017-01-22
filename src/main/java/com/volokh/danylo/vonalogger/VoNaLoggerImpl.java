package com.volokh.danylo.vonalogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by danylo.volokh on 12/25/16.
 * <p>
 * The main paradigm of this implementation is to reuse Log Entries {@link LogEntry} that were already passed to the VoNaLogger.
 * Why this approach?
 * <p>
 * On Java, logging can be quite harming performance wise. Because if we create a lot of objects they have to be
 * garbage collected eventually. And
 * <p>
 * If you use multiple instances of this class please give different names to the Log files.
 * <p>
 * You can pass the logFileName to {@link VoNaLoggerImpl} name but it will be broken down into multiple files in order to implement
 * circular writing to file, for example:
 * if logFileName is "Example_Log" the the actual files will be:
 * {@link #mLogFiles}
 * "Example_Log_1"
 * "Example_Log_2"
 * for reference see {@link #prepareLogFiles(File, String, int)}
 */
final class VoNaLoggerImpl implements VoNaLogger {

    private static final boolean DEBUG = true;

    /**
     * This is a number of entries in a single List of entries.
     * These list of entries are filled with logs and then passed for writing to file.
     * See {@link #mLoggingEntries} and {@link #mProcessingEntries}
     * <p>
     * Entries list is created in {@link #createCurrentListOfEntries()}
     *
     * Minimum 10 for "at least" some effectiveness of logging to the list until it filled.
     * If user specifies "1" then this might cause performance issues.
     */
    private static final int DEFAULT_ENTRIES_COUNT_IN_SINGLE_LIST = 10;

    private static final int LOG_FILES_COUNT = 3;

    private static final String LOG_FILE_SUFIX = ".log";

    /**
     * This is a non-static object and it will not sync every VonaLogger instance.
     * This means that for every different log file you need to have different VoNaLoggerImpl instance.
     */
    private final Object PROCESSING_SYNC_OBJECT = new Object();

    /**
     * This is a queue of Entries. Each Entry is a list of LogEntry-ies.
     * The entries that are here are currently processing by background thread.
     * Processing means writing to file.
     */
    private final Queue<List<LogEntry>> mProcessingEntries = new LinkedList<>();

    /**
     * Log entries that are in this queue are not yet passed for processing.
     */
    private final Queue<List<LogEntry>> mLoggingEntries = new LinkedList<>();

    /**
     * This is executor that is used to write logs into files in background thread.
     */
    private final Executor mBackgroundThread;

    private final AtomicBoolean mTerminated = new AtomicBoolean(true);

    private final Integer mEntriesCountInSingleList;

    private final long mFileSizeMax;

    private final File mLogDir;
    private final String mLogFileName;

    private FileWriter mWriter;

    private File[] mLogFiles;

    /**
     * This index is needed to track if we reached end of list.
     * When we reached end of list, it means that {@link #mCurrentLogEntryList} is full and
     * it needs to be send for processing.
     */
    private int mCurrentItemIndex;

    /**
     * This is so called "Current logging list".
     * This list is fetched from {@link #mLoggingEntries} and is used to put new LogEntry(es) there.
     * After this list is filled it is added to {@link #mProcessingEntries} for processing
     */
    private List<LogEntry> mCurrentLogEntryList;

    private final Runnable mProcessingRunnable = new Runnable() {
        @Override
        public void run() {

            while (!mTerminated.get()) {

                try {

                    List<LogEntry> listOfEntriesToProcess;

                    synchronized (PROCESSING_SYNC_OBJECT) {
                        if (mProcessingEntries.isEmpty()) {
                            try {

                                if (DEBUG) System.out.println("ProcessingRunnable, wait");
                                PROCESSING_SYNC_OBJECT.wait();

                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
//                            if (DEBUG) System.out.println("ProcessingRunnable, mProcessingEntries " + mProcessingEntries);

                            listOfEntriesToProcess = mProcessingEntries.poll();

                            writeEntriesToFile(listOfEntriesToProcess);
                            returnTheListForLogging(listOfEntriesToProcess);
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (DEBUG) System.out.println("ProcessingRunnable, mTerminated " + mTerminated);

            }

            performFlush();
        }
    };

    /**
     * Constructor that creates single thread executor for logging to file.
     */
    VoNaLoggerImpl(File logDir, String logFileName, long fileSizeMax, Integer entriesList) throws IOException {
        mBackgroundThread = Executors.newSingleThreadExecutor();
        mFileSizeMax = fileSizeMax / LOG_FILES_COUNT;

        mLogDir = logDir;
        mLogFileName = logFileName;

        if (entriesList != null) {
            mEntriesCountInSingleList = entriesList;
        } else {
            mEntriesCountInSingleList = DEFAULT_ENTRIES_COUNT_IN_SINGLE_LIST;
        }
        createCurrentListOfEntries();
        initializeVoNaLogger(mLogDir, mLogFileName);
    }

    /**
     * @param logDir      - directory where log files will be stored.
     * @param logFileName - this is basis for log files:
     *                    If "logFileName" is "Example_log"
     *                    then log files names will be:
     *                    "Example_log_0"
     *                    "Example_log_1"
     *                    "Example_log_2"
     *                    etc..
     * @throws IOException
     */
    private void initializeVoNaLogger(File logDir, String logFileName) throws IOException {
        if (DEBUG) System.out.println(">> initializeVoNaLogger, mTerminated " + mTerminated);
        if (mTerminated.get()) {

            mTerminated.set(false);

            prepareLogFiles(logDir, logFileName, LOG_FILES_COUNT);
            createFileWriter();
            initializeBackgroundThreadLogger();
        } else {
            throw new IllegalStateException("VoNaLogger is not terminated. Please call stopLoggingAndGetLogFiles before calling this method");
        }

        if (DEBUG) System.out.println("<< initializeVoNaLogger");
    }

    private void writeEntriesToFile(List<LogEntry> listOfEntriesToProcess) throws IOException {
        if (DEBUG) System.out.println(">> writeEntriesToFile");

        // check if the current file is overfilled
        File current = currentFile();

        if (DEBUG) {
            System.out.println("writeEntriesToFile, file length " + current.length());
            System.out.println("writeEntriesToFile, mFileSizeMax " + mFileSizeMax);
        }

        if (current.length() >= mFileSizeMax) {

            if (DEBUG)
                System.out.println("writeToFile, rotating, current " + current.length() + ", single " + mFileSizeMax);

            rotateFiles();
        }
        for (LogEntry logEntry : listOfEntriesToProcess) {

            if(logEntry.isEntryFilledWithData()){
                String text = logEntry.getMergedStringAndClean();
                mWriter.append(text);
                mWriter.append("\n");
            } else {
                if (DEBUG) System.out.println("writeEntriesToFile, found empty logEntry. Probably it wasn't filled yet.");
                //TODO: may I "break" here? Break if all the rest of LogEntries are empty. TODO: check
            }
        }
        mWriter.flush();
        if (DEBUG) System.out.println("<< writeEntriesToFile");
    }

    /**
     * We have multiple files in order to implement circular writing.
     * This method rotates them in this way.
     * <p>
     * 1. log1 _
     *          \
     *           -> log2
     * 2. log2 _
     *          \
     *           -> log3
     * 3. log3 _
     *          \
     *           -> removed
     * <p>
     *
     * 4. empty "log1" created. From this moment empty log1 is "current file".
     */
    private File rotateFiles() throws IOException {
        if (DEBUG) System.out.println("rotateFiles");

        mWriter.close();

        for (int i = LOG_FILES_COUNT - 1; i >= 1; --i) {
            rename(mLogFiles[i - 1], mLogFiles[i]);
        }
        File file = currentFile();
        createNewFile(file);

        mWriter = new FileWriter(file, false);

        return file;
    }

    private void performFlush() {
        if (DEBUG) System.out.println(">> performFlush");

        try {
            mWriter.close();
            mWriter = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (DEBUG) System.out.println("<< performFlush");
    }

    @Override
    public void stopLoggingAndGetLogFiles(final GetFilesCallback filesCallback) {
        if (DEBUG) System.out.println(">> stopLoggingAndGetLogFiles");

        if (filesCallback == null) {
            throw new IllegalArgumentException("filesCallback cannot be null");
        }

        /**
         * Post the runnable that delivers logging files after writing to file is finished.
         */
        mBackgroundThread.execute(new Runnable() {
            @Override
            public void run() {
                if (DEBUG) System.out.println("stopLoggingAndGetLogFiles >> run");
                filesCallback.onFilesReady(mLogFiles);
            }
        });

        mTerminated.set(true);

        synchronized (PROCESSING_SYNC_OBJECT) {
            PROCESSING_SYNC_OBJECT.notify();
        }
        if (DEBUG) System.out.println("<< stopLoggingAndGetLogFiles, mTerminated " + mTerminated);
    }

    /**
     * May return "null" instead of files to the {@link GetFilesCallback#onFilesReady(File[])}
     * if an error occured.
     */
    @Override
    public void getLoggingFilesSnapShot(final GetFilesCallback filesCallback) {
        if (DEBUG) System.out.println(">> stopLoggingAndGetLogFiles");

        if (filesCallback == null) {
            throw new IllegalArgumentException("filesCallback cannot be null");
        }

        /**
         * Post the runnable that delivers logging files after writing to file is finished.
         */
        mBackgroundThread.execute(new Runnable() {
            @Override
            public void run() {
                if (DEBUG) System.out.println("stopLoggingAndGetLogFiles >> run");
                File[] logFiles;
                try {
                    logFiles = createLogFilesSnapshot();
                    prepareLogFiles(mLogDir, mLogFileName, LOG_FILES_COUNT);

                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }

                filesCallback.onFilesReady(logFiles);
            }
        });

        mTerminated.set(true);

        synchronized (PROCESSING_SYNC_OBJECT) {
            PROCESSING_SYNC_OBJECT.notify();
        }
        if (DEBUG) System.out.println("<< stopLoggingAndGetLogFiles");
    }


    /**
     * If file name is "VonaLogger_1.log" the snapshot file will be "VonaLogger_1_snapshot.log"
     *
     */
    private File[] createLogFilesSnapshot() throws IOException {

        File[] logFilesSnapshot = new File[LOG_FILES_COUNT];
        for (int index = 0; index < mLogFiles.length; index++) {

            String fileName = mLogFiles[index].getName();
            String rawName = fileName.substring(0, fileName.length() - LOG_FILE_SUFIX.length());

            File snapShotLogFile = new File(rawName + "_snapshot" + LOG_FILE_SUFIX);

            createNewFile(snapShotLogFile);
            logFilesSnapshot[index] = snapShotLogFile;

            rename(mLogFiles[index], snapShotLogFile);
        }

        return logFilesSnapshot;
    }

    private void createFileWriter() throws IOException {
        File file = currentFile();
        mWriter = new FileWriter(file, true);
    }

    private void prepareLogFiles(File logDir, String name, int count) throws IOException {
        if (DEBUG)
            System.out.println("prepareLogFiles, name[" + name + "], count " + count + ", logDir " + logDir);

        createDirectoryIfNeeded(logDir);

        File[] result = new File[count];
        for (int i = 0; i < count; ++i) {
            String fileName = logsFileName(name, i);
            File file = new File(logDir, fileName);
            boolean exists = file.exists();

            if (DEBUG)
                System.out.println("prepareLogFiles, created " + exists + ", file " + fileName);

            if (!exists) {
                if(!file.createNewFile()){
                    throw new IOException("file, " + file + ", is not created");
                }
            }
            result[i] = file;
        }
        mLogFiles = result;
    }

    private String logsFileName(String name, int fileIndex) {
        return name + "_" + fileIndex + LOG_FILE_SUFIX;
    }

    private void createDirectoryIfNeeded(File dir) throws IOException {
        if (DEBUG) System.out.println("createDirectoryIfNeeded, logDir " + dir);

        if (dir.exists() && !dir.isDirectory()) {
            boolean deleted = dir.delete();

            if (DEBUG) System.out.println("createDirectoryIfNeeded, deleted " + deleted);
        }

        boolean exists = dir.exists();
        if (!exists) {
            exists = dir.mkdirs();
        }

        if (DEBUG) System.out.println("createDirectoryIfNeeded, exists " + exists);

        if (!exists) {
            throw new IOException("failed to create directory for logs");
        }
    }

    private void returnTheListForLogging(List<LogEntry> listOfEntriesToProcess) {
        mLoggingEntries.add(listOfEntriesToProcess);
    }

    private void createNewFile(File file) throws IOException {
        boolean exists = file.createNewFile();
        if (!exists) {
            throw new IOException("failed to create file " + file.getAbsolutePath());
        }
    }

    /**
     * This method copies all the content from "old" to file with name "newPath".
     * and removes the "old" file.
     * <p>
     * If "newPath" file exists it will be overridden.
     */
    private void rename(File old, File newPath) {
        if (DEBUG) {
            System.out.println("rename " + old.getAbsolutePath() + " to " + newPath.getAbsolutePath());
            System.out.println("rename >> old " + old + " newPath " + newPath);
        }

        boolean success = old.renameTo(newPath);

        if (DEBUG) {
            System.out.println("rename success " + success);
            System.out.println("rename << old " + old + " newPath " + newPath);
            System.out.println("after renaming");

            for (int index = 0; index < LOG_FILES_COUNT; index++) {
                System.out.println("log file " + mLogFiles[index]);
            }
        }
    }

    /**
     * This creates a list of Log Entries that are directly used when methods {@link #writeLog(Object...)} etc..
     * are called.
     */
    private void createCurrentListOfEntries() {
        if (DEBUG) System.out.println(">> createCurrentListOfEntries");
        mCurrentLogEntryList = new ArrayList<>(mEntriesCountInSingleList);

        for (int index = 0; index < mEntriesCountInSingleList; index++) {
            mCurrentLogEntryList.add(new LogEntry());
        }

        if (DEBUG) System.out.println("<< createCurrentListOfEntries");
    }

    private void initializeBackgroundThreadLogger() {
        mBackgroundThread.execute(mProcessingRunnable);
    }

    @Override
    public int writeLog(Object... parameters) {

        if(mTerminated.get()){
            return 0;
        }

        if (DEBUG) System.out.println(">> writeLog " + Arrays.toString(parameters));

        synchronized (PROCESSING_SYNC_OBJECT) {

            if (DEBUG) {
                System.out.println("    writeLog, mCurrentItemIndex " + mCurrentItemIndex);
                System.out.println("    writeLog, entries count " + ((long) (mProcessingEntries.size() + mLoggingEntries.size()) * (long) mEntriesCountInSingleList));
                System.out.println("    writeLog, mProcessingEntries count " + (long) (mProcessingEntries.size()));
                System.out.println("    writeLog, mLoggingEntries count " + (long) (mLoggingEntries.size()));
            }

            if (isCurrentEntryLogListFilled()) {

                /**
                 * Add current list to processing queue
                 */
                mProcessingEntries.add(mCurrentLogEntryList);

                /**
                 * notify background thread that {@link #mProcessingEntries} is not empty and it can
                 * be processed
                 */
                PROCESSING_SYNC_OBJECT.notify();

                if (DEBUG)
                    System.out.println("    writeLog, mLoggingEntries isEmpty " + mLoggingEntries.isEmpty());

                if (mLoggingEntries.isEmpty()) {

                    addNewLogTheEntriesListToTheLoggingQueue();

                }

                mCurrentLogEntryList = mLoggingEntries.poll();
                mCurrentItemIndex = 0;

            }

            /**
             * Get LogEntry from the list.
             * This has to be done synchronously because logger can be used from different Threads.
             */
            LogEntry logEntry = mCurrentLogEntryList.get(mCurrentItemIndex);
            logEntry.setLogParameters(parameters);

            /**
             * Increment index to track the position
             */
            mCurrentItemIndex++;
        }
        if (DEBUG) System.out.println("<< writeLog");
        return 0;
    }

    private boolean isCurrentEntryLogListFilled() {
        boolean isCurrentEntryLogListFilled = mCurrentItemIndex >= mEntriesCountInSingleList;
        if (DEBUG) {
            System.out.println("    isCurrentEntryLogListFilled, mCurrentItemIndex " + mCurrentItemIndex);
            System.out.println("    isCurrentEntryLogListFilled, " + isCurrentEntryLogListFilled);
        }
        return isCurrentEntryLogListFilled;
    }

    /**
     * This method should not be called to often.
     * It is called few times until number of entries will reach the value where no new list of entries is needed.
     */
    private void addNewLogTheEntriesListToTheLoggingQueue() {
        if (DEBUG) System.out.println(" >> addNewLogTheEntriesListToTheLoggingQueue");

        List<LogEntry> singleLogEntries = new ArrayList<>();
        for (int index = 0; index < mEntriesCountInSingleList; index++) {
            /**
             * Add an empty log entry. It will be fetched and filled with data later
             */
            singleLogEntries.add(new LogEntry());
        }
        mLoggingEntries.add(singleLogEntries);
        if (DEBUG) System.out.println(" >> addNewLogTheEntriesListToTheLoggingQueue");
    }

    /**
     * First file is always the current file.
     *
     * @return file ot index 0. It's always the current file.
     */
    private File currentFile() {
        return mLogFiles[0];
    }
}

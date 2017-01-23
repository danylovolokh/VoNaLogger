import com.volokh.danylo.vonalogger.GetFilesCallback;
import com.volokh.danylo.vonalogger.VoNaLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VoNaLoggerTest {

    private static final boolean SHOW_LOGS = false;

    private static final int TESTS_REPEAT_TIME = 10000;

    private VoNaLogger mVoNaLogger;

    private File mDirectory;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Before
    public void before(){
        if(SHOW_LOGS) if(SHOW_LOGS) System.out.println(">> before");

        mDirectory = createDirectoryIfNeeded("log_files_directory");
        clearDirectory(mDirectory);

        if(SHOW_LOGS) if(SHOW_LOGS) System.out.println("<< before");
    }

    @After
    public void after(){
        if(SHOW_LOGS) System.out.println(">> after");

        /**
         * This can be null for some tests. See:
         * {@link #testLoggerFileNameNotSpecified()}
         * {@link #testLoggerFilesDirectoryNotSpecified()}
         * {@link #testMaxFileSizeNotSpecified()}
         */
        if(mVoNaLogger != null){
            /**
             * have to release resources because we cannot create an infinite amount of
             * threads
             */
            mVoNaLogger.releaseResources();
        }
        clearDirectory(mDirectory);
        mDirectory.delete();

        if(SHOW_LOGS) System.out.println("<< after");
    }

    @Repeat(times = TESTS_REPEAT_TIME)
    @Test
    public void testMaxFileSizeNotExceeded() throws IOException, InterruptedException {

        /**
         * 10 bytes
         */
        int maxFileSize = 5;

        int minimumEntriesCount = 20;

        final int expectedMaxFileSize = Math.max(
                // "\n" sign is added after every entry that's why multiply by 2
                minimumEntriesCount * 2,
                maxFileSize);

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
                        .setLoggerFileName("VoNaLoggerFileName")
                        .setLoggerFilesDir(mDirectory)
                        .setMinimumEntriesCount(minimumEntriesCount)
                        .setLogFileMaxSize(maxFileSize)
                        .build();

        /**
         * Assuming a single char is taking at least 8 bits (1 byte)
         * After writing {@link minimumEntriesCount} + 1 amount of characters
         * There will be enough characters to write to file
         *
         */
        for(int charIndex = 0; charIndex < minimumEntriesCount; charIndex++){
            mVoNaLogger.writeLog('a');
            // + "/n" is added after every log entry
            // this means that actual amount of characters will be "maxFileSize * 2"
        }

        final AtomicBoolean lockObject = new AtomicBoolean();

        final AtomicLong totalFilesSizeInBytes = new AtomicLong(0);

        mVoNaLogger.processPendingLogsStopAndGetLogFiles(new GetFilesCallback() {
            @Override
            public void onFilesReady(File[] logFiles) {
                if(SHOW_LOGS) System.out.println("onFilesReady, logFiles " + Arrays.toString(logFiles));

                for(File logFile: logFiles){
                    if(SHOW_LOGS) System.out.println("onFilesReady, logFile.length() " + logFile.length());

                    totalFilesSizeInBytes.addAndGet(logFile.length());
                    showFileContent(logFile);
                }

                if(SHOW_LOGS) System.out.println("onFilesReady, totalFilesSizeInBytes " + totalFilesSizeInBytes);

                synchronized (lockObject) {
                    lockObject.set(true);
                    lockObject.notify();
                }

            }
        });

        synchronized (lockObject) {
            if(!lockObject.get()){
                lockObject.wait();
            }
        }

        assertEquals(expectedMaxFileSize, totalFilesSizeInBytes.intValue());
    }

    @Repeat(times = TESTS_REPEAT_TIME)
    @Test
    public void testAllEntriesProcessed() throws IOException, InterruptedException {

        int minimumEntriesCount = 20;

        final int expectedMaxFileSize = getMaxFileSize();

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
                        .setLoggerFileName("VoNaLoggerFileName")
                        .setLoggerFilesDir(mDirectory)
                        .setMinimumEntriesCount(minimumEntriesCount)
                        .setLogFileMaxSize(expectedMaxFileSize)
                        .build();

        /**
         * Assuming a single char is taking at least 8 bits (1 byte)
         * After writing {@link minimumEntriesCount} + 1 amount of characters
         * There will be enough characters to write to file
         *
         */
        for(int charIndex = 0; charIndex < minimumEntriesCount; charIndex++){
            assertEquals(mVoNaLogger.writeLog("First Log"), 1);

            // + "/n" is added after every log entry
            // this means that actual amount of characters will be "maxFileSize * 2"
        }

        final String concreteLog = "Concrete Log";
        mVoNaLogger.writeLog(concreteLog);

        final AtomicBoolean lockObject = new AtomicBoolean();

        final AtomicBoolean concreteLogFound = new AtomicBoolean();

        mVoNaLogger.processPendingLogsStopAndGetLogFiles(new GetFilesCallback() {
            @Override
            public void onFilesReady(File[] logFiles) {
                if(SHOW_LOGS) System.out.println("onFilesReady, logFiles " + Arrays.toString(logFiles));

                showFilesContent(logFiles);

                concreteLogFound.set(findSpecificLogInFiles(concreteLog, logFiles));

                if(SHOW_LOGS) System.out.println("onFilesReady, concreteLogFound " + concreteLogFound);

                synchronized (lockObject) {
                    lockObject.set(true);
                    lockObject.notify();
                }

            }
        });

        synchronized (lockObject) {
            if(!lockObject.get()){
                lockObject.wait();
            }
        }

        assertTrue(concreteLogFound.get());
    }

    @Repeat(times = TESTS_REPEAT_TIME)
    @Test(expected = IllegalArgumentException.class)
    public void testMaxFileSizeNotSpecified() throws IOException {

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
                        .setLoggerFileName("VoNaLoggerFileName")
//                        .setLogFileMaxSize(maxFileSize)
                        .setLoggerFilesDir(mDirectory)
                        .build();
    }

    @Repeat(times = TESTS_REPEAT_TIME)
    @Test(expected = IllegalArgumentException.class)
    public void testLoggerFileNameNotSpecified() throws IOException {

        long maxFileSize = getMaxFileSize();

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
//                        .setLoggerFileName("VoNaLoggerFileName")
                        .setLogFileMaxSize(maxFileSize)
                        .setLoggerFilesDir(mDirectory)
                        .build();
    }

    @Repeat(times = TESTS_REPEAT_TIME)
    @Test(expected = IllegalArgumentException.class)
    public void testLoggerFilesDirectoryNotSpecified() throws IOException {

        long maxFileSize = getMaxFileSize();

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
                        .setLoggerFileName("VoNaLoggerFileName")
                        .setLogFileMaxSize(maxFileSize)
//                        .setLoggerFilesDir(mDirectory)
                        .build();
    }

    @Repeat(times = TESTS_REPEAT_TIME)
    @Test
    public void testFilesCreated() throws IOException, InterruptedException {
        if(SHOW_LOGS) System.out.println(">> testFilesCreated");

        long maxFileSize = getMaxFileSize();


        if(SHOW_LOGS) System.out.println("testFilesCreated, mDirectory[" + mDirectory.getAbsolutePath() + "]");

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
                        .setLoggerFileName("VoNaLoggerFileName")
                        .setLoggerFilesDir(mDirectory)
                        .setLogFileMaxSize(maxFileSize)
                        .build();

        final AtomicBoolean filesEquals = new AtomicBoolean(false);

        File[] logFiles = mVoNaLogger.stopLoggingAndGetLogFilesSync();

        if(SHOW_LOGS) System.out.println("<< testFilesCreated, filesEquals " + filesEquals);

        assertTrue(Arrays.equals(logFiles, mDirectory.listFiles()));

    }

    @Repeat(times = TESTS_REPEAT_TIME)
    @Test
    public void testProcessPendingLogsSync() throws IOException, InterruptedException {
        if(SHOW_LOGS) System.out.println(">> testProcessPendingLogsSync");

        long maxFileSize = getMaxFileSize();

        if(SHOW_LOGS) System.out.println("testProcessPendingLogsSync, mDirectory[" + mDirectory.getAbsolutePath() + "]");

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
                        .setLoggerFileName("VoNaLoggerFileName")
                        .setLoggerFilesDir(mDirectory)
                        .setLogFileMaxSize(maxFileSize)
                        .build();

        String concreteLog = "ConcreteLog";
        mVoNaLogger.writeLog(concreteLog);

        File[] logFiles = mVoNaLogger.processPendingLogsStopAndGetLogFilesSync();

        showFilesContent(logFiles);

        boolean concreteLogFound = findSpecificLogInFiles(concreteLog, logFiles);

        if(SHOW_LOGS) System.out.println("testProcessPendingLogsSync, concreteLogFound " + concreteLogFound);

        assertTrue(concreteLogFound);

    }

    @Repeat(times = TESTS_REPEAT_TIME)
    @Test
    public void testStopLoggingAfterCallingStop() throws IOException, InterruptedException {
        if(SHOW_LOGS) System.out.println(">> testStopLoggingAfterCallingStop");

        long maxFileSize = 10;

        if(SHOW_LOGS) System.out.println("testStopLoggingAfterCallingStop, mDirectory[" + mDirectory.getAbsolutePath() + "]");

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
                        .setLoggerFileName("VoNaLoggerFileName")
                        .setLoggerFilesDir(mDirectory)
                        .setLogFileMaxSize(maxFileSize)
                        .build();

        /**
         * Write a 1000 logs
         */
        for(int index = 0; index < 1000; index++){
            assertEquals(mVoNaLogger.writeLog("First Log"), 1);
        }

        final AtomicBoolean syncObject = new AtomicBoolean();

        mVoNaLogger.stopLoggingAndGetLogFiles(new GetFilesCallback() {
            @Override
            public void onFilesReady(File[] logFiles) {
                if(SHOW_LOGS) System.out.println("onFilesReady, logFiles " + Arrays.toString(logFiles));
                if(SHOW_LOGS) System.out.println("onFilesReady, logFiles " + Arrays.toString(mDirectory.listFiles()));

                synchronized (syncObject) {
                    syncObject.set(true);
                    syncObject.notify();
                }

            }
        });

        synchronized (syncObject) {
            if(!syncObject.get()){
                if(SHOW_LOGS) System.out.println("testStopLoggingAfterCallingStop, wait" );
                syncObject.wait();
            }
        }
        if(SHOW_LOGS) System.out.println("testStopLoggingAfterCallingStop, wait >>" );

        assertEquals(mVoNaLogger.writeLog("Any log"), 0);

        if(SHOW_LOGS) System.out.println("<< testStopLoggingAfterCallingStop");

    }

    @Repeat(times = TESTS_REPEAT_TIME)
    @Test
    public void testStartLoggingAfterStop() throws IOException, InterruptedException {
        if(SHOW_LOGS) System.out.println(">> testStartLoggingAfterStop");

        long maxFileSize = 10;

        if(SHOW_LOGS) System.out.println("testStartLoggingAfterStop, mDirectory[" + mDirectory.getAbsolutePath() + "]");

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
                        .setLoggerFileName("VoNaLoggerFileName")
                        .setLoggerFilesDir(mDirectory)
                        .setLogFileMaxSize(maxFileSize)
                        .build();

        /**
         * Write a 1000 logs
         */
        for(int index = 0; index < 1000; index++){
            mVoNaLogger.writeLog("First Log");
        }

        String concreteLog = "Concrete Log";
        mVoNaLogger.writeLog(concreteLog);

        File[] logFiles = mVoNaLogger.processPendingLogsStopAndGetLogFilesSync();
        if(SHOW_LOGS) System.out.println("testStartLoggingAfterStop, logFiles " + Arrays.toString(logFiles));
        boolean concreteLogFound = findSpecificLogInFiles(concreteLog, logFiles);
        assertTrue(concreteLogFound);

        /** init logger again*/
        mVoNaLogger.initVoNaLoggerAfterStopping();

        String newConcreteLog = "newConcreteLog";
        assertEquals(mVoNaLogger.writeLog(newConcreteLog), 1);

        File[] logFilesAfterRestart = mVoNaLogger.processPendingLogsStopAndGetLogFilesSync();
        if(SHOW_LOGS) System.out.println("testStartLoggingAfterStop, logFilesAfterRestart " + Arrays.toString(logFilesAfterRestart));

        /** Check if new log found after restart*/
        boolean newConcreteLogFound = findSpecificLogInFiles(newConcreteLog, logFiles);
        assertTrue(newConcreteLogFound);

        if(SHOW_LOGS) System.out.println("<< testStartLoggingAfterStop");

    }

    @Repeat(times = TESTS_REPEAT_TIME)
    @Test
    public void testGetSnapShot() throws IOException, InterruptedException {
        if(SHOW_LOGS) System.out.println(">> testGetSnapShot");

        long maxFileSize = 10;

        if(SHOW_LOGS) System.out.println("testGetSnapShot, mDirectory[" + mDirectory.getAbsolutePath() + "]");

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
                        .setLoggerFileName("VoNaLoggerFileName")
                        .setLoggerFilesDir(mDirectory)
                        .setLogFileMaxSize(maxFileSize)
                        .build();

        /**
         * Write a 1000 logs
         */
        for(int index = 0; index < 1000; index++){
            mVoNaLogger.writeLog("First Log");
        }

        File[] snapShotLogFiles = mVoNaLogger.getLoggingFilesSnapShotSync();
        if(SHOW_LOGS) System.out.println("testGetSnapShot, snapShotLogFiles " + Arrays.toString(snapShotLogFiles));
        assertNotNull(snapShotLogFiles);

        showFilesContent(snapShotLogFiles);

        String concreteLog = "Concrete Log";
        assertEquals(mVoNaLogger.writeLog(concreteLog), 1);

        File[] logFilesAfterRestart = mVoNaLogger.processPendingLogsStopAndGetLogFilesSync();
        if(SHOW_LOGS) System.out.println("testGetSnapShot, logFilesAfterRestart " + Arrays.toString(logFilesAfterRestart));


        boolean concreteLogFound = findSpecificLogInFiles(concreteLog, logFilesAfterRestart);

        /** Check if concrete log found after getting the snapshot*/
        assertTrue(concreteLogFound);

        if(SHOW_LOGS) System.out.println("<< testGetSnapShot");

    }

    private void showFilesContent(File[] logFiles) {
        for(File logFile : logFiles){
            showFileContent(logFile);
        }
    }

    private boolean findSpecificLogInFiles(String concreteLog, File[] logFiles) {
        boolean found = false;
        for(File logFile : logFiles){
            found = findLogInFile(concreteLog, logFile);
            if(found){
                break;
            }
        }
        return found;
    }

    private boolean findLogInFile(String concreteLog, File logFile) {
        if(SHOW_LOGS) System.out.println("findLogInFile, concreteLog[" + concreteLog + "] " + logFile);

        boolean found = false;
        BufferedReader inFile = null;

        try {
            inFile = new BufferedReader(new FileReader(logFile));
            String line;
            int index = 1;
            while((line = inFile.readLine()) != null)
            {
                if(SHOW_LOGS) System.out.println("> line " + index + " findLogInFile, line[" + line + "]");

                if(line.contains(concreteLog)){
                    found = true;
                    break;
                }
                index++;
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(inFile!= null){
                    inFile.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return found;
    }

    private File createDirectoryIfNeeded(String logFilesDirectory) {
        File currDir = new File(".");
        String path = currDir.getAbsolutePath();
        path = path.substring(0, path.length() - 1);

        if(SHOW_LOGS) System.out.println("createDirectoryIfNeeded, path[" + path + "]");

        currDir = new File(path, logFilesDirectory);

        if (!currDir.exists()) {
            if (!currDir.mkdirs()) {
                throw new RuntimeException("Developer error?. Directory not created.");
            }
        }

        if(SHOW_LOGS) System.out.println("createDirectoryIfNeeded, currDir[" + currDir + "]");

        return currDir;
    }

    private void showFileContent(File file) {

        if(!SHOW_LOGS){
            return;
        }

        if(SHOW_LOGS) System.out.println(">> showFileContent, file " + file);

        BufferedReader inFile = null;

        try {
            inFile = new BufferedReader(new FileReader(file));
            String line;
            int index = 1;
            while((line = inFile.readLine()) != null)
            {
                if(SHOW_LOGS) System.out.println("> line " + index + " ["+ line + "]");
                index++;
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(inFile!= null){
                    inFile.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(SHOW_LOGS) System.out.println("<< showFileContent, file " + file);
    }

    private void clearDirectory(File directory) {
        //noinspection ConstantConditions
        for(File file: directory.listFiles()){
            if (!file.isDirectory()){
                //noinspection ResultOfMethodCallIgnored
                boolean deleted = file.delete();
                if(deleted){
                    if(SHOW_LOGS) System.out.println("clearDirectory, file deleted, " + file);
                }
            }
        }
    }

    private int getMaxFileSize() {
        /**
         * 1kb
         */
        return 1024;
    }
}

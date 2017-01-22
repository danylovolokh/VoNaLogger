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
import static org.junit.Assert.assertTrue;

public class VoNaLoggerTest {

    private static final int TESTS_REPEAT_TIME = 1000;
    private VoNaLogger mVoNaLogger;

    private File mDirectory;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Before
    public void before(){
        System.out.println("before");
        mDirectory = createDirectoryIfNeeded("log_files_directory");
    }

    @After
    public void after(){
        System.out.println("after");
        clearDirectory(mDirectory);
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
                System.out.println("onFilesReady, logFiles " + Arrays.toString(logFiles));

                for(File logFile: logFiles){
                    System.out.println("onFilesReady, logFile.length() " + logFile.length());

                    totalFilesSizeInBytes.addAndGet(logFile.length());
                    showFileContent(logFile);
                }

                System.out.println("onFilesReady, totalFilesSizeInBytes " + totalFilesSizeInBytes);

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
                System.out.println("onFilesReady, logFiles " + Arrays.toString(logFiles));

                for(File logFile: logFiles){
                    System.out.println("onFilesReady, logFile.length() " + logFile.length());
                    showFileContent(logFile);
                }

                concreteLogFound.set(findSpecificLogInFiles(concreteLog, logFiles));

                System.out.println("onFilesReady, concreteLogFound " + concreteLogFound);

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
        System.out.println(">> testFilesCreated");

        long maxFileSize = getMaxFileSize();


        System.out.println("testFilesCreated, mDirectory[" + mDirectory.getAbsolutePath() + "]");

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
                        .setLoggerFileName("VoNaLoggerFileName")
                        .setLoggerFilesDir(mDirectory)
                        .setLogFileMaxSize(maxFileSize)
                        .build();

        final AtomicBoolean filesEquals = new AtomicBoolean(false);

        final Object lockObject = new Object();

        File[] logFiles = mVoNaLogger.stopLoggingAndGetLogFilesSync();

        System.out.println("<< testFilesCreated, filesEquals " + filesEquals);

        assertTrue(Arrays.equals(logFiles, mDirectory.listFiles()));

    }

    @Repeat(times = TESTS_REPEAT_TIME)
    @Test
    public void testStopLoggingAfterCallingStop() throws IOException, InterruptedException {
        System.out.println(">> testStopLoggingAfterCallingStop");

        long maxFileSize = 10;

        System.out.println("testStopLoggingAfterCallingStop, mDirectory[" + mDirectory.getAbsolutePath() + "]");

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
                System.out.println("onFilesReady, logFiles " + Arrays.toString(logFiles));
                System.out.println("onFilesReady, logFiles " + Arrays.toString(mDirectory.listFiles()));

                synchronized (syncObject) {
                    syncObject.set(true);
                    syncObject.notify();
                }

            }
        });

        synchronized (syncObject) {
            if(!syncObject.get()){
                System.out.println("testStopLoggingAfterCallingStop, wait" );
                syncObject.wait();
            }
        }
        System.out.println("testStopLoggingAfterCallingStop, wait >>" );

        assertEquals(mVoNaLogger.writeLog("Any log"), 0);

        System.out.println("<< testStopLoggingAfterCallingStop");

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
        System.out.println("findLogInFile, concreteLog[" + concreteLog + "] " + logFile);

        boolean found = false;
        BufferedReader inFile = null;

        try {
            inFile = new BufferedReader(new FileReader(logFile));
            String line;
            while((line = inFile.readLine()) != null)
            {
                System.out.println("findLogInFile, line[" + line + "]");

                if(line.contains(concreteLog)){
                    found = true;
                    break;
                }
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

        System.out.println("createDirectoryIfNeeded, path[" + path + "]");

        currDir = new File(path, logFilesDirectory);

        if (!currDir.exists()) {
            if (!currDir.mkdirs()) {
                throw new RuntimeException("Developer error?. Directory not created.");
            }
        }

        System.out.println("createDirectoryIfNeeded, currDir[" + currDir + "]");

        return currDir;
    }

    private void showFileContent(File file) {
        System.out.println(">> showFileContent, file " + file);

        BufferedReader inFile = null;

        try {
            inFile = new BufferedReader(new FileReader(file));
            String line;
            int index = 1;
            while((line = inFile.readLine()) != null)
            {
                System.out.println("> linea " + index + " ["+ line + "]");
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
        System.out.println("<< showFileContent, file " + file);
    }

    private void clearDirectory(File directory) {
        //noinspection ConstantConditions
        for(File file: directory.listFiles()){
            if (!file.isDirectory()){
                //noinspection ResultOfMethodCallIgnored
                file.delete();
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

import com.volokh.danylo.vonalogger.GetFilesCallback;
import com.volokh.danylo.vonalogger.VoNaLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VoNaLoggerTest {

    private VoNaLogger mVoNaLogger;

    private File mDirectory;

    @Before
    public void before(){
        mDirectory = createDirectoryIfNeeded("log_files_directory");
    }

    @After
    public void after(){
        clearDirectory(mDirectory);
    }

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
        for(int charIndex = 0; charIndex < minimumEntriesCount + 1; charIndex++){
            mVoNaLogger.writeLog('a');
            // + "/n" is added after every log entry
            // this means that actual amount of characters will be "maxFileSize * 2"
        }

        final Object lockObject = new Object();

        final AtomicLong totalFilesSizeInBytes = new AtomicLong(0);
        mVoNaLogger.stopLoggingAndGetLogFiles(new GetFilesCallback() {
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
                    lockObject.notify();
                }

            }
        });

        synchronized (lockObject) {
            lockObject.wait();
        }

        assertEquals(expectedMaxFileSize, totalFilesSizeInBytes.intValue());
    }

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

        mVoNaLogger.stopLoggingAndGetLogFiles(new GetFilesCallback() {
            @Override
            public void onFilesReady(File[] logFiles) {
                System.out.println("onFilesReady, logFiles " + Arrays.toString(logFiles));
                System.out.println("onFilesReady, logFiles " + Arrays.toString(mDirectory.listFiles()));

                filesEquals.set(Arrays.equals(logFiles, mDirectory.listFiles()));

                synchronized (lockObject) {
                    lockObject.notify();
                }

            }
        });

        synchronized (lockObject) {
            lockObject.wait();
        }
        System.out.println("<< testFilesCreated, filesEquals " + filesEquals);

        assertTrue(filesEquals.get());

    }

    private long getMaxFileSize() {
        /**
         * 1kb
         */
        return (long) 1024;
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
}

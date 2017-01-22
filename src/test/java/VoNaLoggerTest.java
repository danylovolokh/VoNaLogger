import com.volokh.danylo.vonalogger.GetFilesCallback;
import com.volokh.danylo.vonalogger.VoNaLogger;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

public class VoNaLoggerTest {

    private VoNaLogger mVoNaLogger;

    @Test
    public void testMaxFileSizeNotExceeded() throws IOException {

//        /**
//         * 1kb
//         */
//        long maxFileSize = 1024;
//
//        File directory = new File(File.separator);
//        mVoNaLogger =
//                new VoNaLogger
//                        .Builder()
//                        .setLoggerFileName("VoNaLoggerFileName")
//                        .setLoggerFilesDir(directory)
//                        .setLogFileMaxSize(maxFileSize)
//                        .build();
//
//        StringBuilder stringBuilder = new StringBuilder();
//
//        /**
//         * Assuming a single char is taking at least 8 bits (1 byte)
//         * After writing {@link maxFileSize} amount of charaters we will
//         * definitely fill the log file;
//         */
//        for(int charIndex = 0; charIndex < maxFileSize; charIndex++){
//
//        }
    }

    @Test
    public void testFilesCreated() throws IOException, InterruptedException {
        System.out.println(">> testFilesCreated");

        /**
         * 1kb
         */
        long maxFileSize = 1024;

        final File directory = createDirectoryIfNeeded("log_files_directory");

        System.out.println("testFilesCreated, directory[" + directory.getAbsolutePath() + "]");

        mVoNaLogger =
                new VoNaLogger
                        .Builder()
                        .setLoggerFileName("VoNaLoggerFileName")
                        .setLoggerFilesDir(directory)
                        .setLogFileMaxSize(maxFileSize)
                        .build();

        final AtomicBoolean filesCreated = new AtomicBoolean(false);

        final Object lockFile = new Object();

        mVoNaLogger.stopLoggingAndGetLogFiles(new GetFilesCallback() {
            @Override
            public void onFilesReady(File[] logFiles) {
                System.out.println("onFilesReady, logFiles " + Arrays.toString(logFiles));
                System.out.println("onFilesReady, logFiles " + Arrays.toString(directory.listFiles()));

                filesCreated.set(Arrays.equals(logFiles, directory.listFiles()));

                synchronized (lockFile){
                    lockFile.notify();
                }

            }
        });

        synchronized (lockFile){
            lockFile.wait();
        }
        System.out.println("<< testFilesCreated, filesCreated " + filesCreated);

        assertTrue(filesCreated.get());

    }

    private File createDirectoryIfNeeded(String logFilesDirectory) {
        File currDir = new File(".");
        String path = currDir.getAbsolutePath();
        path = path.substring(0, path.length() - 1);

        System.out.println("createDirectoryIfNeeded, path[" + path + "]");

        currDir = new File(path, logFilesDirectory);

        if(!currDir.exists()){
            if(!currDir.mkdirs()){
                throw new RuntimeException("Developer error?. Directory not created.");
            }
        }

        System.out.println("createDirectoryIfNeeded, currDir[" + currDir + "]");

        return currDir;
    }
}

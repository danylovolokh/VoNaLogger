package com.volokh.danylo.vonalogger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

public interface VoNaLogger {

    void stopLoggingAndGetLogFiles(GetFilesCallback filesCallback);
    void getLoggingFilesSnapShot(GetFilesCallback filesCallback);

    int writeLog(Object... parameters);

    class Builder {

        private String mLogFileName;
        private File mLogDir;
        private long mLogFileMaxSize;
        private Integer mEntriesList;

        public Builder setLoggerFileName(String logFileName){
            if(mLogFileName != null){
                throw new IllegalArgumentException("logFileName is already set for this Logger. Please create new logger.");
            }
            this.mLogFileName = logFileName;
            return this;
        }

        public Builder setLoggerFilesDir(File logDir){

            this.mLogDir = logDir;

            return this;
        }

//        /**
//         * This is optional. If it's not called a new thread will be created inside
//         * {@link VoNaLoggerImpl()}
//         *
//         * @param executor
//         * @return
//         */
//        public Builder setThreadExecutor(Executor executor){
//            this.mExecutor = executor;
//            return this;
//        }

        public Builder setLogFileMaxSize(long sizeInBytes){
            mLogFileMaxSize = sizeInBytes;
            return this;
        }

        public VoNaLogger build() throws IOException {

            checkLogDirNotNull();
            checkLogFileNameNotNull();
            checkMaxFileSizeSpecified();

            return new VoNaLoggerImpl(mLogDir, mLogFileName, mLogFileMaxSize, mEntriesList);
        }

        private void checkMaxFileSizeSpecified() {
            if(mLogFileMaxSize == 0){
                throw new IllegalArgumentException("No max file size specified. Please specify max file size");
            }
        }

        private void checkLogDirNotNull() {
            if(mLogDir == null){
                throw new IllegalArgumentException("No log directory was specified. Please specify directory for log file");
            }
        }

        private void checkLogFileNameNotNull() {
            if(mLogFileName == null){
                throw new IllegalArgumentException("No log file name was specified. Please specify log file name");
            }
        }
    }
}

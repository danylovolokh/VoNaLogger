package com.volokh.danylo.vonalogger;

import java.io.File;
import java.io.IOException;

public interface VoNaLogger {

    void initVoNaLoggerAfterStopping() throws IOException;

    /**
     * After calling this method logger is no longer valid.
     * New instance needs to be created.
     */
    void releaseResources();

    File[] stopLoggingAndGetLogFilesSync();
    File[] processPendingLogsStopAndGetLogFilesSync();

    void stopLoggingAndGetLogFiles(GetFilesCallback filesCallback);
    void processPendingLogsStopAndGetLogFiles(GetFilesCallback filesCallback);

    /**
     * This method returns a files that contain a snapshot of a current files that are being logged.
     * @return array of snapshot files.
     */
    File[] getLoggingFilesSnapShotSync();

    /**
     * This method is used to write log into file.
     *
     * @param parameters - array of items that will be separated and interpreted as a simple log entry.
     * @return error code. 0 if logger is no longer process the parameters.
     */
    int writeLog(Object... parameters);

    class Builder {

        private String mLogFileName;
        private File mLogDir;
        private long mLogFileMaxSize;

        private Integer mMinimumEntriesCount;

        /**
         * This method sets the file name for a logging.
         * This file name is passed to the concrete constructor - {@link VoNaLoggerImpl}
         * Calling this method is mandatory
         */
        public Builder setLoggerFileName(String logFileName){
            if(mLogFileName != null){
                throw new IllegalArgumentException("logFileName is already set for this Logger. Please create new logger.");
            }
            this.mLogFileName = logFileName;
            return this;
        }

        /**
         * This method sets the directory in which the log files will be stored.
         * Calling this method is mandatory
         */
        public Builder setLoggerFilesDir(File logDir){
            this.mLogDir = logDir;
            return this;
        }

        /**
         * This methods sets max size in bytes for the log files.
         * Calling this method is mandatory
         */
        public Builder setLogFileMaxSize(long sizeInBytes){
            mLogFileMaxSize = sizeInBytes;
            return this;
        }

        /**
         * This method creates a concrete class of {@link VoNaLogger}
         */
        public VoNaLogger build() throws IOException {

            checkLogDirNotNull();
            checkLogFileNameNotNull();
            checkMaxFileSizeSpecified();

            return new VoNaLoggerImpl(mLogDir, mLogFileName, mLogFileMaxSize, mMinimumEntriesCount);
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

        /**
         * This is the minimum entries count that will be written into log file.
         *
         * If this number is bigger than file size then file size will be exceeded no more that the amount of text in
         * minimum entries count
         */
        public Builder setMinimumEntriesCount(int minimumEntriesCount) {
            mMinimumEntriesCount = minimumEntriesCount;
            return this;
        }
    }
}

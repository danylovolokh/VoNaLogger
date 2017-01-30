# VoNaLogger
This is a lightweight Logger written in pure Java.

# Usage
Add this snippet to your project build.gradle file:
```
buildscript {
    repositories {
        jcenter()
    }
}
```
Or Maven

```
<dependency>
  <groupId>com.github.danylovolokh</groupId>
  <artifactId>vona-logger</artifactId>
  <version>1.0.1</version>
  <type>pom</type>
</dependency>
```

# Usage of VoNaLogger
```
dependencies {
    compile 'com.github.danylovolokh:vona-logger:1.0.1'
}
```
First of all it needs to be initialized:
```
File directory = //...
String logFileName = //... File name without ".extention"
int logFileMaxSizeBytes = //.. Example : 2 * 1024 * 1024 = 2Mb

// IF THIS WILL BE TRUE IT WILL AFFECT PERFORMANCE VERY MUCH.
boolean showLogs = false;

VoNaLogger voNaLogger = new VoNaLogger
                .Builder()
                .setLoggerFilesDir(directory)
                .setLoggerFileName(logFileName)
                .setLogFileMaxSize(logFileMaxSizeBytes)
                .setShowLogs(showLogs)
                .build();
                
// Use it by calling 
voNaLogger.writeLog(/* variable count of parameters... */);
                
```
# Few options to get the logs
```
// 1. Async operation that stops logging and returns files with logs. After calling it it has to be re-initialized.
voNaLogger.stopLoggingAndGetLogFiles(new com.volokh.danylo.vonalogger.GetFilesCallback() {
            @Override
            public void onFilesReady(File[] logFiles) {
              // get all the information you need from files
              // re- initialize the logger by calling 
              
              voNaLogger.initVoNaLoggerAfterStopping();
              
            }
        });

// 2. Synchronous operation that stops logging and returns files with logs. After calling it it has to be re-initialized.
File[] logFiles = voNaLogger.stopLoggingAndGetLogFilesSync();
voNaLogger.initVoNaLoggerAfterStopping();

// 3. Calling this method will stop logging. But it will write all the logs to files until #onFilesReady() will be called.
voNaLogger.processPendingLogsStopAndGetLogFiles(new com.volokh.danylo.vonalogger.GetFilesCallback() {
            @Override
            public void onFilesReady(File[] logFiles) {
              // get all the information you need from files
              // re- initialize the logger by calling 
              
              voNaLogger.initVoNaLoggerAfterStopping();
                
            }
        });

// 4. Synchronous operation that stops logging and returns files with logs. After calling it it has to be re-initialized.
// But this method will write all the logs to files until return. So this method call might took a lot of time.
File[] logFiles = voNaLogger.processPendingLogsStopAndGetLogFilesSync();
voNaLogger.initVoNaLoggerAfterStopping();

// 5. Calling this method will return a Files snapshot and it doesn't stop logger so it shouldn't be initialized again.
File[] logFiles = voNaLogger.getLoggingFilesSnapShotSync();

```
# How it works
The logs (any parameters passed to the logger) are stored in Log Entries.

TODO:

# License

Copyright 2017 Danylo Volokh

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

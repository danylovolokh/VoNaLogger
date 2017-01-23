package com.volokh.danylo.vonalogger;

import java.util.Arrays;

/**
 * Created by danylo.volokh on 12/28/16.
 *
 * Log Entry contains the parameters that has to be written into file.
 */
class LogEntry {

    private final StringBuilder mStringBuilder = new StringBuilder();

    private Object[] parameters;

    void setLogParameters(Object... parameters){
        this.parameters = parameters;
    }

    boolean isEntryFilledWithData(){
        return parameters != null;
    }

    /**
     * This method returns merged string of parameters passed to the logger.
     * After creating a merged string it cleans the content to prevent reusing non-valid data.
     *
     * Parameters are split with tabulation sign.
     */
    String getMergedStringAndClean(){

        mStringBuilder.delete(0, mStringBuilder.length());

        for(int index = 0; index < parameters.length; index++){
            mStringBuilder.append(parameters[index]);

            // we don't need to add the tab after last entry
            if(index < parameters.length - 1){
                mStringBuilder.append("\t");
            }
        }

        parameters = null;

        return mStringBuilder.toString();
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "parameters=" + Arrays.toString(parameters) +
                '}';
    }
}

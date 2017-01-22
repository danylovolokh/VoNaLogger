package com.volokh.danylo.vonalogger;

/**
 * Created by danylo.volokh on 12/28/16.
 *
 * Logh Entry contains the parameters taht has to be written into file.
 */
class LogEntry {

    private final StringBuilder mStringBuilder = new StringBuilder();

    private Object[] parameters;

    void setLogParameters(Object... parameters){
        this.parameters = parameters;
    }

    String getMergedString(){

        mStringBuilder.delete(0, mStringBuilder.length());

        for(Object param : parameters){
            mStringBuilder.append(param);
            mStringBuilder.append("\t");

        }

        return mStringBuilder.toString();
    }
}

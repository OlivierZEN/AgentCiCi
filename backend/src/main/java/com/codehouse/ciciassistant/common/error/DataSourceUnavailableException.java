package com.codehouse.ciciassistant.common.error;

public class DataSourceUnavailableException extends RuntimeException {

    public DataSourceUnavailableException() {
        super("DATA_SOURCE_UNAVAILABLE");
    }

    public DataSourceUnavailableException(Throwable cause) {
        super("DATA_SOURCE_UNAVAILABLE", cause);
    }
}

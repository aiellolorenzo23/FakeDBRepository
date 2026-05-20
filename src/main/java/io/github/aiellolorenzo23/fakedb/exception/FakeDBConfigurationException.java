package io.github.aiellolorenzo23.fakedb.exception;

public class FakeDBConfigurationException extends FakeDBException {

    public FakeDBConfigurationException(String message) {
        super(message);
    }

    public FakeDBConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

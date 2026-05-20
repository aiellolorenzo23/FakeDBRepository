package io.github.aiellolorenzo23.fakedb.exception;

public class FakeDBException extends RuntimeException {

    public FakeDBException(String message) {
        super(message);
    }

    public FakeDBException(String message, Throwable cause) {
        super(message, cause);
    }
}

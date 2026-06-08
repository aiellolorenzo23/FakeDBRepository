package io.github.aiellolorenzo23.fakedb.exception;

public class FakeDBConstraintViolationException extends FakeDBException {

    public FakeDBConstraintViolationException(String message) {
        super(message);
    }

    public FakeDBConstraintViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}

package net.ripe.rpki.hsm.db;

public class DatabaseKeyStorageException extends RuntimeException {
    public DatabaseKeyStorageException(String message) {
        super(message);
    }

    public DatabaseKeyStorageException(Throwable cause) {
        super(cause);
    }
}

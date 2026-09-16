package dev.minted.backend;

/** Wraps the checked SQL failures so they don't leak into the economy layer. */
public final class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

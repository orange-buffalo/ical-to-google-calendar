package ictgc.domain;

/**
 * Wrapper on checked exceptions.
 */
public class CalendarSynchronizationException extends RuntimeException {

    public CalendarSynchronizationException() {
        super();
    }

    public CalendarSynchronizationException(String message) {
        super(message);
    }

    public CalendarSynchronizationException(String message, Throwable cause) {
        super(message, cause);
    }

    public CalendarSynchronizationException(Throwable cause) {
        super(cause);
    }

    protected CalendarSynchronizationException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

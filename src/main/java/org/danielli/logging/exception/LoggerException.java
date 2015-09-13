package org.danielli.logging.exception;

/**
 * 日志异常。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class LoggerException extends RuntimeException {

    private static final long serialVersionUID = 6366395965071580537L;

    public LoggerException(Throwable cause) {
        super(cause);
    }

    public LoggerException(String message, Throwable cause) {
        super(message, cause);
    }
}

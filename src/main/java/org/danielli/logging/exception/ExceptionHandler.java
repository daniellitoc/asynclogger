package org.danielli.logging.exception;

import org.danielli.logging.LoggerEvent;

/**
 * 异常处理器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public interface ExceptionHandler {

    void handleEventException(String msg, Throwable e, LoggerEvent event);

    void handleException(String msg, Throwable e);

    void handle(String msg);
}

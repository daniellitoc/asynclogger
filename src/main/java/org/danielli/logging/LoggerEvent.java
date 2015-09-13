package org.danielli.logging;

import java.io.Serializable;

/**
 * 日志事件。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public interface LoggerEvent extends Serializable {

    long getTimeMillis();

    byte[] toByteArray();
}

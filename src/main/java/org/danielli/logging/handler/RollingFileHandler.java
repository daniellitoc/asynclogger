package org.danielli.logging.handler;

import org.danielli.logging.LoggerEvent;
import org.danielli.logging.roll.pattern.FilePattern;

/**
 * 可轮转日志文件。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public interface RollingFileHandler extends FileHandler {

    void initialize();

    void checkRollover(LoggerEvent event);

    FilePattern getFilePattern();

}

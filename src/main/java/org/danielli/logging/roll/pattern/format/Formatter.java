package org.danielli.logging.roll.pattern.format;

/**
 * 格式化器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public interface Formatter {

    void format(StringBuilder source, Object... arguments);

}

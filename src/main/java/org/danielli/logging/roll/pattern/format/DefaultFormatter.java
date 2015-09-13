package org.danielli.logging.roll.pattern.format;

/**
 * 默认格式化器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class DefaultFormatter implements Formatter {

    private final String pattern;

    public DefaultFormatter(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public void format(StringBuilder source, Object... arguments) {
        source.append(this.pattern);
    }
}

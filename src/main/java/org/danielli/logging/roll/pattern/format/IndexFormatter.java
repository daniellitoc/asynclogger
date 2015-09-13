package org.danielli.logging.roll.pattern.format;

/**
 * 索引格式化器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class IndexFormatter implements Formatter {

    @Override
    public void format(StringBuilder source, Object... arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Integer) {
                source.append(argument);
                break;
            }
        }
    }
}

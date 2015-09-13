package org.danielli.logging.roll;

import org.danielli.logging.roll.action.Action;
import org.danielli.logging.roll.pattern.FilePattern;

/**
 * 轮转器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public interface Rollover {

    Description rollover(String fileName, FilePattern pattern);

    /**
     * 描述。
     *
     * @author Daniel Li
     * @since 29 August 2015
     */
    public interface Description {

        Action getSync();

        Action getAsync();
    }

}

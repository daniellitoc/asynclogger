package org.danielli.logging.roll.action;

import java.io.IOException;

/**
 * 轮转动作。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public interface Action extends Runnable {

    boolean execute() throws IOException;

    void close();

    boolean isComplete();

}

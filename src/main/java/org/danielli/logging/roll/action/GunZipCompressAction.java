package org.danielli.logging.roll.action;

import org.danielli.logging.exception.ExceptionHandler;
import org.danielli.common.io.Files;

import java.io.File;
import java.io.IOException;

/**
 * 轮转时压缩行为。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class GunZipCompressAction extends AbstractAction {

    private final File source;
    private final File destination;
    private final boolean deleteSource;
    private final int bufferSize;

    public GunZipCompressAction(File source, File destination, boolean deleteSource, int bufferSize, ExceptionHandler handler) {
        super(handler);
        this.source = source;
        this.destination = destination;
        this.deleteSource = deleteSource;
        this.bufferSize = bufferSize;
    }

    @Override
    public boolean execute() throws IOException {
        if (source.exists()) {
            Files.compressFile(source, destination, bufferSize);

            if (deleteSource && !source.delete()) {
                handler.handle("Unable to delete " + source.getPath());
            }
            return true;
        }
        return false;
    }

}

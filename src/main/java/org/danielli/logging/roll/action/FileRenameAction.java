package org.danielli.logging.roll.action;

import org.danielli.logging.exception.ExceptionHandler;
import org.danielli.common.io.Files;

import java.io.File;
import java.io.IOException;

/**
 * 轮转时重命名行为。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class FileRenameAction extends AbstractAction {

    private final File source;
    private final File destination;

    public FileRenameAction(File source, File destination, ExceptionHandler handler) {
        super(handler);
        this.source = source;
        this.destination = destination;
    }

    @Override
    public boolean execute() throws IOException {
        if (source.exists() && source.length() > 0) {
            final File parent = destination.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
                if (!parent.exists()) {
                    handler.handle("Unable to create directory " + parent.getPath());
                    return false;
                }
            }
            try {
                if (!source.renameTo(destination)) {
                    try {
                        Files.copyFile(source, destination);
                        return source.delete();
                    } catch (IOException e) {
                        handler.handleException("Unable to rename file " + source.getPath() + " to " + destination.getPath(), e);
                    }
                }
                return true;
            } catch (Exception ex) {
                try {
                    Files.copyFile(source, destination);
                    return source.delete();
                } catch (IOException e) {
                    handler.handleException("Unable to rename file " + source.getPath() + " to " + destination.getPath(), e);
                }
            }
        }
        return false;
    }
}

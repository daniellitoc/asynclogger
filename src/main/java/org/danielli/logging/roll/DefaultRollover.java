package org.danielli.logging.roll;

import org.danielli.logging.exception.ExceptionHandler;
import org.danielli.logging.roll.action.Action;
import org.danielli.logging.roll.action.FileRenameAction;
import org.danielli.logging.roll.action.GunZipCompressAction;
import org.danielli.common.io.Files;
import org.danielli.logging.roll.pattern.FilePattern;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认轮转器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class DefaultRollover implements Rollover {

    private final int maxIndex;
    private final int minIndex;
    private final boolean useMax;
//    private final int compressionLevel;
    private final int bufferSize;
    private final ExceptionHandler handler;

    public DefaultRollover(int minIndex, int maxIndex, boolean useMax, int compressionLevel, int bufferSize, ExceptionHandler handler) {
        this.minIndex = minIndex;
        this.maxIndex = maxIndex;
        this.useMax = useMax;
//        this.compressionLevel = compressionLevel;
        this.bufferSize = bufferSize;
        this.handler = handler;
    }

    private int purge(int lowIndex, int highIndex, FilePattern pattern) {
        return useMax ? purgeAscending(lowIndex, highIndex, pattern) : purgeDescending(lowIndex, highIndex, pattern);
    }

    private int purgeAscending(int lowIndex, int highIndex, FilePattern pattern) {
        List<FileRenameAction> renames = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        pattern.format(buf, highIndex);
        String highFilename = buf.toString();

        int maxIndex = 0;

        for (int i = highIndex; i >= lowIndex; i--) {
            File toRename = new File(highFilename);
            if (i == highIndex && toRename.exists()) {
                maxIndex = highIndex;
            } else if (maxIndex == 0 && toRename.exists()) {
                maxIndex = i + 1;
                break;
            }

            boolean isBase = false;

            if (pattern.isGunZip()) {
                File toRenameBase = new File(Files.removeExtension(highFilename));
                if (toRename.exists()) {
                    if (toRenameBase.exists()) {
                        toRenameBase.delete();
                    }
                } else {
                    toRename = toRenameBase;
                    isBase = true;
                }
            }

            if (toRename.exists()) {
                if (i == lowIndex) {
                    if (!toRename.delete()) {
                        return -1;
                    }
                    break;
                }

                buf.setLength(0);
                pattern.format(buf, i - 1);
                String lowFilename = buf.toString();

                String renameTo = lowFilename;

                if (isBase) {
                    if (pattern.isGunZip()) {
                        renameTo = Files.removeExtension(lowFilename);
                    } else {
                        renameTo = lowFilename;
                    }
                }

                renames.add(new FileRenameAction(toRename, new File(renameTo), handler));
                highFilename = lowFilename;
            } else {
                buf.setLength(0);
                pattern.format(buf, i - 1);
                highFilename = buf.toString();
            }
        }
        if (maxIndex == 0) {
            maxIndex = lowIndex;
        }

        for (int i = renames.size() - 1; i >= 0; i--) {
            Action action = renames.get(i);
            try {
                if (!action.execute()) {
                    return -1;
                }
            } catch (Exception ex) {
                handler.handleException("Exception during purge in rolling", ex);
                return -1;
            }
        }
        return maxIndex;
    }

    private int purgeDescending(int lowIndex, int highIndex, FilePattern pattern) {
        List<FileRenameAction> renames = new ArrayList<>();

        StringBuilder buf = new StringBuilder();
        pattern.format(buf, lowIndex);
        String lowFilename = buf.toString();

        for (int i = lowIndex; i <= highIndex; i++) {
            File toRename = new File(lowFilename);
            boolean isBase = false;

            if (pattern.isGunZip()) {
                File toRenameBase = new File(Files.removeExtension(lowFilename));
                if (toRename.exists()) {
                    if (toRenameBase.exists()) {
                        toRenameBase.delete();
                    }
                } else {
                    toRename = toRenameBase;
                    isBase = true;
                }
            }

            if (toRename.exists()) {
                if (i == highIndex) {
                    if (!toRename.delete()) {
                        return -1;
                    }
                    break;
                }


                buf.setLength(0);
                pattern.format(buf, i + 1);
                String highFilename = buf.toString();

                String renameTo = highFilename;

                if (isBase) {
                    if (pattern.isGunZip()) {
                        renameTo = Files.removeExtension(highFilename);
                    } else {
                        renameTo = highFilename;
                    }
                }

                renames.add(new FileRenameAction(toRename, new File(renameTo), handler));
                lowFilename = highFilename;
            } else {
                break;
            }
        }

        for (int i = renames.size() - 1; i >= 0; i--) {
            Action action = renames.get(i);
            try {
                if (!action.execute()) {
                    return -1;
                }
            } catch (Exception ex) {
                handler.handleException("Exception during purge in rolling", ex);
                return -1;
            }
        }

        return lowIndex;
    }

    @Override
    public Description rollover(String fileName, FilePattern pattern) throws SecurityException {
        if (maxIndex < 0) {
            return null;
        }

        int fileIndex = purge(minIndex, maxIndex, pattern);
        if (fileIndex < 0) {
            return null;
        }

        StringBuilder buf = new StringBuilder(255);
        pattern.format(buf, fileIndex);

        String renameTo = buf.toString();
        String compressedName = renameTo;
        Action compressAction = null;

        if (pattern.isGunZip()) {
            renameTo = Files.removeExtension(renameTo);
            compressAction = new GunZipCompressAction(new File(renameTo), new File(compressedName), true, bufferSize, handler);
        }
        FileRenameAction renameAction = new FileRenameAction(new File(fileName), new File(renameTo), handler);

        return new DefaultDescription(renameAction, compressAction);
    }

    /**
     * 默认实现。
     *
     * @author Daniel Li
     * @since 29 August 2015
     */
    public class DefaultDescription implements Description {

        private final Action sync;
        private final Action async;

        public DefaultDescription(Action sync, Action async) {
            this.sync = sync;
            this.async = async;
        }

        @Override
        public Action getSync() {
            return sync;
        }

        @Override
        public Action getAsync() {
            return async;
        }
    }
}

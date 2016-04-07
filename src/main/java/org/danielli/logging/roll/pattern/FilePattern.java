package org.danielli.logging.roll.pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.danielli.common.clock.Clock;
import org.danielli.common.io.Files;
import org.danielli.logging.roll.pattern.format.DateFormatter;
import org.danielli.logging.roll.pattern.format.DefaultFormatter;
import org.danielli.logging.roll.pattern.format.Formatter;
import org.danielli.logging.roll.pattern.format.IndexFormatter;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轮转文件模式。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class FilePattern {

    private static final String[] GunZip = new String[]{"gz", "GZ"};

    private static final String Split = ".";
    private static final String RegexSplit = "\\" + Split;


    private final Formatter[] formatters;
    private final DateFormatter.Frequency frequency;
    private final boolean isGunZip;
    private final Clock clock;

    private long prevFileTime = 0;
    private long nextFileTime = 0;

    public FilePattern(String filePattern, Clock clock) {
        filePattern = Preconditions.checkNotNull(filePattern, "parameter 'filePattern' must not be null or empty");
        DateFormatter.Frequency frequency = null;
        String[] filePatterns = filePattern.split(RegexSplit);

        List<Formatter> formatters = Lists.newArrayListWithCapacity(filePatterns.length);
        for (String pattern : filePatterns) {
            Formatter formatter = FormatterFactory.valueOf(pattern);
            if (formatter instanceof DateFormatter) {
                frequency = ((DateFormatter) formatter).getFrequency();
            }
            formatters.add(formatter);
        }
        this.formatters = formatters.toArray(new Formatter[formatters.size()]);
        this.frequency = frequency;
        this.isGunZip = Files.isExtension(filePattern, GunZip);
        this.clock = clock;
    }


    public void format(StringBuilder source, int index) {
        Date fileTime = prevFileTime == 0 ? new Date(clock.currentTimeMillis()) : new Date(prevFileTime);
        for (int i = 0, length = formatters.length; i < length; i++) {
            Formatter formatter = formatters[i];
            if (i != 0) {
                source.append(Split);
            }
            formatter.format(source, fileTime, index);
        }
    }


    public long getNextTime(long millis, int increment, boolean modulus) {
        prevFileTime = nextFileTime;
        Map.Entry<Long, Long> pair = frequency.getNextTime(millis, increment, modulus);
        nextFileTime = pair.getValue();
        return pair.getKey();
    }

    public boolean containDate() {
        return this.frequency != null;
    }

    public void updateTime() {
        prevFileTime = nextFileTime;
    }

    public boolean isGunZip() {
        return isGunZip;
    }


    private static class FormatterFactory {

        private static Pattern datePattern = Pattern.compile("%d\\{(.*)\\}");

        private static Pattern indexPattern = Pattern.compile("%index");

        public static Formatter valueOf(String pattern) {
            Matcher dateMatcher = datePattern.matcher(pattern);
            if (dateMatcher.find()) {
                return new DateFormatter(dateMatcher.group(1));
            }
            if (indexPattern.matcher(pattern).matches()) {
                return new IndexFormatter();
            }
            return new DefaultFormatter(pattern);
        }

    }


}

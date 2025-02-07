/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2023 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/Apache2 OR 2/JSEL
 *
 *     1/ Apache2
 *     ==================================================================================
 *
 *     Copyright (C) 2002-2023 Jahia Solutions Group SA. All rights reserved.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.bin.errors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.ThrowablePatternConverter;
import org.apache.logging.log4j.util.Strings;

/**
 * Outputs the Throwable portion of the LoggingEvent as a full stack trace
 * unless this converter's option is 'short', where it just outputs the first line of the trace, or if
 * the number of lines to print is explicitly specified.
 * <p>
 * The extended stack trace will also include the location of where the class was loaded from and the
 * version of the jar if available.
 * <p>
 * If the message of the throwable contains a carriage return or line feed character, it gets encoded
 * to prevent log injection.
 */
@Plugin(name = "SafeExtendedThrowablePatternConverter", category = PatternConverter.CATEGORY)
@ConverterKeys({ "sxEx", "sxThrowable", "sxException" })
public final class SafeExtendedThrowablePatternConverter extends ThrowablePatternConverter {
    private static final String STACKTRACE_LINE_START = "\n\tat";
    private static final String NESTED_STACKTRACE_LINE_START = "\n\t\tat";
    /**
     * Private constructor.
     *
     * @param config
     * @param options options, may be null.
     */
    private SafeExtendedThrowablePatternConverter(final Configuration config, final String[] options) {
        super("SafeExtendedThrowable", "throwable", options, config);
    }

    /**
     * Gets an instance of the class.
     *
     * @param config The current Configuration.
     * @param options pattern options, may be null.  If first element is "short",
     *                only the first line of the throwable will be formatted.
     * @return instance of class.
     */
    public static SafeExtendedThrowablePatternConverter newInstance(final Configuration config, final String[] options) {
        return new SafeExtendedThrowablePatternConverter(config, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void format(final LogEvent event, final StringBuilder toAppendTo) {
        final ThrowableProxy proxy = event.getThrownProxy();
        final Throwable throwable = event.getThrown();
        if ((throwable != null || proxy != null) && options.anyLines()) {
            if (proxy == null) {
                super.format(event, toAppendTo);
                return;
            }
            final String suffix = getSuffix(event);
            final String extStackTrace = proxy.getExtendedStackTraceAsString(options.getIgnorePackages(), options.getTextRenderer(), suffix);
            final int len = toAppendTo.length();
            if (len > 0 && !Character.isWhitespace(toAppendTo.charAt(len - 1))) {
                toAppendTo.append(' ');
            }
            appendExtendedStackTrace(toAppendTo, extStackTrace);
        }
    }

    private void appendExtendedStackTrace(final StringBuilder toAppendTo, final String extStackTrace) {
        if (!options.allLines() || !Strings.LINE_SEPARATOR.equals(options.getSeparator())) {
            toAppendTo.append(replaceLineSeparator(extStackTrace));
        } else {
            int firstMessageWithCRLFIndex = indexOfMessageWithCRLF(extStackTrace);
            if (firstMessageWithCRLFIndex != -1) {
                toAppendTo.append(replaceLineSeparatorInMessages(extStackTrace, firstMessageWithCRLFIndex));
            } else {
                toAppendTo.append(extStackTrace);
            }
        }
    }

    private String replaceLineSeparator(String extStackTrace) {
        String[] array = extStackTrace.split(Strings.LINE_SEPARATOR);
        return StringUtils.join(array, options.getSeparator(), 0, options.minLines(array.length));
    }

    private int indexOfMessageWithCRLF(String extStackTrace) {
        int messageStartIndex = 0;
        int stackTraceStartIndex;
        do {
            stackTraceStartIndex = getNextIndex(extStackTrace.indexOf(STACKTRACE_LINE_START, messageStartIndex),
                    extStackTrace.indexOf(NESTED_STACKTRACE_LINE_START, messageStartIndex));
            if (stackTraceStartIndex != -1) {
                stackTraceStartIndex--;
                if (StringUtils.lastIndexOf(extStackTrace, '\r', stackTraceStartIndex) >= messageStartIndex
                        || StringUtils.lastIndexOf(extStackTrace, '\n', stackTraceStartIndex) >= messageStartIndex) {
                    return messageStartIndex;
                } else if (stackTraceStartIndex != -1) {
                    messageStartIndex = getNextNestedMessageIndex(extStackTrace, stackTraceStartIndex);
                }
            }
        } while (messageStartIndex != -1 && stackTraceStartIndex != -1);
        return -1;
    }

    private int getNextNestedMessageIndex(String extStackTrace, int stackTraceStartIndex) {
        return getNextIndex(extStackTrace.indexOf("Caused by:", stackTraceStartIndex),
                extStackTrace.indexOf("Suppressed:", stackTraceStartIndex));
    }

    private int getNextNestedMessageIndex(StringBuilder extStackTrace, int stackTraceStartIndex) {
        return getNextIndex(extStackTrace.indexOf("Caused by:", stackTraceStartIndex),
                extStackTrace.indexOf("Suppressed:", stackTraceStartIndex));
    }

    private int getNextIndex(int firstIndex, int secondIndex) {
        if (secondIndex == -1) {
            return firstIndex;
        } else if (firstIndex == -1) {
            return secondIndex;
        } else {
            return Math.min(firstIndex, secondIndex);
        }
    }

    private String replaceLineSeparatorInMessages(String extStackTrace, int messageWithCRLFIndex) {
        final StringBuilder sb = new StringBuilder(extStackTrace);
        do {
            int beginningOfStackTrace = getNextIndex(sb.indexOf(STACKTRACE_LINE_START, messageWithCRLFIndex),
                    sb.indexOf(NESTED_STACKTRACE_LINE_START, messageWithCRLFIndex));
            replaceCRLF(sb, messageWithCRLFIndex, beginningOfStackTrace);
            messageWithCRLFIndex = indexOfNestedExceptionMessageWithCRLF(sb, beginningOfStackTrace);
        } while (messageWithCRLFIndex != -1);
        return sb.toString();
    }

    private int indexOfNestedExceptionMessageWithCRLF(StringBuilder extStackTrace, int startIndex) {
        int stackTraceStartIndex;
        do {
            startIndex = getNextNestedMessageIndex(extStackTrace, startIndex);
            if (startIndex == -1) {
                return startIndex;
            }
            stackTraceStartIndex = getNextIndex(extStackTrace.indexOf(STACKTRACE_LINE_START, startIndex),
                    extStackTrace.indexOf(NESTED_STACKTRACE_LINE_START, startIndex));
            if (stackTraceStartIndex != -1) {
                stackTraceStartIndex--;
                if (StringUtils.lastIndexOf(extStackTrace, '\r', stackTraceStartIndex) >= startIndex
                        || StringUtils.lastIndexOf(extStackTrace, '\n', stackTraceStartIndex) >= startIndex) {
                    return startIndex;
                }
            }
            startIndex = stackTraceStartIndex;
        } while (startIndex != -1 && stackTraceStartIndex != -1);
        return -1;
    }

    private StringBuilder replaceCRLF(StringBuilder toAppendTo, int start, int end) {
        for (int i = end - 1; i >= start; i--) {
            final char c = toAppendTo.charAt(i);
            switch (c) {
                case '\r':
                    toAppendTo.setCharAt(i, '\\');
                    toAppendTo.insert(i + 1, 'r');
                    break;
                case '\n':
                    toAppendTo.setCharAt(i, '\\');
                    toAppendTo.insert(i + 1, 'n');
                    break;
                default:
                    break;
            }
        }
        return toAppendTo;
    }

}

package com.illcode.hl7;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.TreeMap;

/**
 * An instance of {@code HL7Params} stores the settings used to encode/decode HL7v2
 * messages. These are, basically, the field separator, encoding characters, and
 * character set found in the <tt>MSH</tt> segment.
 */
public final class HL7Params
{
    public final char fieldSeparator;
    public final char componentSeparator;
    public final char subcomponentSeparator;
    public final char repetitionSeparator;
    public final char escapeChar;

    /**
     * An array of the separators in "descending" order of a parse tree for field data:<br>
     * i.e. <tt>[repetitionSeparator, componentSeparator, subcomponentSeparator]</tt>
     */
    public char[] fieldValueSeparators;

    /**
     * A map from each special character to the appropriate escape sequence. For default
     * separator and escape characters, this map's contents are:
     <pre>{@code
    | -> \F\
    ^ -> \S\
    & -> \T\
    ~ -> \R\
    \ -> \E\
     }</pre>
     */
    private Map<Character,String> escapeSequenceMap;

    public HL7Params() {
        fieldSeparator = '|';
        componentSeparator = '^';
        subcomponentSeparator = '&';
        repetitionSeparator = '~';
        escapeChar = '\\';
        initState();
    }

    public HL7Params(char fieldSeparator, char componentSeparator, char subcomponentSeparator,
                     char repetitionSeparator, char escapeChar) {
        this.fieldSeparator = fieldSeparator;
        this.componentSeparator = componentSeparator;
        this.subcomponentSeparator = subcomponentSeparator;
        this.repetitionSeparator = repetitionSeparator;
        this.escapeChar = escapeChar;
        initState();
    }

    public HL7Params(String MSHText) {
        if (MSHText.length() >= 8 && MSHText.startsWith("MSH")) {
            fieldSeparator = MSHText.charAt(3);
            componentSeparator = MSHText.charAt(4);
            repetitionSeparator = MSHText.charAt(5);
            escapeChar = MSHText.charAt(6);
            subcomponentSeparator = MSHText.charAt(7);
        } else {
            fieldSeparator = '|';
            componentSeparator = '^';
            subcomponentSeparator = '&';
            repetitionSeparator = '~';
            escapeChar = '\\';
        }
        initState();
    }

    private void initState() {
        fieldValueSeparators = new char[] {repetitionSeparator, componentSeparator, subcomponentSeparator};

        escapeSequenceMap = new TreeMap<>();
        escapeSequenceMap.put(fieldSeparator, escapeChar + "F" + escapeChar);
        escapeSequenceMap.put(componentSeparator, escapeChar + "S" + escapeChar);
        escapeSequenceMap.put(subcomponentSeparator, escapeChar + "T" + escapeChar);
        escapeSequenceMap.put(repetitionSeparator, escapeChar + "R" + escapeChar);
        escapeSequenceMap.put(escapeChar, escapeChar + "E" + escapeChar);
    }

    /**
     * Escapes text given the separators and escape character in this <tt>HL7Params</tt> instance.
     * <p/>
     * For instance, given the default HL7 delimeters and escape character,
     * <blockquote>
     *     {@code "It's ~20 lbs & 3 oz" &rarr; "It's \R\20 lbs \T\ 3 oz" }
     * </blockquote>
     * @param s string to escape
     * @return escaped string
     */
    public String escape(String s) {
        return escapeHelper(s, false);
    }

    /**
     * Unescapes text given the separators and escape character in this <tt>HL7Params</tt> instance.
     * <p/>
     * For instance, given the default HL7 delimeters and escape character,
     * <blockquote>
     *     {@code  "It's \R\20 lbs \T\ 3 oz" &rarr; "It's ~20 lbs & 3 oz" }
     * </blockquote>
     * @param s string to unescape
     * @return unescaped string
     */
    public String unescape(String s) {
        return escapeHelper(s, true);
    }

    private String escapeHelper(String s, boolean unescape) {
        final String[] chars = new String[escapeSequenceMap.size()];
        final String[] replacements = new String[chars.length];
        int idx = 0;
        for (Map.Entry<Character,String> entry : escapeSequenceMap.entrySet()) {
            chars[idx] = entry.getKey().toString();
            replacements[idx] = entry.getValue();
            idx++;
        }
        return unescape ? StringUtils.replaceEach(s, replacements, chars)
                        : StringUtils.replaceEach(s, chars, replacements);
    }
}

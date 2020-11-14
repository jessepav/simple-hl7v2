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
     * {@code escapeCharArray} and {@code escapeSequenceArray} are
     * a pair of lists that, for each index, indicate the mapping from a special character to the
     * appropriate escape sequence. For default separator and escape characters, these lists' contents
     * are (taking liberties with backslash escaping):
     <pre>{@code
        escapeCharArray =  ["|",   "^",   "&",   "~",   "\",   "\r",    "\n"  ]
    escapeSequenceArray =  ["\F\", "\S\", "\T\", "\R\", "\E\", "\X0D\", "\X0A\"]
     }</pre>
     */
    private String[] escapeCharArray;

    /** See {@link #escapeCharArray} */
    private String[] escapeSequenceArray;

    /**
     * Construct an HL7Params using the default separators and escape character.
     */
    public HL7Params() {
        fieldSeparator = '|';
        componentSeparator = '^';
        subcomponentSeparator = '&';
        repetitionSeparator = '~';
        escapeChar = '\\';
        initState();
    }

    /**
     * Construct an HL7Params using the given separators and escape character.
     */
    public HL7Params(char fieldSeparator, char componentSeparator, char subcomponentSeparator,
                     char repetitionSeparator, char escapeChar) {
        this.fieldSeparator = fieldSeparator;
        this.componentSeparator = componentSeparator;
        this.subcomponentSeparator = subcomponentSeparator;
        this.repetitionSeparator = repetitionSeparator;
        this.escapeChar = escapeChar;
        initState();
    }

    /**
     * Construct an HL7Params using the separators and escape character found
     * in the MSH segment text given as an argument.
     */
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
        escapeCharArray = new String[]
            {Character.toString(fieldSeparator), Character.toString(componentSeparator),
             Character.toString(subcomponentSeparator), Character.toString(repetitionSeparator),
             Character.toString(escapeChar), "\r", "\n"};
        escapeSequenceArray = new String[]
            {escapeChar + "F" + escapeChar, escapeChar + "S" + escapeChar, escapeChar + "T" + escapeChar,
             escapeChar + "R" + escapeChar, escapeChar + "E" + escapeChar,
             escapeChar + "X0D" + escapeChar, escapeChar + "X0A" + escapeChar};
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
        return StringUtils.replaceEach(s, escapeCharArray, escapeSequenceArray);
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
        return StringUtils.replaceEach(s, escapeSequenceArray, escapeCharArray);
    }
}

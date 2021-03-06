package com.illcode.hl7;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.illcode.hl7.Segment.FieldValue;

/**
 * Parses HL7v2 text into an instance of {@link Message}.
 */
public final class HL7Parser
{
    // Used with parseFieldText()
    private static final int LEVEL_FIELD = 0;
    private static final int LEVEL_COMPONENT = 1;
    private static final int LEVEL_SUBCOMPONENT = 2;

    private HL7Params params;

    /**
     * Constuct an HL7Parser with default parameters.
     */
    public HL7Parser() {
        this.params = new HL7Params();
    }

    /**
     * Constuct an HL7Parser with the given parameters.
     */
    public HL7Parser(HL7Params params) {
        this.params = params;
    }

    /**
     * Constuct an HL7Parser with parameters derived from the given HL7 message.
     * This message must begin with an <tt>MSH</tt> segment, so that the delimeters
     * and escape character can be derived.
     */
    public HL7Parser(String hl7msg) {
        this.params = new HL7Params(hl7msg);
    }

    /** Return the parameters being used by this HL7Parser */
    public HL7Params getParams() {
        return params;
    }

    /** Set the parameters to be used by this HL7Parser */
    public void setParams(HL7Params params) {
        this.params = params;
    }

    /**
     * Parse text as a <em>single</em> HL7 message.
     * @param hl7msg HL7v2 text to parse
     * @return new Message instance
     */
    public Message parse(String hl7msg) {
        Message m = new Message();
        final String[] lines = StringUtils.split(hl7msg, "\r\n");
        for (String line : lines) {
            if (line.length() < 5 || line.charAt(3) != params.fieldSeparator)  // malformed
                continue;
            final String segmentId = line.substring(0, 3);
            List<List<FieldValue>> fieldValues;
            String[] fields = StringUtils.splitPreserveAllTokens(line.substring(4), params.fieldSeparator);
            int fieldOffset = 0;
            if (segmentId.equals("MSH")) {
                // MSH.1 & MSH.2 are special
                fieldValues = new ArrayList<>(fields.length + 1); // MSH.1 exists outside the normal scheme of things
                fieldValues.add(Arrays.asList(new FieldValue[] {new FieldValue(Character.toString(params.fieldSeparator))}));
                fieldValues.add(Arrays.asList(new FieldValue[] {new FieldValue(fields[0])}));  // add the delimeters verbatim
                fieldOffset = 1;
            } else {
                fieldValues = new ArrayList<>(fields.length);
            }
            while (fieldOffset < fields.length)
                fieldValues.add(parseFieldText(fields[fieldOffset++]));
            m.putSegment(new Segment(segmentId, fieldValues));
        }
        return m;
    }

    /** Parse HL7v2 text from a Reader */
    public Message parse(Reader r) {
        return parse(slurpReaderText(r, 2048).trim());
    }

    /** Parse HL7v2 text from a Path */
    public Message parse(Path p) throws IOException {
        final Reader r = new InputStreamReader(Files.newInputStream(p), StandardCharsets.UTF_8);
        return parse(r);
    }

    private List<FieldValue> parseFieldText(String text) {
        // It is a space-saving optimization to use null for the reptition list to
        // indicate a blank field value, rather than having a one-element list with
        // a null-value FieldValue.
        if (text.isEmpty())
            return null;

        String[] reps = StringUtils.split(text, params.repetitionSeparator);
        final List<FieldValue> repList = new ArrayList<>(reps.length);
        for (String s : reps)
            repList.add(parseFieldTextHelper(s, LEVEL_FIELD));

        return repList;
    }

    private FieldValue parseFieldTextHelper(String text, int fieldLevel) {
        if (text.isEmpty())
            return new FieldValue();  // null-value
        else if (text.equals("\"\""))
            return new FieldValue(""); // present but empty
        if (fieldLevel == LEVEL_SUBCOMPONENT)  // we cannot go any deeper
            return new FieldValue(params.unescape(text));
        final char separator = fieldLevel == LEVEL_FIELD ? params.componentSeparator : params.subcomponentSeparator;
        String[] parts = StringUtils.splitPreserveAllTokens(text, separator);
        if (parts.length == 1) {  // no children
            // I've seen it in the wild that a field will have subcomponent separators without any component
            // separators, so we'll need to take that possibility into account.
            if (fieldLevel == LEVEL_FIELD && parts[0].indexOf(params.subcomponentSeparator) != -1)
                return new FieldValue(Arrays.asList(new FieldValue[] {parseFieldTextHelper(parts[0], LEVEL_COMPONENT)}));
            else
                return new FieldValue(params.unescape(parts[0]));
        } else {
            final List<FieldValue> values = new ArrayList<>(parts.length);
            for (String part : parts)
                values.add(parseFieldTextHelper(part, fieldLevel + 1));
            return new FieldValue(values);
        }
    }

    /**
     * Read all characters available from a Reader and return them as a string.
     * We use a BufferedReader internally to make the process more efficient.
     * @param r Reader from which to read characters
     * @param bufferSize buffer size for our BufferedReader
     * @return String read from the reader, or null if an exception occurred
     */
    public static String slurpReaderText(Reader r, int bufferSize) {
        String s = null;
        try (BufferedReader reader = new BufferedReader(r)) {
            char [] buffer = new char[bufferSize];
            StringBuilder sb = new StringBuilder(5*bufferSize);
            int n;
            while ((n = reader.read(buffer, 0, buffer.length)) != -1)
                sb.append(buffer, 0, n);
            s = sb.toString();
        } catch (IOException ex) {
            s = null;
        }
        return s;
    }
}

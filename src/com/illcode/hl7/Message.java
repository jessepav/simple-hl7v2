package com.illcode.hl7;

import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * An HL7v2 message. A message is represent as a map from a segment ID to a list of {@link Segment}S
 * (with entries in the list being the repetitions of segments in the message).
 */
public final class Message
{
    // Segment ID -> repetitions of segment
    final Map<String,List<Segment>> segmentMap;

    /** Construct an empty message. */
    public Message() {
        segmentMap = new LinkedHashMap<>();
    }

    /** Return a set of all the segment IDs in the message. */
    public Set<String> getSegmentIds() {
        return segmentMap.keySet();
    }

    /** Return true if a segment with the given ID is in the message. */
    public boolean containsSegment(String id) {
        return segmentMap.containsKey(id);
    }

    /**
     * Return a collection of all the repetitions of segments in the message.
     * <p>
     * Note that the iteration order of the returned collection is the order in which the
     * segment lists were added to the message, so to get a well-formed HL7 message, you'll
     * either need to:
     * <ol>
     *     <li>Insert segments in the correct order (i.e. MSH first, etc.), or</li>
     *     <li>Pick out the segments you need in the appropriate order using {@link #getSegment(String)}
     *         or {@link #getSegment(String, int)} and encode them individually</li>
     * </ol>
     * </p>
     */
    public Collection<List<Segment>> segments() {
        return segmentMap.values();
    }

    /**
     * Return a list of repetitions for a given segment ID, or null if no segment with
     * that ID is in the message.
     */
    public List<Segment> getSegments(String id) {
        return segmentMap.get(id);
    }

    /**
     * Return a given reptition of segments with the given ID, or
     * null if no such segment is in the Message.
     */
    public Segment getSegment(String id, int repetition) {
        final List<Segment> l = segmentMap.get(id);
        if (l != null && l.size() >= repetition)
            return l.get(repetition - 1);
        else
            return null;
    }

    /**
     * Return the first segment (among any repetitions) with the given ID, or
     * null if no such segment is in the Message.
     */
    public Segment getSegment(String id) {
        final List<Segment> l = segmentMap.get(id);
        if (l != null && !l.isEmpty())
            return l.get(0);
        else
            return null;
    }

    /**
     * Put a segment into the message. If a segment with the same ID already exists,
     * the new segment is added as a repetition.
     */
    public void putSegment(Segment s) {
        List<Segment> l = segmentMap.get(s.getId());
        if (l == null) {
            l = new ArrayList<>(3);
            segmentMap.put(s.getId(), l);
        }
        l.add(s);
    }

    /**
     * Remove a segment from the message.
     */
    public void removeSegment(Segment s) {
        List<Segment> l = segmentMap.get(s.getId());
        if (l != null)
            l.remove(s);
    }

    /**
     * Returns the message type found in {@code MSH.9}, if present, or null otherwise.
     * <p/>
     * Example: <tt>"QRY^A19"</tt>
     */
    public String getMessageType() {
        final Segment MSH = getSegment("MSH");
        if (MSH == null)
            return null;
        String type = MSH.getFieldValue(9, 1);
        String event = MSH.getFieldValue(9, 2);
        if (type == null || event == null)
            return null;
        return type + "^" + event;
    }

    /**
     * Returns the string value of a segment field given a path-like spec string as input.
     * The forms that this spec string may take are described below. <em>SID</em> is
     * the three-character segment ID, and <em>F, R, C,</em> and <em>S</em> are positive integers.
     * <ol>
     *     <li><tt>SID.F</tt> &mdash; field <em>F</em> of segment <em>SID</em> </li>
     *     <li><tt>SID.F.C</tt> &mdash; component <em>C</em> of field <em>F</em> of segment <em>SID</em> </li>
     *     <li><tt>SID.F.C.S</tt> &mdash; subcomponent <em>S</em> of component <em>C</em> of
     *              field <em>F</em> of segment <em>SID</em> </li>
     * </ol>
     * Each of these forms has a variant where the field number <em>F</em> is given in the format
     * <tt>"F(R)"</tt>, where <em>R</em> is the repetition number (starting at 1) for a given field.
     * <p>
     * Examples:
     * <blockquote>
     *     <tt>"MSH.9.1"</tt> - Message Type <br>
     *     <tt>"PID.3(2).1</tt> - The ID portion (i.e. PID.3.1) of the 2nd repetition in the PID.3 field.
     * </blockquote>
     * Likewise, for segments that can repeat in a message, the SID can be written <tt>SID(R)</tt> to get
     * the Rth repetition. Ex: <tt>"NK1(2).2.1"</tt> for the Family Name of the second instance of the NK1
     * segment.
     * <p>
     * If the repetition number is not specified for segment IDs or fields, it defaults to 1.
     * </p>
     * <h3>HAPI Terser Compatibility Syntax</h3>
     * <p>
     *     To be partially compatible with the syntax for HAPI's
     *     <a href="https://hapifhir.github.io/hapi-hl7v2/base/apidocs/ca/uhn/hl7v2/util/Terser.html">Terser</a>
     *     class, you can use a dash <tt>"-"</tt> in place of the periods <tt>"."</tt> in the above format. Also, if the
     *     spec string starts with a slash <tt>"/"</tt>, it will be silently discarded.
     * </p>
     * @param spec spec string, as above
     * @return the field value or the empty string "" if such a field is not present in the message
     */
    public String getField(String spec) {
        FieldValuePath path = parseSpec(spec);
        if (path == null)
            return "";
        Segment seg = getSegment(path.segmentId, path.segmentRep);
        if (seg == null)
            return "";
        return seg.getFieldValue(path.field, path.fieldRep, path.component, path.subcomponent);
    }

    /**
     * Sets a field of a segment in the message.
     * @param spec spec string as described in the docs for {@link #getField}
     * @param value scalar value to assign to the field
     */
    public void setField(String spec, String value) {
        FieldValuePath path = parseSpec(spec);
        if (path == null)
            return;
        Segment seg = getSegment(path.segmentId, path.segmentRep);
        if (seg == null)
            return;
        seg.setFieldValue(path.field, path.fieldRep, path.component, path.subcomponent, value);
    }

    private FieldValuePath parseSpec(String spec) {
        try {
            if (spec.startsWith("/"))
                spec = spec.substring(1);
            if (spec.length() < 5)
                return null;
            final String[] parts = StringUtils.splitPreserveAllTokens(spec, ".-");
            if (parts.length < 2 || parts.length > 4)
                return null;

            int segmentRep = 1, fieldRep = 1;
            int field, component = 0, subcomponent = 0;

            final String segmentId = parts[0].substring(0, 3);
            int idx = parts[0].indexOf('(');
            if (idx != -1) {
                final int lastChar = parts[0].length() - 1;
                if (parts[0].charAt(lastChar) == ')')
                    segmentRep = Integer.parseInt(parts[0].substring(idx + 1, lastChar));
                else
                    return null;
            }
            idx = parts[1].indexOf('(');
            if (idx != -1) {
                final int lastChar = parts[1].length() - 1;
                if (parts[1].charAt(lastChar) == ')') {
                    field = Integer.parseInt(parts[1].substring(0, idx));
                    fieldRep = Integer.parseInt(parts[1].substring(idx + 1, lastChar));
                } else {
                    return null;
                }
            } else {
                field = Integer.parseInt(parts[1]);
            }
            if (parts.length > 2)
                component = Integer.parseInt(parts[2]);
            if (parts.length > 3)
                subcomponent = Integer.parseInt(parts[3]);
            return new FieldValuePath(segmentId, segmentRep, field, fieldRep, component, subcomponent);
        } catch (NumberFormatException|IndexOutOfBoundsException ex) {
            return null;
        }
    }

    /**
     * Create and return a new Message that is a deep copy of the given message.
     */
    public static Message copyOf(Message m) {
        Message copy = new Message();
        for (List<Segment> segs : m.segments()) {
            for (Segment s : segs)
                copy.putSegment(Segment.copyOf(s));
        }
        return copy;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(512);
        for (List<Segment> l : segmentMap.values()) {
            for (Segment s : l)
                sb.append(s.toString());
        }
        return sb.toString();
    }

    // For use with parseSpec()
    private static class FieldValuePath
    {
        String segmentId;
        int segmentRep;
        int field;
        int fieldRep;
        int component;
        int subcomponent;

        private FieldValuePath(String segmentId, int segmentRep, int field, int fieldRep, int component, int subcomponent) {
            this.segmentId = segmentId;
            this.segmentRep = segmentRep;
            this.field = field;
            this.fieldRep = fieldRep;
            this.component = component;
            this.subcomponent = subcomponent;
        }
    }
}

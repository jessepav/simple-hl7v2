package com.illcode.hl7;

import java.util.Collection;
import java.util.List;

import static com.illcode.hl7.Segment.FieldValue;
import static org.apache.commons.lang3.StringUtils.defaultString;

/**
 * Encodes an instance of {@link Message} into HL7v2 text.
 */
public final class HL7Encoder
{
    private HL7Params params;

    /**
     * Construct an HL7Encoder with default params.
     */
    public HL7Encoder() {
        params = new HL7Params();
    }

    /**
     * Construct an HL7Encoder with the given params.
     */
    public HL7Encoder(HL7Params params) {
        this.params = params;
    }

    /** Return the parameters being used by this HL7Encoder */
    public HL7Params getParams() {
        return params;
    }

    /** Set the parameters to be used by this HL7Encoder */
    public void setParams(HL7Params params) {
        this.params = params;
    }

    /**
     * Encode a Message into HL7v2 format.
     * <p>
     * Note that for the resulting HL7 to be valid, the segments need to be added to the
     * Message instance in the correct order, since Message iterates through its segments
     * in the order of insertion.
     * @param m Message to encode
     * @return HL7v2 text
     */
    public String encode(Message m) {
        final Collection<List<Segment>> segments = m.segmentMap.values();
        final StringBuilder sb = new StringBuilder(segments.size() * 100);
        for (List<Segment> segReps : segments) {
            for (Segment s : segReps)
                encodeHelper(sb, s);
        }
        return sb.toString();
    }

    /**
     * Encode a segment into HL7v2 format
     * @param s Segment to encode
     * @return HL7v2 text (including the carriage-return '\r' at the end)
     */
    public String encode(Segment s) {
        final StringBuilder sb = new StringBuilder(100);
        encodeHelper(sb, s);
        return sb.toString();
    }

    private void encodeHelper(StringBuilder sb, Segment s) {
        sb.append(s.id);
        final int numFields = s.fieldValues.size();
        int fieldNo = 0;
        if (s.id.equals("MSH")) {
            // output MSH.1 and MSH.2 specially
            sb.append(params.fieldSeparator);
            sb.append(params.componentSeparator).append(params.repetitionSeparator)
              .append(params.escapeChar).append(params.subcomponentSeparator);
            fieldNo = 2;
        }
        for (; fieldNo < numFields; fieldNo++) {
            sb.append(params.fieldSeparator);
            List<FieldValue> repList = s.fieldValues.get(fieldNo);
            encodeFieldValues(sb, repList, 0);
        }
        sb.append('\r');
    }

    private void encodeFieldValues(StringBuilder sb, List<FieldValue> values, int level) {
        if (values == null || level >= params.fieldValueSeparators.length)
            return;
        boolean first = true;
        for (FieldValue v : values) {
            if (first) first = false;
            else sb.append(params.fieldValueSeparators[level]);
            if (v.isScalar()) {
                if (v.value != null) {
                    if (v.value.isEmpty())
                        sb.append("\"\"");
                    else
                        sb.append(params.escape(v.value));
                }
            } else {
                encodeFieldValues(sb, v.children, level + 1);
            }
        }
    }
}

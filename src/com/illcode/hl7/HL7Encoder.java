package com.illcode.hl7;

import org.apache.commons.lang3.StringUtils;

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

    public HL7Encoder() {
        params = new HL7Params();
    }

    public HL7Encoder(HL7Params params) {
        this.params = params;
    }

    public HL7Params getParams() {
        return params;
    }

    public void setParams(HL7Params params) {
        this.params = params;
    }

    public String encode(Message m) {
        final Collection<List<Segment>> segments = m.segmentMap.values();
        StringBuilder sb = new StringBuilder(segments.size() * 100);
        for (List<Segment> segReps : segments) {
            for (Segment s : segReps) {
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
                    encodeHelper(sb, repList, 0);
                }
                sb.append('\r');
            }
        }
        return sb.toString();
    }

    private void encodeHelper(StringBuilder sb, List<FieldValue> values, int level) {
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
                encodeHelper(sb, v.children, level + 1);
            }
        }
    }
}

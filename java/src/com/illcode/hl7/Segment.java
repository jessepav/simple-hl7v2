package com.illcode.hl7;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.defaultString;

/**
 * Field data in a single HL7v2 segment.
 */
public final class Segment
{
    String id;
    // field # -> repetition # -> FieldValue
    List<List<FieldValue>> fieldValues;

    /**
     * Construct an empty segment with the given ID.
     */
    public Segment(String id) {
        this.id = id;
        this.fieldValues = new ArrayList<>();
    }

    /**
     * Construct a segment with the given ID and list of field values.
     * @param id segment ID
     * @param fieldValues a list of list of field values. The outer list index represents the field #,
     *          and the inner list index represents the field repetitions.
     */
    public Segment(String id, List<List<FieldValue>> fieldValues) {
        this.id = id;
        this.fieldValues = fieldValues;
    }

    /**
     * Construct an empty segment with a hint about its final size.
     * @param id segment ID
     * @param sizeHint hint as to the total number of fields for which we should allocate our
     *          internal data structures
     */
    public Segment(String id, int sizeHint) {
        this.id = id;
        this.fieldValues = new ArrayList<>(sizeHint);
    }

    /**
     * Get the ID of this segment (ex. "MSH")
     */
    public String getId() {
        return id;
    }

    /**
     * Return the string value of a given field/component/subcomponent, or the empty string (<tt>""</tt>) if
     * the requested value does not exist, or is a null value. Requesting the first child of a scalar parent
     * will return the value of the parent. Requesting the value of a parent that is a composite will return
     * the value of its first child.
     * <p/>
     * All parameters represent indices that start at 1.
     * @param fieldNo field number {@code (>= 1)}
     * @param repetition the repetition number, for fields that have repeated values {@code (>= 1)}
     * @param componentNo component number (or 0, to indicate no component)
     * @param subcomponentNo subcomponent number (or 0, to indicate no subcomponent)
     * @return value of field/repetition/component/subcomponent, or empty string
     */
    public String getFieldValue(int fieldNo, int repetition, int componentNo, int subcomponentNo) {
        if (fieldValues.size() < fieldNo)
            return "";
        final List<FieldValue> repetitionList = fieldValues.get(fieldNo - 1);
        if (repetitionList == null || repetitionList.size() < repetition)
            return "";
        final FieldValue fieldVal = repetitionList.get(repetition - 1);
        if (componentNo == 0) {
            return leftmostScalar(fieldVal);
        } else {
            if (fieldVal.isScalar()) {
                // This is the case where we request a component, and perhaps subcomponent, of index 1,
                // but the field value is a scalar; and so we just return the field value.
                if (componentNo == 1 && (subcomponentNo == 1 || subcomponentNo == 0))
                    return defaultString(fieldVal.value);
                else
                    return "";  // can't traverse a scalar value
            } else { // now delve into the component list
                if (fieldVal.children.size() < componentNo)
                    return "";
                final FieldValue componentVal = fieldVal.children.get(componentNo - 1);
                if (subcomponentNo == 0) {
                    return leftmostScalar(componentVal);
                } else {
                    if (componentVal.isScalar()) {
                        if (subcomponentNo == 1)
                            return defaultString(componentVal.value);
                        else
                            return "";
                    } else {
                        if (componentVal.children.size() < subcomponentNo)
                            return "";
                        return defaultString(componentVal.children.get(subcomponentNo - 1).value);
                    }
                }
            }
        }
    }

    private String leftmostScalar(FieldValue v) {
        while (true) {
            if (v.isScalar())
                return defaultString(v.value);
            else if (!v.children.isEmpty())
                v = v.children.get(0);
            else
                return "";
        }
    }

    /**
     * Equivalent to {@link #getFieldValue(int, int, int, int) getFieldValue(fieldNo, 1, 0, 0)}.
     */
    public String getFieldValue(int fieldNo) {
        return getFieldValue(fieldNo, 1, 0, 0);
    }

    /**
     * Equivalent to {@link #getFieldValue(int, int, int, int) getFieldValue(fieldNo, 1, componentNo, 0)}.
     */
    public String getFieldValue(int fieldNo, int componentNo) {
        return getFieldValue(fieldNo, 1, componentNo, 0);
    }

    /**
     * Equivalent to {@link #getFieldValue(int, int, int, int) getFieldValue(fieldNo, 1, componentNo, subcomponentNo)}.
     */
    public String getFieldValue(int fieldNo, int componentNo, int subcomponentNo) {
        return getFieldValue(fieldNo, 1, componentNo, subcomponentNo);
    }

    /**
     * Add or replace values for a field.
     * @param fieldNo field number (>= 1)
     * @param repeatedFieldValues list of repetitions of field values; a null value clears
     *              the field
     */
    public void putFieldValues(int fieldNo, List<FieldValue> repeatedFieldValues) {
        ensureCapacity(fieldNo);
        fieldValues.set(fieldNo - 1, repeatedFieldValues);
    }

    /**
     * Clear any values from a given field
     * @param fieldNo field number (>= 1)
     */
    public void clearFieldValues(int fieldNo) {
        if (fieldValues.size() >= fieldNo)
            fieldValues.set(fieldNo - 1, null);
    }

    /**
     * Add a value to a given field. If a value is already present in the specified field,
     * the new value will be added as a repetition.
     * @param fieldNo field number (>= 1)
     * @param v field value to add
     */
    public void addFieldValue(int fieldNo, FieldValue v) {
        ensureCapacity(fieldNo);
        List<FieldValue> l = fieldValues.get(fieldNo - 1);
        if (l == null) {
            l = new LinkedList<>();
            fieldValues.set(fieldNo - 1, l);
        }
        l.add(v);
    }

    /**
     * Add a scalar value to a given field. If a value is already present in the specified field,
     * the new value will be added as a repetition.
     * @param fieldNo field number (>= 1)
     * @param value scalar field value to add
     */
    public void addFieldValue(int fieldNo, String value) {
        addFieldValue(fieldNo, FieldValue.scalar(value));
    }

    /**
     * Set the scalar value for a specified field/repetition/component/subcomponent. Any existing value
     * will be replaced, and if a scalar value exists at a point where a composite value is needed, the
     * scalar will be replaced (for instance, if you set the value for "PID.3.2" but "PID.3" is a scalar,
     * the scalar value will be replaced by a composite with a null "PID.3.1" and the given value of
     * "PID.3.2").
     * @param fieldNo field number {@code (>= 1)}
     * @param repetition the repetition number, for fields that have repeated values {@code (>= 1)}
     * @param componentNo component number (or 0, to indicate no component)
     * @param subcomponentNo subcomponent number (or 0, to indicate no subcomponent)
     * @param value scalar value to set
     */
    public void setFieldValue(int fieldNo, int repetition, int componentNo, int subcomponentNo, String value) {
        ensureCapacity(fieldNo);
        List<FieldValue> reps = fieldValues.get(fieldNo - 1);
        if (reps == null) {
            reps = new LinkedList<>();
            fieldValues.set(fieldNo - 1, reps);
        }
        int numReps = reps.size();
        for (; numReps < repetition; numReps++)
            reps.add(new FieldValue());
        FieldValue v = reps.get(repetition - 1);
        if (componentNo > 0) {
            if (v.isScalar()) {
                v.value = null;
                v.children = new ArrayList<>(componentNo);
            }
            for (int i = v.children.size(); i < componentNo; i++) // ensure sufficient size
                v.children.add(new FieldValue());
            v = v.children.get(componentNo - 1);
            if (subcomponentNo > 0) {
                if (v.isScalar()) {
                    v.value = null;
                    v.children = new ArrayList<>(subcomponentNo);
                }
                for (int i = v.children.size(); i < subcomponentNo; i++)
                    v.children.add(new FieldValue());
                v = v.children.get(subcomponentNo - 1);
            }
        }
        v.value = value;
        v.children = null;  // make it scalar
    }

    /**
     * Equivalent to {@link #setFieldValue(int, int, int, int, String) setFieldValue(fieldNo, 1, 0, 0, value)}.
     */
    public void setFieldValue(int fieldNo, String value) {
        setFieldValue(fieldNo, 1, 0, 0, value);
    }

    /**
     * Equivalent to {@link #setFieldValue(int, int, int, int, String) setFieldValue(fieldNo, 1, componentNo, 0, value)}.
     */
    public void setFieldValue(int fieldNo, int componentNo, String value) {
        setFieldValue(fieldNo, 1, componentNo, 0, value);
    }

    /**
     * Equivalent to {@link #setFieldValue(int, int, int, int, String) setFieldValue(fieldNo, 1, componentNo, subcomponentNo, value)}.
     */
    public void setFieldValue(int fieldNo, int componentNo, int subcomponentNo, String value) {
        setFieldValue(fieldNo, 1, componentNo, subcomponentNo, value);
    }

    private void ensureCapacity(int numFields) {
        int n = fieldValues.size();
        // Expand the array if necessary
        for (; n < numFields; n++)
            fieldValues.add(null);
    }

    /**
     * Create and return a new Segment that is a deep copy of the given segment.
     */
    public static Segment copyOf(Segment orig) {
        Segment copy = new Segment(orig.id, orig.fieldValues.size());
        for (List<FieldValue> reps : orig.fieldValues) {
            if (reps == null) {
                copy.fieldValues.add(null);
            } else {
                List<FieldValue> repsCopy = new ArrayList<>(reps.size());
                for (FieldValue v : reps)
                    repsCopy.add(FieldValue.copyOf(v));
                copy.fieldValues.add(repsCopy);
            }
        }
        return copy;
    }

    public String toString() {
        int field = 1;
        final StringBuilder sb = new StringBuilder(fieldValues.size() * 80);
        for (List<FieldValue> repList : fieldValues) {
            if (repList != null) {
                for (FieldValue v : repList) {
                    String prefix = id + "." + field;
                    toStringHelper(sb, prefix, v);
                }
            }
            field++;
        }
        return sb.toString();
    }

    private void toStringHelper(StringBuilder sb, String prefix, FieldValue v) {
        if (v.isScalar()) {
            if (v.value != null)
                sb.append(prefix).append(": ").append(v.value.isEmpty() ? "\"\"" : v.value).append("\n");
        } else {
            sb.append(prefix).append(":").append("\n");
            int i = 1;
            for (FieldValue child : v.children) {
                toStringHelper(sb, String.format("  %s.%d", prefix, i), child);
                i++;
            }
        }
    }

    /**
     * The value of a field (scalar or composite). The value is either text contained in the node itself (if
     * it is a field with no components, or a component with no subcomponents, or is a subcomponent), or it
     * is the list of the values of its children.
     */
    public static class FieldValue {
        /** Scalar value of this FieldValue. May be null if the FieldValue has a null-value. */
        public String value;

        /** Sub-values of this FieldValue. Null if the value is scalar. */
        public List<FieldValue> children;

        /**
         * Construct a null-value FieldValue
         */
        public FieldValue() {
        }

        /**
         * Construct a scalar FieldValue (no children)
         * @param value textual value, or null.
         */
        public FieldValue(String value) {
            this.value = value;
        }

        /**
         * Construct a composite FieldValue
         * @param children (sub)components of the field value
         */
        public FieldValue(List<FieldValue> children) {
            this.children = children;
        }

        /**
         * Returns true if this is a scalar FieldValue, i.e. that is has no children.
         */
        public boolean isScalar() {
            return children == null;
        }

        /**
         * Returns a new FieldValue that is a deep copy of the given argument.
         */
        public static FieldValue copyOf(FieldValue v) {
            if (v.isScalar()) {
                return new FieldValue(v.value);
            } else {
                List<FieldValue> children = new ArrayList<>(v.children.size());
                for (FieldValue child : v.children)
                    children.add(copyOf(child));
                return new FieldValue(children);
            }
        }

        /**
         * Create and return a scalar {@code FieldValue}, whose String value is given as an argument.
         */
        public static FieldValue scalar(String value) {
            return new FieldValue(value);
        }

        /**
         * Create a composite FieldValue, whose children are passed as arguments.
         * @param vals String arguments will be converted to scalar {@code FieldValue}S,
         *          while {@code FieldValue} arguments will be used as-is. All other types
         *          will be converted to null-value {@code FieldValue}S.
         * @return a new composite FieldValue
         */
        public static FieldValue composite(Object... vals) {
            if (vals == null || vals.length == 0)
                return new FieldValue();
            final List<FieldValue> children = new ArrayList<>(vals.length);
            for (Object o : vals) {
                if (o instanceof String)
                    children.add(new FieldValue((String) o));
                else if (o instanceof FieldValue)
                    children.add((FieldValue) o);
                else
                    children.add(new FieldValue());
            }
            return new FieldValue(children);
        }

        /**
         * Creates and returns a composite FieldValue whose children are specified by the
         * {@code indexedVals} argument as a sequence of <em>index</em>, <em>value</em> pairs. For instance,
         * <pre>{@code
    FieldValue v = indexed(2, "Hello",
                           5, composite("Sub1", "Sub2"));
         * }</pre>
         * yields a composite FieldValue with five children: the second child being a scalar with value "Hello",
         * the fifth child a composite with two children (themselves scalars), and the first, third, and fourth
         * children having null values. A values may be given as a String, in which case it's wrapped into a
         * scalar FieldValue, or as a FieldValue, in which case it's used unchanged.
         * <br>
         * This method is useful for constructing field values to be passed to {@link Segment#addFieldValue} when
         * programmatically assembling HL7 messages.
         * @param indexedVals index,value pairs
         * @return composite FieldValue
         */
        public static FieldValue indexed(Object... indexedVals) {
            if (indexedVals == null || indexedVals.length == 0 || indexedVals.length % 2 != 0)
                return new FieldValue();
            final List<Integer> indices = new ArrayList<>(indexedVals.length / 2);
            final List<FieldValue> values = new ArrayList<>(indexedVals.length / 2);
            for (int i = 0; i < indexedVals.length; i += 2) {
                if (!(indexedVals[i] instanceof Integer))
                    return new FieldValue();
                FieldValue v;
                if (indexedVals[i+1] instanceof FieldValue)
                    v = (FieldValue) indexedVals[i + 1];
                else if (indexedVals[i+1] instanceof String)
                    v = FieldValue.scalar((String) indexedVals[i + 1]);
                else
                    v = new FieldValue();
                indices.add((Integer) indexedVals[i]);
                values.add(v);
            }
            int maxIdx = 0;
            for (int i = 0; i < indices.size(); i++) {
                final int idx = indices.get(i);
                if (idx <= 0)
                    return new FieldValue();
                else if (idx > maxIdx)
                    maxIdx = idx;
            }
            final List<FieldValue> children = new ArrayList<>(maxIdx);
            for (int i = 0; i < maxIdx; i++)  // grow the list to size
                children.add(new FieldValue());
            for (int i = 0; i < indices.size(); i++)
                children.set(indices.get(i) - 1, values.get(i));
            return new FieldValue(children);
        }
    }
}

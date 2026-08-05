/**
 * Simple-HL7v2 &mdash; a simple HL7v2 parsing and encoding library.
 *
 * This module is a port of the Java version of Simple-HL7v2 (`java/src/com/illcode/hl7`),
 * and has no dependencies: it operates purely on strings, so it runs unchanged in Node,
 * Deno, and browsers. File and network transport are up to the caller.
 *
 * ```js
 * import { HL7Parser, HL7Encoder } from './simple_hl7.mjs';
 *
 * const parser = new HL7Parser();
 * const m = parser.parse(hl7text);
 *
 * const type = m.getMessageType();          // ex. "MDM^T02"
 * const ts = m.getField("OBR.7");           // get a field value
 * m.setField("OBR(1).8", ts);               // change a field value
 *
 * const encoder = new HL7Encoder();
 * const updatedHL7text = encoder.encode(m);
 * ```
 *
 * Differences from the Java version:
 *
 * 1. `Segment.FieldValue` is a plain object of the shape `{value, children}` rather than a
 *    class; use the {@link scalar}, {@link composite}, and {@link indexed} factories, and
 *    the {@link isScalar} helper, all of which are module-level exports.
 * 2. Java's overloaded methods are implemented by dispatching on argument count and type;
 *    see the docs of each method for the recognized forms.
 * 3. `HL7Parser` reads only strings &mdash; the Java `parse(Reader)`, `parse(Path)`, and
 *    `slurpReaderText()` methods are not ported.
 * 4. Empty field repetitions are preserved when parsing: `"a~~b"` yields three repetitions,
 *    the middle one being a null value. (The Java version collapses them, which makes
 *    parse/encode round-trips asymmetric.)
 *
 * Simple-HL7v2 is distributed under the MIT license; see `java/LICENSE`.
 *
 * @module simple_hl7
 */

//------------------------------------------------------------------------------
// Internal helpers
//------------------------------------------------------------------------------

/** Escape a string so that it matches itself literally when used in a RegExp. */
function regexEscape(s) {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Build a function that replaces all occurrences of the strings in `searches` with the
 * corresponding strings in `replacements`.
 *
 * This is a port of Commons-Lang's `StringUtils.replaceEach()`: replacement is
 * simultaneous, single-pass, and non-recursive, so replacement text is never rescanned.
 * (Chaining individual replacements would be wrong: unescaping "\E\" to "\" could
 * conjure up an escape sequence that a later pass would then "unescape" again.)
 *
 * At any position the earliest match wins, and among matches at the same position the one
 * listed first wins &mdash; which is exactly how RegExp alternation behaves.
 */
function buildReplacer(searches, replacements) {
    const map = new Map();
    const alternatives = [];
    for (let i = 0; i < searches.length; i++) {
        const search = searches[i];
        if (!search || map.has(search))  // skip empty and duplicate search strings
            continue;
        map.set(search, replacements[i]);
        alternatives.push(regexEscape(search));
    }
    if (alternatives.length === 0)
        return (text) => text;
    const re = new RegExp(alternatives.join('|'), 'g');
    return (text) => text.replace(re, (match) => map.get(match));
}

/**
 * Split HL7 text into segment lines. Like the Java version (which uses
 * `StringUtils.split(text, "\r\n")`) this splits on either line-ending character and
 * discards empty lines, so CR, LF, and CRLF-delimited messages all work.
 */
function splitLines(text) {
    return text.split(/[\r\n]+/).filter((line) => line.length > 0);
}

/**
 * Parse a string of decimal digits, or return NaN. Used in place of JS `parseInt()`, which
 * is far more lenient than the `Integer.parseInt()` of the Java version.
 */
function strictInt(s) {
    return /^\d+$/.test(s) ? Number(s) : NaN;
}

/** Return `s`, or the empty string if `s` is null or undefined. */
function defaultString(s) {
    return s ?? "";
}

//------------------------------------------------------------------------------
// Field values
//------------------------------------------------------------------------------

/**
 * The value of a field (scalar or composite), represented as a plain object with two
 * properties:
 *
 * - `value` &mdash; the textual value of this node, or `null` for a null value.
 * - `children` &mdash; an array of the (sub)component values of this node, or `null` if
 *   the value is scalar.
 *
 * A value is either text contained in the node itself (if it is a field with no components,
 * or a component with no subcomponents, or is a subcomponent), or it is the list of the
 * values of its children.
 *
 * @typedef {{value: ?string, children: ?Array<FieldValue>}} FieldValue
 */

/**
 * Create a field value with the given text and children. Prefer the more specific
 * {@link scalar}, {@link composite}, {@link indexed}, and {@link nullValue} factories.
 * @param {?string} [value] textual value, or null
 * @param {?Array<FieldValue>} [children] child values, or null if the value is scalar
 * @returns {FieldValue}
 */
export function fieldValue(value = null, children = null) {
    return {value, children};
}

/**
 * Create a null-value (i.e. absent) field value.
 * @returns {FieldValue}
 */
export function nullValue() {
    return {value: null, children: null};
}

/**
 * Create a scalar field value (i.e. one with no children) with the given text.
 * @param {?string} value textual value, or null
 * @returns {FieldValue}
 */
export function scalar(value) {
    return {value: value ?? null, children: null};
}

/**
 * Create a composite field value, whose children are passed as arguments.
 *
 * ```js
 * composite("DOE", "JOHN")
 * ```
 * @param {...(string|FieldValue|*)} vals string arguments will be converted to scalar field
 *          values, while field-value arguments will be used as-is. All other values
 *          (including `null`) will be converted to null values.
 * @returns {FieldValue} a new composite field value
 */
export function composite(...vals) {
    if (vals.length === 0)
        return nullValue();
    const children = vals.map(toFieldValue);
    return {value: null, children};
}

/**
 * Create a composite field value whose children are specified as a sequence of
 * <em>index</em>, <em>value</em> pairs. For instance,
 *
 * ```js
 * const v = indexed(2, "Hello",
 *                   5, composite("Sub1", "Sub2"));
 * ```
 *
 * yields a composite field value with five children: the second child being a scalar with
 * value "Hello", the fifth child a composite with two children (themselves scalars), and
 * the first, third, and fourth children having null values. A value may be given as a
 * string, in which case it's wrapped into a scalar field value, or as a field value, in
 * which case it's used unchanged.
 *
 * This function is useful for constructing field values to be passed to
 * {@link Segment#addFieldValue} when programmatically assembling HL7 messages.
 *
 * @param {...*} indexedVals index, value pairs
 * @returns {FieldValue} composite field value, or a null value if the arguments are not
 *          well-formed (an odd number of arguments, or an index that isn't a positive
 *          integer)
 */
export function indexed(...indexedVals) {
    if (indexedVals.length === 0 || indexedVals.length % 2 !== 0)
        return nullValue();
    const indices = [];
    const values = [];
    for (let i = 0; i < indexedVals.length; i += 2) {
        const idx = indexedVals[i];
        if (!Number.isInteger(idx) || idx <= 0)
            return nullValue();
        indices.push(idx);
        values.push(toFieldValue(indexedVals[i + 1]));
    }
    const maxIdx = Math.max(...indices);
    const children = [];
    for (let i = 0; i < maxIdx; i++)  // grow the list to size
        children.push(nullValue());
    for (let i = 0; i < indices.length; i++)
        children[indices[i] - 1] = values[i];
    return {value: null, children};
}

/**
 * Returns true if the given field value is scalar, i.e. if it has no children.
 * @param {FieldValue} v
 * @returns {boolean}
 */
export function isScalar(v) {
    return v.children == null;
}

/**
 * Returns a new field value that is a deep copy of the given argument.
 * @param {FieldValue} v
 * @returns {FieldValue}
 */
export function copyFieldValue(v) {
    if (isScalar(v))
        return scalar(v.value);
    else
        return {value: null, children: v.children.map(copyFieldValue)};
}

/**
 * Returns true if `x` looks like a {@link FieldValue} object. Used where the Java version
 * relies on overload resolution between `FieldValue` and `String` parameters.
 */
function isFieldValue(x) {
    return x !== null && typeof x === 'object' && !Array.isArray(x);
}

/** Coerce an argument of `composite()`/`indexed()`/`addFieldValue()` to a field value. */
function toFieldValue(o) {
    if (typeof o === 'string')
        return scalar(o);
    else if (isFieldValue(o))
        return o;
    else
        return nullValue();
}

//------------------------------------------------------------------------------
// Segment
//------------------------------------------------------------------------------

/**
 * Field data in a single HL7v2 segment.
 */
export class Segment
{
    /**
     * ID of this segment (ex. "MSH").
     * @type {string}
     */
    id;

    /**
     * Field values of this segment, indexed by field # &minus; 1, then by repetition
     * # &minus; 1. An entry of the outer array may be `null`, indicating that the field is
     * absent from the segment.
     * @type {Array<?Array<FieldValue>>}
     */
    fieldValues;

    /**
     * Construct a segment. The recognized forms are:
     *
     * - `new Segment(id)` &mdash; an empty segment with the given ID.
     * - `new Segment(id, fieldValues)` &mdash; a segment with the given array of field
     *   values (see {@link Segment#fieldValues}).
     * - `new Segment(id, sizeHint)` &mdash; an empty segment; the size hint of the Java
     *   version is accepted for compatibility, but ignored.
     *
     * @param {string} id segment ID
     * @param {Array<?Array<FieldValue>>|number} [fieldValuesOrSizeHint]
     */
    constructor(id, fieldValuesOrSizeHint) {
        this.id = id;
        this.fieldValues = Array.isArray(fieldValuesOrSizeHint) ? fieldValuesOrSizeHint : [];
    }

    /**
     * Get the ID of this segment (ex. "MSH")
     * @returns {string}
     */
    getId() {
        return this.id;
    }

    /**
     * Return the string value of a given field/component/subcomponent, or the empty string
     * (`""`) if the requested value does not exist, or is a null value. Requesting the first
     * child of a scalar parent will return the value of the parent. Requesting the value of
     * a parent that is a composite will return the value of its first child.
     *
     * All parameters represent indices that start at 1. The recognized forms, mirroring the
     * overloads of the Java version, are:
     *
     * ```js
     * seg.getFieldValue(fieldNo)                                    // (fieldNo, 1, 0, 0)
     * seg.getFieldValue(fieldNo, componentNo)                       // (fieldNo, 1, componentNo, 0)
     * seg.getFieldValue(fieldNo, componentNo, subcomponentNo)       // (fieldNo, 1, componentNo, subcomponentNo)
     * seg.getFieldValue(fieldNo, repetition, componentNo, subcomponentNo)
     * ```
     *
     * @param {...number} args field number `(>= 1)`, repetition number `(>= 1)` for fields
     *          that have repeated values, component number (or 0, to indicate no component),
     *          and subcomponent number (or 0, to indicate no subcomponent), as above
     * @returns {string} value of field/repetition/component/subcomponent, or empty string
     */
    getFieldValue(...args) {
        let fieldNo, repetition = 1, componentNo = 0, subcomponentNo = 0;
        switch (args.length) {
        case 1:
            [fieldNo] = args;
            break;
        case 2:
            [fieldNo, componentNo] = args;
            break;
        case 3:
            [fieldNo, componentNo, subcomponentNo] = args;
            break;
        case 4:
            [fieldNo, repetition, componentNo, subcomponentNo] = args;
            break;
        default:
            throw new TypeError("getFieldValue() takes 1 to 4 arguments");
        }
        if (this.fieldValues.length < fieldNo)
            return "";
        const repetitionList = this.fieldValues[fieldNo - 1];
        if (repetitionList == null || repetitionList.length < repetition)
            return "";
        const fieldVal = repetitionList[repetition - 1];
        if (componentNo === 0) {
            return leftmostScalar(fieldVal);
        } else {
            if (isScalar(fieldVal)) {
                // This is the case where we request a component, and perhaps subcomponent, of index 1,
                // but the field value is a scalar; and so we just return the field value.
                if (componentNo === 1 && (subcomponentNo === 1 || subcomponentNo === 0))
                    return defaultString(fieldVal.value);
                else
                    return "";  // can't traverse a scalar value
            } else { // now delve into the component list
                if (fieldVal.children.length < componentNo)
                    return "";
                const componentVal = fieldVal.children[componentNo - 1];
                if (subcomponentNo === 0) {
                    return leftmostScalar(componentVal);
                } else {
                    if (isScalar(componentVal)) {
                        if (subcomponentNo === 1)
                            return defaultString(componentVal.value);
                        else
                            return "";
                    } else {
                        if (componentVal.children.length < subcomponentNo)
                            return "";
                        return defaultString(componentVal.children[subcomponentNo - 1].value);
                    }
                }
            }
        }
    }

    /**
     * Add or replace values for a field.
     * @param {number} fieldNo field number `(>= 1)`
     * @param {?Array<FieldValue>} repeatedFieldValues array of repetitions of field values;
     *              a null value clears the field
     */
    putFieldValues(fieldNo, repeatedFieldValues) {
        this.#ensureCapacity(fieldNo);
        this.fieldValues[fieldNo - 1] = repeatedFieldValues ?? null;
    }

    /**
     * Clear any values from a given field
     * @param {number} fieldNo field number `(>= 1)`
     */
    clearFieldValues(fieldNo) {
        if (this.fieldValues.length >= fieldNo)
            this.fieldValues[fieldNo - 1] = null;
    }

    /**
     * Add a value to a given field. If a value is already present in the specified field,
     * the new value will be added as a repetition.
     *
     * Unlike the Java version, no disambiguation between a field value and a string is
     * needed: `addFieldValue(13, null)` adds a null value.
     *
     * @param {number} fieldNo field number `(>= 1)`
     * @param {FieldValue|string|null} v field value to add; a string is wrapped into a
     *          scalar field value, and any other non-field-value becomes a null value
     */
    addFieldValue(fieldNo, v) {
        this.#ensureCapacity(fieldNo);
        let l = this.fieldValues[fieldNo - 1];
        if (l == null) {
            l = [];
            this.fieldValues[fieldNo - 1] = l;
        }
        l.push(toFieldValue(v));
    }

    /**
     * Set the scalar value for a specified field/repetition/component/subcomponent. Any
     * existing value will be replaced, and if a scalar value exists at a point where a
     * composite value is needed, the scalar will be replaced (for instance, if you set the
     * value for "PID.3.2" but "PID.3" is a scalar, the scalar value will be replaced by a
     * composite with a null "PID.3.1" and the given value of "PID.3.2").
     *
     * All indices start at 1. The recognized forms, mirroring the overloads of the Java
     * version, are:
     *
     * ```js
     * seg.setFieldValue(fieldNo, value)                                    // (fieldNo, 1, 0, 0, value)
     * seg.setFieldValue(fieldNo, componentNo, value)                       // (fieldNo, 1, componentNo, 0, value)
     * seg.setFieldValue(fieldNo, componentNo, subcomponentNo, value)       // (fieldNo, 1, componentNo, subcomponentNo, value)
     * seg.setFieldValue(fieldNo, repetition, componentNo, subcomponentNo, value)
     * ```
     *
     * @param {...(number|string)} args field number `(>= 1)`, repetition number `(>= 1)`,
     *          component number (or 0, to indicate no component), subcomponent number (or 0,
     *          to indicate no subcomponent), and the scalar value to set, as above
     */
    setFieldValue(...args) {
        let fieldNo, repetition = 1, componentNo = 0, subcomponentNo = 0, value;
        switch (args.length) {
        case 2:
            [fieldNo, value] = args;
            break;
        case 3:
            [fieldNo, componentNo, value] = args;
            break;
        case 4:
            [fieldNo, componentNo, subcomponentNo, value] = args;
            break;
        case 5:
            [fieldNo, repetition, componentNo, subcomponentNo, value] = args;
            break;
        default:
            throw new TypeError("setFieldValue() takes 2 to 5 arguments");
        }
        this.#ensureCapacity(fieldNo);
        let reps = this.fieldValues[fieldNo - 1];
        if (reps == null) {
            reps = [];
            this.fieldValues[fieldNo - 1] = reps;
        }
        for (let numReps = reps.length; numReps < repetition; numReps++)
            reps.push(nullValue());
        let v = reps[repetition - 1];
        if (componentNo > 0) {
            if (isScalar(v)) {
                v.value = null;
                v.children = [];
            }
            for (let i = v.children.length; i < componentNo; i++) // ensure sufficient size
                v.children.push(nullValue());
            v = v.children[componentNo - 1];
            if (subcomponentNo > 0) {
                if (isScalar(v)) {
                    v.value = null;
                    v.children = [];
                }
                for (let i = v.children.length; i < subcomponentNo; i++)
                    v.children.push(nullValue());
                v = v.children[subcomponentNo - 1];
            }
        }
        v.value = value ?? null;
        v.children = null;  // make it scalar
    }

    #ensureCapacity(numFields) {
        // Expand the array if necessary
        for (let n = this.fieldValues.length; n < numFields; n++)
            this.fieldValues.push(null);
    }

    /**
     * Create and return a new Segment that is a deep copy of the given segment.
     * @param {Segment} orig
     * @returns {Segment}
     */
    static copyOf(orig) {
        const copy = new Segment(orig.id);
        for (const reps of orig.fieldValues) {
            if (reps == null)
                copy.fieldValues.push(null);
            else
                copy.fieldValues.push(reps.map(copyFieldValue));
        }
        return copy;
    }

    /**
     * Return a human-readable dump of the field values in this segment (<em>not</em> HL7
     * text &mdash; use {@link HL7Encoder#encode} for that).
     * @returns {string}
     */
    toString() {
        const parts = [];
        let field = 1;
        for (const repList of this.fieldValues) {
            if (repList != null) {
                for (const v of repList)
                    toStringHelper(parts, `${this.id}.${field}`, v);
            }
            field++;
        }
        return parts.join("");
    }
}

/** The value of a composite field value is the value of its first (leftmost) leaf. */
function leftmostScalar(v) {
    for (;;) {
        if (isScalar(v))
            return defaultString(v.value);
        else if (v.children.length !== 0)
            v = v.children[0];
        else
            return "";
    }
}

function toStringHelper(parts, prefix, v) {
    if (isScalar(v)) {
        if (v.value !== null)
            parts.push(`${prefix}: ${v.value === "" ? '""' : v.value}\n`);
    } else {
        parts.push(`${prefix}:\n`);
        let i = 1;
        for (const child of v.children) {
            toStringHelper(parts, `  ${prefix}.${i}`, child);
            i++;
        }
    }
}

//------------------------------------------------------------------------------
// Message
//------------------------------------------------------------------------------

/**
 * An HL7v2 message. A message is represented as a map from a segment ID to an array of
 * {@link Segment}s (with entries in the array being the repetitions of segments in the
 * message).
 */
export class Message
{
    /**
     * Segment ID &rarr; repetitions of that segment. Iteration order is insertion order,
     * which is the order in which segments are encoded.
     * @type {Map<string,Array<Segment>>}
     */
    segmentMap;

    /** Construct an empty message. */
    constructor() {
        this.segmentMap = new Map();
    }

    /**
     * Return an array of all the segment IDs in the message, in insertion order.
     *
     * Unlike the key set returned by the Java version, this array is a snapshot: adding to
     * or removing from it does not affect the message.
     * @returns {Array<string>}
     */
    getSegmentIds() {
        return [...this.segmentMap.keys()];
    }

    /**
     * Return true if a segment with the given ID is in the message.
     * @param {string} id
     * @returns {boolean}
     */
    containsSegment(id) {
        return this.segmentMap.has(id);
    }

    /**
     * Return an array of all the repetitions of segments in the message.
     *
     * Note that the iteration order of the returned array is the order in which the segment
     * lists were added to the message, so to get a well-formed HL7 message, you'll either
     * need to:
     *
     * 1. Insert segments in the correct order (i.e. MSH first, etc.), or
     * 2. Pick out the segments you need in the appropriate order using
     *    {@link Message#getSegment} and encode them individually.
     *
     * The inner arrays are the message's own, so modifying them modifies the message.
     * @returns {Array<Array<Segment>>}
     */
    segments() {
        return [...this.segmentMap.values()];
    }

    /**
     * Return an array of repetitions for a given segment ID, or null if no segment with
     * that ID is in the message.
     * @param {string} id
     * @returns {?Array<Segment>}
     */
    getSegments(id) {
        return this.segmentMap.get(id) ?? null;
    }

    /**
     * Return a given repetition of segments with the given ID, or null if no such segment is
     * in the Message. If the repetition is not specified, the first repetition is returned.
     * @param {string} id segment ID
     * @param {number} [repetition] repetition number `(>= 1)`; defaults to 1
     * @returns {?Segment}
     */
    getSegment(id, repetition = 1) {
        const l = this.segmentMap.get(id);
        if (l != null && repetition >= 1 && l.length >= repetition)
            return l[repetition - 1];
        else
            return null;
    }

    /**
     * Put a segment into the message. If a segment with the same ID already exists,
     * the new segment is added as a repetition.
     * @param {Segment} s
     */
    putSegment(s) {
        let l = this.segmentMap.get(s.getId());
        if (l == null) {
            l = [];
            this.segmentMap.set(s.getId(), l);
        }
        l.push(s);
    }

    /**
     * Remove a segment from the message. As in the Java version, the segment is identified
     * by identity, not by equality of its contents.
     * @param {Segment} s
     */
    removeSegment(s) {
        const l = this.segmentMap.get(s.getId());
        if (l != null) {
            const idx = l.indexOf(s);
            if (idx !== -1)
                l.splice(idx, 1);
        }
    }

    /**
     * Returns the message type found in `MSH.9`, if present, or null otherwise.
     *
     * Example: `"QRY^A19"`
     * @returns {?string}
     */
    getMessageType() {
        const MSH = this.getSegment("MSH");
        if (MSH === null)
            return null;
        // getFieldValue() never returns null, so a message with an MSH but no MSH.9
        // yields "^" rather than null, as in the Java version.
        const type = MSH.getFieldValue(9, 1);
        const event = MSH.getFieldValue(9, 2);
        return type + "^" + event;
    }

    /**
     * Returns the string value of a segment field given a path-like spec string as input.
     * The forms that this spec string may take are described below. <em>SID</em> is the
     * three-character segment ID, and <em>F, R, C,</em> and <em>S</em> are positive integers.
     *
     * 1. `SID.F` &mdash; field <em>F</em> of segment <em>SID</em>
     * 2. `SID.F.C` &mdash; component <em>C</em> of field <em>F</em> of segment <em>SID</em>
     * 3. `SID.F.C.S` &mdash; subcomponent <em>S</em> of component <em>C</em> of field
     *    <em>F</em> of segment <em>SID</em>
     *
     * Each of these forms has a variant where the field number <em>F</em> is given in the
     * format `"F(R)"`, where <em>R</em> is the repetition number (starting at 1) for a given
     * field.
     *
     * Examples:
     *
     * > `"MSH.9.1"` - Message Type <br>
     * > `"PID.3(2).1"` - The ID portion (i.e. PID.3.1) of the 2nd repetition in the PID.3 field.
     *
     * Likewise, for segments that can repeat in a message, the SID can be written `SID(R)`
     * to get the Rth repetition. Ex: `"NK1(2).2.1"` for the Family Name of the second
     * instance of the NK1 segment.
     *
     * If the repetition number is not specified for segment IDs or fields, it defaults to 1.
     *
     * <h3>HAPI Terser Compatibility Syntax</h3>
     *
     * To be partially compatible with the syntax for HAPI's
     * [Terser](https://hapifhir.github.io/hapi-hl7v2/base/apidocs/ca/uhn/hl7v2/util/Terser.html)
     * class, you can use a dash `"-"` in place of the periods `"."` in the above format.
     * Also, if the spec string starts with a slash `"/"`, it will be silently discarded.
     *
     * @param {string} spec spec string, as above
     * @returns {string} the field value, or the empty string "" if such a field is not
     *          present in the message (or if the spec string is malformed)
     */
    getField(spec) {
        const path = parseSpec(spec);
        if (path === null)
            return "";
        const seg = this.getSegment(path.segmentId, path.segmentRep);
        if (seg === null)
            return "";
        return seg.getFieldValue(path.field, path.fieldRep, path.component, path.subcomponent);
    }

    /**
     * Sets a field of a segment in the message. If no segment with the specified ID exists
     * in the message, it will be created and added. A malformed spec string is silently
     * ignored.
     * @param {string} spec spec string as described in the docs for {@link Message#getField}
     * @param {?string} value scalar value to assign to the field
     */
    setField(spec, value) {
        const path = parseSpec(spec);
        if (path === null)
            return;
        let seg = this.getSegment(path.segmentId, path.segmentRep);
        if (seg === null) {
            seg = new Segment(path.segmentId);
            this.putSegment(seg);
        }
        seg.setFieldValue(path.field, path.fieldRep, path.component, path.subcomponent, value);
    }

    /**
     * Create and return a new Message that is a deep copy of the given message.
     * @param {Message} m
     * @returns {Message}
     */
    static copyOf(m) {
        const copy = new Message();
        for (const segs of m.segments()) {
            for (const s of segs)
                copy.putSegment(Segment.copyOf(s));
        }
        return copy;
    }

    /**
     * Return a human-readable dump of the segments in this message (<em>not</em> HL7 text
     * &mdash; use {@link HL7Encoder#encode} for that).
     * @returns {string}
     */
    toString() {
        const parts = [];
        for (const l of this.segmentMap.values()) {
            for (const s of l)
                parts.push(s.toString());
        }
        return parts.join("");
    }
}

/**
 * Parse a spec string as described in the docs for {@link Message#getField} into
 * `{segmentId, segmentRep, field, fieldRep, component, subcomponent}`, or return null if
 * the spec string is malformed.
 *
 * Where the Java version relies on catching NumberFormatException and
 * IndexOutOfBoundsException, we validate explicitly. As a consequence, specs with a
 * non-positive segment repetition, field number, or field repetition (ex. "MSH.0") return
 * null here, rather than throwing an exception out of getField()/setField() as they do in
 * Java.
 */
function parseSpec(spec) {
    if (typeof spec !== 'string')
        return null;
    if (spec.startsWith("/"))
        spec = spec.substring(1);
    if (spec.length < 5)  // as in Java: this rejects specs like "AB.1", but allows "MSH.9"
        return null;
    const parts = spec.split(/[.\-]/);
    if (parts.length < 2 || parts.length > 4)
        return null;

    let segmentRep = 1, fieldRep = 1;
    let field, component = 0, subcomponent = 0;

    if (parts[0].length < 3)
        return null;
    const segmentId = parts[0].substring(0, 3);
    let idx = parts[0].indexOf('(');
    if (idx !== -1) {
        const lastChar = parts[0].length - 1;
        if (parts[0].charAt(lastChar) === ')')
            segmentRep = strictInt(parts[0].substring(idx + 1, lastChar));
        else
            return null;
    }
    idx = parts[1].indexOf('(');
    if (idx !== -1) {
        const lastChar = parts[1].length - 1;
        if (parts[1].charAt(lastChar) === ')') {
            field = strictInt(parts[1].substring(0, idx));
            fieldRep = strictInt(parts[1].substring(idx + 1, lastChar));
        } else {
            return null;
        }
    } else {
        field = strictInt(parts[1]);
    }
    if (parts.length > 2)
        component = strictInt(parts[2]);
    if (parts.length > 3)
        subcomponent = strictInt(parts[3]);

    if (!(segmentRep >= 1) || !(field >= 1) || !(fieldRep >= 1) ||
        !(component >= 0) || !(subcomponent >= 0))  // also catches NaN
        return null;
    return {segmentId, segmentRep, field, fieldRep, component, subcomponent};
}

//------------------------------------------------------------------------------
// HL7Params
//------------------------------------------------------------------------------

const DEFAULT_FIELD_SEPARATOR = '|';
const DEFAULT_COMPONENT_SEPARATOR = '^';
const DEFAULT_SUBCOMPONENT_SEPARATOR = '&';
const DEFAULT_REPETITION_SEPARATOR = '~';
const DEFAULT_ESCAPE_CHAR = '\\';

/**
 * An instance of `HL7Params` stores the settings used to encode/decode HL7v2 messages.
 * These are, basically, the field separator and encoding characters found in the `MSH`
 * segment.
 */
export class HL7Params
{
    /** @type {string} */ fieldSeparator;
    /** @type {string} */ componentSeparator;
    /** @type {string} */ subcomponentSeparator;
    /** @type {string} */ repetitionSeparator;
    /** @type {string} */ escapeChar;

    /**
     * An array of the separators in "descending" order of a parse tree for field data,
     * i.e. `[repetitionSeparator, componentSeparator, subcomponentSeparator]`
     * @type {Array<string>}
     */
    fieldValueSeparators;

    // Simultaneous-replacement functions built from the separators and escape character;
    // see #initState().
    #escaper;
    #unescaper;

    /**
     * Construct an HL7Params. The recognized forms are:
     *
     * - `new HL7Params()` &mdash; the default separators and escape character,
     *   i.e. <code>| ^ &amp; ~ \\</code>
     * - `new HL7Params(MSHText)` &mdash; the separators and escape character found in the
     *   MSH segment text given as an argument (see {@link HL7Params.fromMSH}).
     * - `new HL7Params(fieldSeparator, componentSeparator, subcomponentSeparator,
     *   repetitionSeparator, escapeChar)` &mdash; the given separators and escape character.
     *   Note that this argument order (inherited from the Java version) is <em>not</em> the
     *   order in which the delimiters appear in an MSH segment.
     *
     * @param {...string} args as above
     */
    constructor(...args) {
        if (args.length === 5) {
            [this.fieldSeparator, this.componentSeparator, this.subcomponentSeparator,
             this.repetitionSeparator, this.escapeChar] = args;
        } else if (args.length === 1) {
            const MSHText = args[0];
            if (MSHText.length >= 8 && MSHText.startsWith("MSH")) {
                this.fieldSeparator = MSHText.charAt(3);
                this.componentSeparator = MSHText.charAt(4);
                this.repetitionSeparator = MSHText.charAt(5);
                this.escapeChar = MSHText.charAt(6);
                this.subcomponentSeparator = MSHText.charAt(7);
            } else {
                this.#setDefaults();
            }
        } else if (args.length === 0) {
            this.#setDefaults();
        } else {
            throw new TypeError("new HL7Params() takes 0, 1, or 5 arguments");
        }
        this.#initState();
    }

    /**
     * Construct an HL7Params using the separators and escape character found in the MSH
     * segment text given as an argument. If the text doesn't look like an MSH segment, the
     * default separators and escape character are used.
     * @param {string} MSHText
     * @returns {HL7Params}
     */
    static fromMSH(MSHText) {
        return new HL7Params(MSHText);
    }

    #setDefaults() {
        this.fieldSeparator = DEFAULT_FIELD_SEPARATOR;
        this.componentSeparator = DEFAULT_COMPONENT_SEPARATOR;
        this.subcomponentSeparator = DEFAULT_SUBCOMPONENT_SEPARATOR;
        this.repetitionSeparator = DEFAULT_REPETITION_SEPARATOR;
        this.escapeChar = DEFAULT_ESCAPE_CHAR;
    }

    #initState() {
        this.fieldValueSeparators =
            [this.repetitionSeparator, this.componentSeparator, this.subcomponentSeparator];
        // These two arrays are parallel: for each index, they indicate the mapping from a
        // special character to the appropriate escape sequence. For the default separator
        // and escape characters, their contents are (taking liberties with backslash
        // escaping):
        //
        //     escapeChars     = ["|",   "^",   "&",   "~",   "\",   "\r",    "\n"  ]
        //     escapeSequences = ["\F\", "\S\", "\T\", "\R\", "\E\", "\X0D\", "\X0A\"]
        const e = this.escapeChar;
        const escapeChars = [this.fieldSeparator, this.componentSeparator,
                             this.subcomponentSeparator, this.repetitionSeparator,
                             this.escapeChar, "\r", "\n"];
        const escapeSequences = [`${e}F${e}`, `${e}S${e}`, `${e}T${e}`, `${e}R${e}`,
                                 `${e}E${e}`, `${e}X0D${e}`, `${e}X0A${e}`];
        this.#escaper = buildReplacer(escapeChars, escapeSequences);
        this.#unescaper = buildReplacer(escapeSequences, escapeChars);
    }

    /**
     * Escapes text given the separators and escape character in this `HL7Params` instance.
     *
     * For instance, given the default HL7 delimiters and escape character,
     *
     * > `"It's ~20 lbs & 3 oz"` &rarr; `"It's \R\20 lbs \T\ 3 oz"`
     *
     * @param {string} s string to escape
     * @returns {string} escaped string
     */
    escape(s) {
        return this.#escaper(s);
    }

    /**
     * Unescapes text given the separators and escape character in this `HL7Params` instance.
     *
     * For instance, given the default HL7 delimiters and escape character,
     *
     * > `"It's \R\20 lbs \T\ 3 oz"` &rarr; `"It's ~20 lbs & 3 oz"`
     *
     * @param {string} s string to unescape
     * @returns {string} unescaped string
     */
    unescape(s) {
        return this.#unescaper(s);
    }
}

//------------------------------------------------------------------------------
// HL7Parser
//------------------------------------------------------------------------------

// Used with #parseFieldText()
const LEVEL_FIELD = 0;
const LEVEL_COMPONENT = 1;
const LEVEL_SUBCOMPONENT = 2;

/**
 * Parses HL7v2 text into an instance of {@link Message}.
 */
export class HL7Parser
{
    /** @type {HL7Params} */
    params;

    /**
     * Construct an HL7Parser. The recognized forms are:
     *
     * - `new HL7Parser()` &mdash; with default parameters.
     * - `new HL7Parser(params)` &mdash; with the given {@link HL7Params}.
     * - `new HL7Parser(hl7msg)` &mdash; with parameters derived from the given HL7 message.
     *   This message must begin with an `MSH` segment, so that the delimiters and escape
     *   character can be derived.
     *
     * @param {HL7Params|string} [paramsOrHl7msg]
     */
    constructor(paramsOrHl7msg) {
        if (paramsOrHl7msg === undefined)
            this.params = new HL7Params();
        else if (paramsOrHl7msg instanceof HL7Params)
            this.params = paramsOrHl7msg;
        else
            this.params = HL7Params.fromMSH(paramsOrHl7msg);
    }

    /**
     * Return the parameters being used by this HL7Parser
     * @returns {HL7Params}
     */
    getParams() {
        return this.params;
    }

    /**
     * Set the parameters to be used by this HL7Parser
     * @param {HL7Params} params
     */
    setParams(params) {
        this.params = params;
    }

    /**
     * Parse text as a <em>single</em> HL7 message. Malformed lines (those shorter than five
     * characters, or whose fourth character isn't the field separator) are skipped.
     * @param {string} hl7msg HL7v2 text to parse
     * @returns {Message} new Message instance
     */
    parse(hl7msg) {
        const m = new Message();
        for (const line of splitLines(hl7msg)) {
            if (line.length < 5 || line.charAt(3) !== this.params.fieldSeparator)  // malformed
                continue;
            const segmentId = line.substring(0, 3);
            let fieldValues;
            const fields = line.substring(4).split(this.params.fieldSeparator);
            let fieldOffset = 0;
            if (segmentId === "MSH") {
                // MSH.1 & MSH.2 are special
                fieldValues = [];
                fieldValues.push([scalar(this.params.fieldSeparator)]);  // MSH.1 exists outside the normal scheme of things
                fieldValues.push([scalar(fields[0])]);  // add the delimiters verbatim
                fieldOffset = 1;
            } else {
                fieldValues = [];
            }
            while (fieldOffset < fields.length)
                fieldValues.push(this.#parseFieldText(fields[fieldOffset++]));
            m.putSegment(new Segment(segmentId, fieldValues));
        }
        return m;
    }

    #parseFieldText(text) {
        // It is a space-saving optimization to use null for the repetition list to
        // indicate a blank field value, rather than having a one-element list with
        // a null-value FieldValue.
        if (text.length === 0)
            return null;

        // Unlike the Java version (which uses StringUtils.split() here, and thereby drops
        // empty repetitions), we preserve empty repetitions, so that parsing and encoding
        // are symmetrical: "a~~b" gives three repetitions, the second a null value.
        const reps = text.split(this.params.repetitionSeparator);
        return reps.map((s) => this.#parseFieldTextHelper(s, LEVEL_FIELD));
    }

    #parseFieldTextHelper(text, fieldLevel) {
        if (text.length === 0)
            return nullValue();
        else if (text === '""')
            return scalar("");  // present but empty
        if (fieldLevel === LEVEL_SUBCOMPONENT)  // we cannot go any deeper
            return scalar(this.params.unescape(text));
        const separator = fieldLevel === LEVEL_FIELD
            ? this.params.componentSeparator : this.params.subcomponentSeparator;
        const parts = text.split(separator);
        if (parts.length === 1) {  // no children
            // I've seen it in the wild that a field will have subcomponent separators without any component
            // separators, so we'll need to take that possibility into account.
            if (fieldLevel === LEVEL_FIELD && parts[0].indexOf(this.params.subcomponentSeparator) !== -1)
                return fieldValue(null, [this.#parseFieldTextHelper(parts[0], LEVEL_COMPONENT)]);
            else
                return scalar(this.params.unescape(parts[0]));
        } else {
            return fieldValue(null,
                parts.map((part) => this.#parseFieldTextHelper(part, fieldLevel + 1)));
        }
    }
}

//------------------------------------------------------------------------------
// HL7Encoder
//------------------------------------------------------------------------------

/**
 * Encodes an instance of {@link Message} into HL7v2 text.
 */
export class HL7Encoder
{
    /** @type {HL7Params} */
    params;

    /**
     * Construct an HL7Encoder with the given params, or with default params if none are
     * given.
     * @param {HL7Params} [params]
     */
    constructor(params) {
        this.params = params ?? new HL7Params();
    }

    /**
     * Return the parameters being used by this HL7Encoder
     * @returns {HL7Params}
     */
    getParams() {
        return this.params;
    }

    /**
     * Set the parameters to be used by this HL7Encoder
     * @param {HL7Params} params
     */
    setParams(params) {
        this.params = params;
    }

    /**
     * Encode a {@link Message} or a single {@link Segment} into HL7v2 format.
     *
     * Note that for the resulting HL7 to be valid, the segments need to be added to the
     * Message instance in the correct order, since Message iterates through its segments in
     * the order of insertion.
     *
     * Also note that `MSH.1` and `MSH.2` are written from the separators and escape
     * character of this encoder's {@link HL7Params}, and not from the values stored in the
     * MSH segment; encoding a message with params other than those it was parsed with will
     * thus rewrite its delimiters.
     *
     * @param {Message|Segment} messageOrSegment Message or Segment to encode
     * @returns {string} HL7v2 text (including the carriage-return '\r' at the end of each
     *          segment)
     */
    encode(messageOrSegment) {
        const parts = [];
        if (messageOrSegment instanceof Message) {
            for (const segReps of messageOrSegment.segmentMap.values()) {
                for (const s of segReps)
                    this.#encodeHelper(parts, s);
            }
        } else if (messageOrSegment instanceof Segment) {
            this.#encodeHelper(parts, messageOrSegment);
        } else {
            throw new TypeError("encode() takes a Message or a Segment");
        }
        return parts.join("");
    }

    #encodeHelper(parts, s) {
        parts.push(s.id);
        const numFields = s.fieldValues.length;
        let fieldNo = 0;
        if (s.id === "MSH") {
            // output MSH.1 and MSH.2 specially
            parts.push(this.params.fieldSeparator);
            parts.push(this.params.componentSeparator, this.params.repetitionSeparator,
                       this.params.escapeChar, this.params.subcomponentSeparator);
            fieldNo = 2;
        }
        for (; fieldNo < numFields; fieldNo++) {
            parts.push(this.params.fieldSeparator);
            this.#encodeFieldValues(parts, s.fieldValues[fieldNo], 0);
        }
        parts.push('\r');
    }

    #encodeFieldValues(parts, values, level) {
        // Values nested deeper than a subcomponent cannot be represented in HL7 text,
        // and are silently dropped.
        if (values == null || level >= this.params.fieldValueSeparators.length)
            return;
        let first = true;
        for (const v of values) {
            if (first) first = false;
            else parts.push(this.params.fieldValueSeparators[level]);
            if (isScalar(v)) {
                if (v.value !== null) {
                    if (v.value === "")
                        parts.push('""');
                    else
                        parts.push(this.params.escape(v.value));
                }
            } else {
                this.#encodeFieldValues(parts, v.children, level + 1);
            }
        }
    }
}

export default {
    HL7Params, HL7Parser, HL7Encoder, Message, Segment,
    fieldValue, nullValue, scalar, composite, indexed, isScalar, copyFieldValue,
};

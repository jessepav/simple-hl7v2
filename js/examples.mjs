/**
 * Examples of using Simple-HL7v2; a port of `java/examples/SimpleHL7v2Examples.java`.
 *
 * Run as:
 * ```
 *   node examples.mjs                        # just createSegments()
 *   node examples.mjs message.hl7            # dump the internal representation
 *   node examples.mjs message.hl7 MSH.9.1    # query a field value
 *   node examples.mjs message.hl7 MSH.9.1 X  # set a field value, then dump
 * ```
 */

import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

import {HL7Params, HL7Parser, HL7Encoder, Message, Segment,
        scalar, composite, indexed} from './simple_hl7.mjs';

export function createSegments() {
    const MSH = new Segment("MSH");
    MSH.addFieldValue(1, "|");
    MSH.addFieldValue(2, "^~\\&");
    MSH.addFieldValue(3, "EPIC");
    MSH.addFieldValue(5, "SMS");
    MSH.addFieldValue(6, "SMSDT");
    MSH.addFieldValue(7, "201501011408");
    MSH.addFieldValue(9, composite("ADT", "A04"));
    MSH.addFieldValue(10, "9000123");
    MSH.addFieldValue(11, "D");
    MSH.addFieldValue(12, "2.7");
    MSH.addFieldValue(13, null);  // a null value; scalar(null) works too

    const PID = new Segment("PID");
    PID.addFieldValue(2,
        indexed(
            1, "0493575",
            4, composite("Big", null, "Elephant"),
            5, "ID 1")
    );
    PID.addFieldValue(3, "454721");
    PID.addFieldValue(5, composite("DOE", "JOHN"));

    const PV1 = new Segment("PV1", 3);  // the size hint is accepted, but ignored, in JS
    PV1.addFieldValue(2, "O");
    PV1.addFieldValue(3, "168 ");
    PV1.addFieldValue(3, "219");
    PV1.addFieldValue(3, "C");
    PV1.addFieldValue(3, "P");

    const m = new Message();
    m.putSegment(MSH);
    m.putSegment(PID);
    m.putSegment(PV1);
    const encoder = new HL7Encoder();
    // Since HL7 messages are delimited by '\r', we change the line endings for printing
    console.log(encoder.encode(m).replaceAll("\r", "\n"));
}

export function parseInput(args) {
    if (args.length === 0)
        return new Message();

    // Simple-HL7v2 parses strings, so we handle the file transport ourselves.
    const hl7text = readFileSync(args[0], 'utf8').trim();
    const parser = new HL7Parser(new HL7Params(hl7text));
    const m = parser.parse(hl7text);

    // If one argument is supplied, print the internal representation
    if (args.length === 1) {
        console.log(m.toString());
    } else if (args.length === 2) {  // Query a field value using Message#getField(String)
        console.log(m.getField(args[1]));
    } else if (args.length === 3) {  // Set a field value using Message#setField(String, String)
        m.setField(args[1], args[2]);
        console.log(m.toString());
    }
    return m;
}

export function editMessage(m) {
    if (m.getMessageType() === "QRY^A19") {
        // Get and set some field values
        const firstIdNumber = m.getField("QRD.8.1");
        m.setField("QRD.8.2", "Good Family Name");

        // Get the second repetition of a field value
        const secondIdNumber = m.getField("QRD.8(2).1");
    }
}

function main(args) {
    console.log("\n--- parseInput() ----------------\n");
    parseInput(args);
    console.log("\n\n--- createSegments() ----------------\n");
    createSegments();
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href)
    main(process.argv.slice(2));

package com.illcode.hl7;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.illcode.hl7.Segment.FieldValue.composite;
import static com.illcode.hl7.Segment.FieldValue.scalar;
import static com.illcode.hl7.Segment.FieldValue.indexed;

public class SimpleHL7v2Examples
{
    public static void createSegments() {
        Segment MSH = new Segment("MSH");
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
        MSH.addFieldValue(13, scalar(null)); // You need the scalar() to disambiguate overload resolution

        Segment PID = new Segment("PID");
        PID.addFieldValue(2,
            indexed(
                1, "0493575",
                4, composite("Big", null, "Elephant"),
                5, "ID 1")
        );
        PID.addFieldValue(3, "454721");
        PID.addFieldValue(5, composite("DOE", "JOHN"));

        Segment PV1 = new Segment("PV1", 3);
        PV1.addFieldValue(2, "O");
        PV1.addFieldValue(3, "168 ");
        PV1.addFieldValue(3, "219");
        PV1.addFieldValue(3, "C");
        PV1.addFieldValue(3, "P");

        Message m = new Message();
        m.putSegment(MSH);
        m.putSegment(PID);
        m.putSegment(PV1);
        HL7Encoder encoder = new HL7Encoder();
        // Since HL7 messages are delimited by '\r', we change the line endings for printing
        System.out.println(encoder.encode(m).replace("\r", "\n"));
    }

    public static Message parseInput(String[] args) throws IOException {
        if (args.length == 0)
            return new Message();

        final Reader r = new InputStreamReader(Files.newInputStream(Paths.get(args[0])), StandardCharsets.UTF_8);
        final String hl7text = HL7Parser.slurpReaderText(r, 512).trim();
        final HL7Parser parser = new HL7Parser(new HL7Params(hl7text));
        final Message m = parser.parse(hl7text);

        // If one argument is supplied, print the internal representation
        if (args.length == 1) {
            System.out.println(m.toString());
        } else if (args.length == 2) {  // Query a field value using Message#getField(String)
            System.out.println(m.getField(args[1]));
        } else if (args.length == 3) {  // Set a field value using Message#setField(String, String)
            m.setField(args[1], args[2]);
            System.out.println(m);
        }
        return m;
    }

    public static void editMessage(Message m) {
        if (m.getMessageType().equals("QRY^A19")) {
            // Get and set some field values
            String firstIdNumber = m.getField("QRD.8.1");
            m.setField("QRD.8.2", "Good Family Name");

            // Get the second repetition of a field value
            String secondIdNumber = m.getField("QRD.8(2).1");
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("\n--- parseInput() ----------------\n");
        parseInput(args);
        System.out.println("\n\n--- createSegments() ----------------\n");
        createSegments();
    }
}

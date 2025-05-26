# Simple-HL7v2

A simple Java HL7v2 parsing and encoding library.

[HAPI HL7 v2](https://hapifhir.github.io/hapi-hl7v2/) is excellent and comprehensive, but
it's a big library (36 MB distribution tarball, 131 MB Javadoc ZIP), handling many aspects
of HL7 connectivity, both as client and server. It's built from the official HL7
databases, and actually understands the structure of various version of HL7 messages.

*Simple-HL7v2* on the other hand deals only with parsing, manipulating, and encoding
HL7-formatted data. You need to refer to the MSH message-type field components as
`MSH.9.1` and `MSH.9.2` rather than (with HAPI) `MSH.getMessageType()`, and *Simple-HL7v2*
will happily allow you to add fifteen components to the `QRD.4` field if you tell it
to.

But *Simple-HL7v2* is a single 18K .jar with a dependency only on *Commons-Lang3*, and if you
have a clear idea of the type of messages you'll be receiving and sending, and handle
your own file or network transport, it may be all you need.

# Download

Download JARs and source from the GitHub [releases](https://github.com/jessepav/simple-hl7v2/releases)
page.

# Building & Requirements

*Simple-HL7v2* works with JDK version 7+.

You'll need [Apache Commons Lang 3](http://commons.apache.org/proper/commons-lang/) in your
classpath to use the library. An older version (3.4, compatible with Java 7) is in the
[`lib`](https://github.com/jessepav/simple-hl7v2/tree/master/lib) directory of the source tree.

To build a binary JAR, run

```
ant artifact.dist
```

in the root of the source tree. 

To generate Javadocs, run either `scripts\make-javadoc.bat` or
`scripts/make-javadoc.sh`, depending on your platform.

# Usage

For example code, see [examples/SimpleHL7v2Examples.java](https://github.com/jessepav/simple-hl7v2/blob/master/examples/SimpleHL7v2Examples.java)
but basic usage is quite easy:

```java
HL7Parser parser = new HL7Parser();
Message m = parser.parse(hl7text);

String type = m.getMessageType();  // ex. "MDM^T02"

String observationTimestamp1 = m.getField("OBR.7");  // Get a field value
String observationTimestamp2 = m.getField("OBR(2).7");  // Get a field value from a repeated segment

m.setField("OBR(1).8", observationTimestamp2);  // Change a field value

HL7Encoder encoder = new HL7Encoder();
String updatedHL7text = encoder.encode(m);

```

Programmatic construction of segment values is made more efficient by static helper
methods like `indexed()` and `composite()` in the [`Segment.FieldValue`](https://jessepav.github.io/simple-hl7v2/javadoc/com/illcode/hl7/Segment.FieldValue.html)
class.

```java
Segment PID = new Segment("PID");
PID.addFieldValue(2,
	indexed(
		1, "0493575",
		4, composite("Big", null, "Elephant"),
		5, "ID 1")
);
PID.addFieldValue(3, "454721");
PID.addFieldValue(5, composite("DOE", "JOHN"));

Message m = new Message();
m.putSegment(PID);
```

# Javadoc

Javadocs are available at [https://jessepav.github.io/simple-hl7v2/javadoc/](https://jessepav.github.io/simple-hl7v2/javadoc/).

# Simple-HL7v2

A simple Java HL7v2 parsing and encoding library.

[HAPI HL7 v2](https://hapifhir.github.io/hapi-hl7v2/) is excellent and comprehensive, but
it's a big library (36 MB distribution tarball, 131 MB Javadoc ZIP), handling many aspects
of HL7 connectivity, both as client and server. It's built from the official HL7
databases, and actually understands the structure of various version of HL7 messages.

*Simple-HL7v2* on the other hand deals only with parsing. manipulating, and encoding
HL7-formatted data. You need to refer to the MSH message-type field components as
`MSH.9.1` and `MSH.9.2` rather than (with HAPI) `MSH.getMessageType()`, and *Simple-HL7v2*
will happily allow you to add fifteen components to the `QRD.4` field if you tell it
to.

But *Simple-HL7v2* is a 18K .jar with a dependency only on Commons-Lang3, and if you
have a clear idea of the type of messages you'll be receiving and sending, and handle
your own file or network transport, it may be all you need.



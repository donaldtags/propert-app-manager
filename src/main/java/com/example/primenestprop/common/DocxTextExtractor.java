package com.example.primenestprop.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Extracts plain text from a .docx file without pulling in Apache POI - a .docx is a zip archive
 * containing word/document.xml, whose &lt;w:t&gt; elements hold the visible text.
 */
public final class DocxTextExtractor {
    private DocxTextExtractor() {
    }

    public static String extract(InputStream docxStream) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(docxStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return extractFromDocumentXml(zip);
                }
            }
        }
        throw new IOException("word/document.xml not found - not a valid .docx file");
    }

    private static String extractFromDocumentXml(InputStream xmlStream) throws IOException {
        StringBuilder text = new StringBuilder();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(xmlStream);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT && "t".equals(reader.getLocalName())) {
                        text.append(reader.getElementText());
                    } else if (event == XMLStreamConstants.END_ELEMENT
                            && ("p".equals(reader.getLocalName()) || "tr".equals(reader.getLocalName()))) {
                        text.append('\n');
                    } else if (event == XMLStreamConstants.START_ELEMENT
                            && ("tab".equals(reader.getLocalName()) || "br".equals(reader.getLocalName()))) {
                        text.append(' ');
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException ex) {
            throw new IOException("Could not parse document.xml", ex);
        }
        return text.toString();
    }
}

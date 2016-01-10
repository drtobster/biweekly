package biweekly.io.xml;

import static biweekly.io.xml.XCalNamespaceContext.XCAL_NS;
import static biweekly.io.xml.XCalQNames.COMPONENTS;
import static biweekly.io.xml.XCalQNames.ICALENDAR;
import static biweekly.io.xml.XCalQNames.PARAMETERS;
import static biweekly.io.xml.XCalQNames.PROPERTIES;
import static biweekly.util.IOUtils.utf8Writer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import biweekly.ICalDataType;
import biweekly.ICalVersion;
import biweekly.ICalendar;
import biweekly.component.ICalComponent;
import biweekly.component.VTimezone;
import biweekly.io.SkipMeException;
import biweekly.io.StreamWriter;
import biweekly.io.scribe.component.ICalComponentScribe;
import biweekly.io.scribe.property.ICalPropertyScribe;
import biweekly.parameter.ICalParameters;
import biweekly.property.ICalProperty;
import biweekly.property.Version;
import biweekly.property.Xml;
import biweekly.util.XmlUtils;

/*
 Copyright (c) 2013-2015, Michael Angstadt
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met: 

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer. 
 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution. 

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 The views and conclusions contained in the software and documentation are those
 of the authors and should not be interpreted as representing official policies, 
 either expressed or implied, of the FreeBSD Project.
 */

/**
 * <p>
 * Writes xCards (XML-encoded iCalendar objects) in a streaming fashion.
 * </p>
 * <p>
 * <b>Example:</b>
 * 
 * <pre class="brush:java">
 * ICalendar ical1 = ...
 * ICalendar ical2 = ...
 * File file = new File("icals.xml");
 * XCalWriter writer = null;
 * try {
 *   writer = new XCalWriter(file);
 *   writer.write(ical1);
 *   writer.write(ical2);
 * } finally {
 *   if (writer != null) writer.close();
 * }
 * </pre>
 * 
 * </p>
 * 
 * <p>
 * <b>Changing the timezone settings:</b>
 * 
 * <pre class="brush:java">
 * XCalWriter writer = new XCalWriter(...);
 * 
 * //format all date/time values in a specific timezone instead of UTC
 * //note: this makes an HTTP call to the "tzurl.org" website
 * writer.getTimezoneInfo().setDefaultTimeZone(TimeZone.getDefault());
 * 
 * //format the value of a particular date/time property in a specific timezone instead of UTC
 * //note: this makes an HTTP call to the "tzurl.org" website
 * DateStart dtstart = ...
 * writer.getTimezoneInfo().setTimeZone(dtstart, TimeZone.getDefault());
 * 
 * //generate Outlook-friendly VTIMEZONE components:
 * writer.getTimezoneInfo().setGenerator(new TzUrlDotOrgGenerator(true));
 * </pre>
 * 
 * </p>
 * 
 * @author Michael Angstadt
 * @see <a href="http://tools.ietf.org/html/rfc6351">RFC 6351</a>
 */
public class XCalWriter extends StreamWriter {
	//How to use SAX to write XML: http://stackoverflow.com/q/4898590
	private final Document DOC = XmlUtils.createDocument();

	/**
	 * Defines the names of the XML elements that are used to hold each
	 * parameter's value.
	 */
	private final Map<String, ICalDataType> parameterDataTypes = new HashMap<String, ICalDataType>();
	{
		registerParameterDataType(ICalParameters.CN, ICalDataType.TEXT);
		registerParameterDataType(ICalParameters.ALTREP, ICalDataType.URI);
		registerParameterDataType(ICalParameters.CUTYPE, ICalDataType.TEXT);
		registerParameterDataType(ICalParameters.DELEGATED_FROM, ICalDataType.CAL_ADDRESS);
		registerParameterDataType(ICalParameters.DELEGATED_TO, ICalDataType.CAL_ADDRESS);
		registerParameterDataType(ICalParameters.DIR, ICalDataType.URI);
		registerParameterDataType(ICalParameters.ENCODING, ICalDataType.TEXT);
		registerParameterDataType(ICalParameters.FMTTYPE, ICalDataType.TEXT);
		registerParameterDataType(ICalParameters.FBTYPE, ICalDataType.TEXT);
		registerParameterDataType(ICalParameters.LANGUAGE, ICalDataType.TEXT);
		registerParameterDataType(ICalParameters.MEMBER, ICalDataType.CAL_ADDRESS);
		registerParameterDataType(ICalParameters.PARTSTAT, ICalDataType.TEXT);
		registerParameterDataType(ICalParameters.RANGE, ICalDataType.TEXT);
		registerParameterDataType(ICalParameters.RELATED, ICalDataType.TEXT);
		registerParameterDataType(ICalParameters.RELTYPE, ICalDataType.TEXT);
		registerParameterDataType(ICalParameters.ROLE, ICalDataType.TEXT);
		registerParameterDataType(ICalParameters.RSVP, ICalDataType.BOOLEAN);
		registerParameterDataType(ICalParameters.SENT_BY, ICalDataType.CAL_ADDRESS);
		registerParameterDataType(ICalParameters.TZID, ICalDataType.TEXT);
	}

	private final Writer writer;
	private final ICalVersion targetVersion = ICalVersion.V2_0;
	private final TransformerHandler handler;
	private final boolean icalendarElementExists;
	private boolean started = false;

	/**
	 * @param out the output stream to write to (UTF-8 encoding will be used)
	 */
	public XCalWriter(OutputStream out) {
		this(out, (Integer) null);
	}

	/**
	 * @param out the output stream to write to (UTF-8 encoding will be used)
	 * @param indent the number of indent spaces to use for pretty-printing or
	 * "null" to disable pretty-printing (disabled by default)
	 */
	public XCalWriter(OutputStream out, Integer indent) {
		this(out, indent, null);
	}

	/**
	 * @param out the output stream to write to (UTF-8 encoding will be used)
	 * @param indent the number of indent spaces to use for pretty-printing or
	 * "null" to disable pretty-printing (disabled by default)
	 * @param xmlVersion the XML version to use (defaults to "1.0") (Note: Many
	 * JDKs only support 1.0 natively. For XML 1.1 support, add a JAXP library
	 * like <a href=
	 * "http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22xalan%22%20AND%20a%3A%22xalan%22"
	 * >xalan</a> to your project)
	 */
	public XCalWriter(OutputStream out, Integer indent, String xmlVersion) {
		this(utf8Writer(out), indent, xmlVersion);
	}

	/**
	 * @param out the output stream to write to (UTF-8 encoding will be used)
	 * @param outputProperties properties to assign to the JAXP transformer (see
	 * {@link Transformer#setOutputProperties(Properties)}).
	 */
	public XCalWriter(OutputStream out, Map<String, String> outputProperties) {
		this(utf8Writer(out), outputProperties);
	}

	/**
	 * @param file the file to write to (UTF-8 encoding will be used)
	 * @throws IOException if there's a problem opening the file
	 */
	public XCalWriter(File file) throws IOException {
		this(file, (Integer) null);
	}

	/**
	 * @param file the file to write to (UTF-8 encoding will be used)
	 * @param indent the number of indent spaces to use for pretty-printing or
	 * "null" to disable pretty-printing (disabled by default)
	 * @throws IOException if there's a problem opening the file
	 */
	public XCalWriter(File file, Integer indent) throws IOException {
		this(file, indent, null);
	}

	/**
	 * @param file the file to write to (UTF-8 encoding will be used)
	 * @param indent the number of indent spaces to use for pretty-printing or
	 * "null" to disable pretty-printing (disabled by default)
	 * @param xmlVersion the XML version to use (defaults to "1.0") (Note: Many
	 * JDKs only support 1.0 natively. For XML 1.1 support, add a JAXP library
	 * like <a href=
	 * "http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22xalan%22%20AND%20a%3A%22xalan%22"
	 * >xalan</a> to your project)
	 * @throws IOException if there's a problem opening the file
	 */
	public XCalWriter(File file, Integer indent, String xmlVersion) throws IOException {
		this(utf8Writer(file), indent, xmlVersion);
	}

	/**
	 * @param file the file to write to (UTF-8 encoding will be used)
	 * @param outputProperties properties to assign to the JAXP transformer (see
	 * {@link Transformer#setOutputProperties(Properties)}).
	 * @throws IOException if there's a problem opening the file
	 */
	public XCalWriter(File file, Map<String, String> outputProperties) throws IOException {
		this(utf8Writer(file), outputProperties);
	}

	/**
	 * @param writer the writer to write to
	 */
	public XCalWriter(Writer writer) {
		this(writer, (Integer) null);
	}

	/**
	 * @param writer the writer to write to
	 * @param indent the number of indent spaces to use for pretty-printing or
	 * "null" to disable pretty-printing (disabled by default)
	 */
	public XCalWriter(Writer writer, Integer indent) {
		this(writer, indent, null);
	}

	/**
	 * @param writer the writer to write to
	 * @param indent the number of indent spaces to use for pretty-printing or
	 * "null" to disable pretty-printing (disabled by default)
	 * @param xmlVersion the XML version to use (defaults to "1.0") (Note: Many
	 * JDKs only support 1.0 natively. For XML 1.1 support, add a JAXP library
	 * like <a href=
	 * "http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22xalan%22%20AND%20a%3A%22xalan%22"
	 * >xalan</a> to your project)
	 */
	public XCalWriter(Writer writer, Integer indent, String xmlVersion) {
		this(writer, new XCalOutputProperties(indent, xmlVersion));
	}

	/**
	 * @param writer the writer to write to
	 * @param outputProperties properties to assign to the JAXP transformer (see
	 * {@link Transformer#setOutputProperties(Properties)}).
	 */
	public XCalWriter(Writer writer, Map<String, String> outputProperties) {
		this(writer, null, outputProperties);
	}

	/**
	 * @param parent the DOM node to add child elements to
	 */
	public XCalWriter(Node parent) {
		this(null, parent, new HashMap<String, String>());
	}

	private XCalWriter(Writer writer, Node parent, Map<String, String> outputProperties) {
		this.writer = writer;

		if (parent instanceof Document) {
			Node root = parent.getFirstChild();
			if (root != null) {
				parent = root;
			}
		}
		this.icalendarElementExists = isICalendarElement(parent);

		try {
			SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactory.newInstance();
			handler = factory.newTransformerHandler();
		} catch (TransformerConfigurationException e) {
			throw new RuntimeException(e);
		}

		Transformer transformer = handler.getTransformer();

		/*
		 * Using Transformer#setOutputProperties(Properties) doesn't work for
		 * some reason for setting the number of indentation spaces.
		 */
		for (Map.Entry<String, String> entry : outputProperties.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			transformer.setOutputProperty(key, value);
		}

		Result result = (writer == null) ? new DOMResult(parent) : new StreamResult(writer);
		handler.setResult(result);
	}

	private boolean isICalendarElement(Node node) {
		if (node == null) {
			return false;
		}

		if (!(node instanceof Element)) {
			return false;
		}

		return XmlUtils.hasQName(node, ICALENDAR);
	}

	/**
	 * Registers the data type of an experimental parameter. Experimental
	 * parameters use the "unknown" data type by default.
	 * @param parameterName the parameter name (e.g. "x-foo")
	 * @param dataType the data type or null to remove
	 */
	public void registerParameterDataType(String parameterName, ICalDataType dataType) {
		parameterName = parameterName.toLowerCase();
		if (dataType == null) {
			parameterDataTypes.remove(parameterName);
		} else {
			parameterDataTypes.put(parameterName, dataType);
		}
	}

	@Override
	protected void _write(ICalendar ical) throws IOException {
		try {
			if (!started) {
				handler.startDocument();

				if (!icalendarElementExists) {
					//don't output a <icalendar> element if the parent is a <icalendar> element
					start(ICALENDAR);
				}

				started = true;
			}

			write((ICalComponent) ical);
		} catch (SAXException e) {
			throw new IOException(e);
		}
	}

	@Override
	protected ICalVersion getTargetVersion() {
		return targetVersion;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void write(ICalComponent component) throws SAXException {
		ICalComponentScribe scribe = index.getComponentScribe(component);
		String name = scribe.getComponentName().toLowerCase();

		start(name);

		List properties = scribe.getProperties(component);
		if (component instanceof ICalendar && component.getProperty(Version.class) == null) {
			properties.add(0, new Version(targetVersion));
		}

		if (!properties.isEmpty()) {
			start(PROPERTIES);

			for (Object propertyObj : properties) {
				context.setParent(component); //set parent here incase a scribe resets the parent
				ICalProperty property = (ICalProperty) propertyObj;
				write(property);
			}

			end(PROPERTIES);
		}

		Collection subComponents = scribe.getComponents(component);
		if (component instanceof ICalendar) {
			//add the VTIMEZONE components that were auto-generated by TimezoneOptions
			Collection<VTimezone> tzs = tzinfo.getComponents();
			for (VTimezone tz : tzs) {
				if (!subComponents.contains(tz)) {
					subComponents.add(tz);
				}
			}
		}
		if (!subComponents.isEmpty()) {
			start(COMPONENTS);
			for (Object subComponentObj : subComponents) {
				ICalComponent subComponent = (ICalComponent) subComponentObj;
				write(subComponent);
			}
			end(COMPONENTS);
		}

		end(name);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void write(ICalProperty property) throws SAXException {
		ICalPropertyScribe scribe = index.getPropertyScribe(property);
		ICalParameters parameters = scribe.prepareParameters(property, context);

		//get the property element to write
		Element propertyElement;
		if (property instanceof Xml) {
			Xml xml = (Xml) property;
			Document value = xml.getValue();
			if (value == null) {
				return;
			}
			propertyElement = value.getDocumentElement();
		} else {
			QName qname = scribe.getQName();
			propertyElement = DOC.createElementNS(qname.getNamespaceURI(), qname.getLocalPart());
			try {
				scribe.writeXml(property, propertyElement, context);
			} catch (SkipMeException e) {
				return;
			}
		}

		start(propertyElement);

		write(parameters);
		write(propertyElement);

		end(propertyElement);
	}

	private void write(Element propertyElement) throws SAXException {
		NodeList children = propertyElement.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);

			if (child instanceof Element) {
				Element element = (Element) child;

				if (element.hasChildNodes()) {
					start(element);
					write(element);
					end(element);
				} else {
					childless(element);
				}

				continue;
			}

			if (child instanceof Text) {
				Text text = (Text) child;
				text(text.getTextContent());
				continue;
			}
		}
	}

	private void write(ICalParameters parameters) throws SAXException {
		if (parameters.isEmpty()) {
			return;
		}

		start(PARAMETERS);

		for (Map.Entry<String, List<String>> parameter : parameters) {
			String parameterName = parameter.getKey().toLowerCase();
			start(parameterName);

			for (String parameterValue : parameter.getValue()) {
				ICalDataType dataType = parameterDataTypes.get(parameterName);
				String dataTypeElementName = (dataType == null) ? "unknown" : dataType.getName().toLowerCase();

				start(dataTypeElementName);
				text(parameterValue);
				end(dataTypeElementName);
			}

			end(parameterName);
		}

		end(PARAMETERS);
	}

	/**
	 * Makes an childless element appear as {@code<foo />} instead of
	 * {@code<foo></foo>}
	 * @param element the element
	 * @throws SAXException
	 */
	private void childless(Element element) throws SAXException {
		Attributes attributes = getElementAttributes(element);
		handler.startElement(element.getNamespaceURI(), "", element.getLocalName(), attributes);
		handler.endElement(element.getNamespaceURI(), "", element.getLocalName());
	}

	private void start(Element element) throws SAXException {
		Attributes attributes = getElementAttributes(element);
		start(element.getNamespaceURI(), element.getLocalName(), attributes);
	}

	private void start(String element) throws SAXException {
		start(element, new AttributesImpl());
	}

	private void start(QName qname) throws SAXException {
		start(qname, new AttributesImpl());
	}

	private void start(QName qname, Attributes attributes) throws SAXException {
		start(qname.getNamespaceURI(), qname.getLocalPart(), attributes);
	}

	private void start(String element, Attributes attributes) throws SAXException {
		start(XCAL_NS, element, attributes);
	}

	private void start(String namespace, String element, Attributes attributes) throws SAXException {
		handler.startElement(namespace, "", element, attributes);
	}

	private void end(Element element) throws SAXException {
		end(element.getNamespaceURI(), element.getLocalName());
	}

	private void end(String element) throws SAXException {
		end(XCAL_NS, element);
	}

	private void end(QName qname) throws SAXException {
		end(qname.getNamespaceURI(), qname.getLocalPart());
	}

	private void end(String namespace, String element) throws SAXException {
		handler.endElement(namespace, "", element);
	}

	private void text(String text) throws SAXException {
		handler.characters(text.toCharArray(), 0, text.length());
	}

	private Attributes getElementAttributes(Element element) {
		AttributesImpl attributes = new AttributesImpl();
		NamedNodeMap attributeNodes = element.getAttributes();
		for (int i = 0; i < attributeNodes.getLength(); i++) {
			Node node = attributeNodes.item(i);

			String localName = node.getLocalName();
			if ("xmlns".equals(localName)) {
				continue;
			}

			attributes.addAttribute(node.getNamespaceURI(), "", node.getLocalName(), "", node.getNodeValue());
		}
		return attributes;
	}

	/**
	 * Terminates the XML document and closes the output stream.
	 */
	public void close() throws IOException {
		try {
			if (!started) {
				handler.startDocument();

				if (!icalendarElementExists) {
					//don't output a <icalendar> element if the parent is a <icalendar> element
					start(ICALENDAR);
				}
			}

			if (!icalendarElementExists) {
				end(ICALENDAR);
			}
			handler.endDocument();
		} catch (SAXException e) {
			throw new IOException(e);
		}

		if (writer != null) {
			writer.close();
		}
	}
}

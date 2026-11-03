package android.text;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

/* What Html's converter gets from TagSoup in AOSP, for the markup apps put in strings: lower-case
 * element names; void elements (br, img, ...) as a start and an end; an end tag closes the elements
 * opened after its own, one with no open element is dropped, and the end of the text closes the
 * rest; entities decoded; comments, doctypes and processing instructions skipped. */
final class ATLHtmlReader implements XMLReader {
	private static final String[] VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "wbr"};
	private static final Map<String, Integer> ENTITIES = new HashMap<>();

	static {
		Object[] entities = {
		    "amp", '&', "lt", '<', "gt", '>', "quot", '"', "apos", '\'', "nbsp", '\u00a0', "copy", '©', "reg", '®',
		    "trade", '™', "hellip", '…', "mdash", '—', "ndash", '–', "lsquo", '‘', "rsquo", '’',
		    "ldquo", '“', "rdquo", '”', "bull", '•', "middot", '·', "deg", '°', "times", '×',
		    "euro", '€', "laquo", '«', "raquo", '»'};
		for (int i = 0; i < entities.length; i += 2)
			ENTITIES.put((String)entities[i], (int)(Character)entities[i + 1]);
	}

	private ContentHandler handler;
	private String text;
	private int at;
	private final ArrayList<String> open = new ArrayList<>();
	private final StringBuilder characters = new StringBuilder();

	@Override
	public void parse(InputSource input) throws IOException, SAXException {
		Reader reader = input.getCharacterStream();
		StringBuilder all = new StringBuilder();
		char[] buffer = new char[4096];
		for (int n; (n = reader.read(buffer)) > 0;)
			all.append(buffer, 0, n);
		text = all.toString();
		at = 0;
		open.clear();
		handler.startDocument();
		while (at < text.length()) {
			char c = text.charAt(at);
			if (c == '<' && tagAhead())
				tag();
			else if (c == '&')
				entity();
			else
				characters.append(text.charAt(at++));
		}
		flush();
		for (int i = open.size() - 1; i >= 0; i--)
			handler.endElement("", open.get(i), open.get(i));
		open.clear();
		handler.endDocument();
	}

	// a '<' before a letter, '/', '!' or '?' starts markup; any other is text
	private boolean tagAhead() {
		if (at + 1 >= text.length())
			return false;
		char next = text.charAt(at + 1);
		return Character.isLetter(next) || next == '/' || next == '!' || next == '?';
	}

	private void flush() throws SAXException {
		if (characters.length() == 0)
			return;
		char[] chars = characters.toString().toCharArray();
		characters.setLength(0);
		handler.characters(chars, 0, chars.length);
	}

	private void entity() {
		int end = text.indexOf(';', at);
		if (end < 0 || end - at > 10) {
			characters.append(text.charAt(at++));
			return;
		}
		String name = text.substring(at + 1, end);
		int code = -1;
		try {
			if (name.startsWith("#x") || name.startsWith("#X"))
				code = Integer.parseInt(name.substring(2), 16);
			else if (name.startsWith("#"))
				code = Integer.parseInt(name.substring(1));
		} catch (NumberFormatException e) {
			code = -1;
		}
		if (code < 0) {
			Integer named = ENTITIES.get(name);
			code = named != null ? named : -1;
		}
		if (code < 0 || code > Character.MAX_CODE_POINT) {
			characters.append(text.charAt(at++));
			return;
		}
		characters.appendCodePoint(code);
		at = end + 1;
	}

	private void tag() throws SAXException {
		flush();
		if (text.startsWith("<!--", at)) {
			int end = text.indexOf("-->", at + 4);
			at = end < 0 ? text.length() : end + 3;
			return;
		}
		if (text.charAt(at + 1) == '!' || text.charAt(at + 1) == '?') {
			int end = text.indexOf('>', at);
			at = end < 0 ? text.length() : end + 1;
			return;
		}
		boolean closing = text.charAt(at + 1) == '/';
		int i = at + (closing ? 2 : 1);
		int nameStart = i;
		while (i < text.length() && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '>' && text.charAt(i) != '/')
			i++;
		String name = text.substring(nameStart, i).toLowerCase(Locale.ROOT);
		AttributesImpl attributes = new AttributesImpl();
		boolean selfClosing = false;
		while (i < text.length() && text.charAt(i) != '>') {
			char c = text.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '/') {
				selfClosing = true;
				i++;
			} else {
				int attrStart = i;
				while (i < text.length() && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '=' && text.charAt(i) != '>' && text.charAt(i) != '/')
					i++;
				String attr = text.substring(attrStart, i).toLowerCase(Locale.ROOT);
				String value = attr;
				while (i < text.length() && Character.isWhitespace(text.charAt(i)))
					i++;
				if (i < text.length() && text.charAt(i) == '=') {
					i++;
					while (i < text.length() && Character.isWhitespace(text.charAt(i)))
						i++;
					if (i < text.length() && (text.charAt(i) == '"' || text.charAt(i) == '\'')) {
						char quote = text.charAt(i);
						int end = text.indexOf(quote, i + 1);
						end = end < 0 ? text.length() : end;
						value = decode(text.substring(i + 1, end));
						i = Math.min(end + 1, text.length());
					} else {
						int valueStart = i;
						while (i < text.length() && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '>')
							i++;
						value = decode(text.substring(valueStart, i));
					}
				}
				if (!attr.isEmpty())
					attributes.addAttribute("", attr, attr, "CDATA", value);
			}
		}
		at = Math.min(i + 1, text.length());
		if (name.isEmpty())
			return;
		boolean isVoid = isVoid(name);
		if (closing) {
			if (isVoid) { // </br>: a line break, as browsers take it
				handler.startElement("", name, name, new AttributesImpl());
				handler.endElement("", name, name);
				return;
			}
			int index = open.lastIndexOf(name);
			if (index < 0)
				return;
			for (int j = open.size() - 1; j >= index; j--)
				handler.endElement("", open.get(j), open.get(j));
			open.subList(index, open.size()).clear();
			return;
		}
		handler.startElement("", name, name, attributes);
		if (isVoid || selfClosing)
			handler.endElement("", name, name);
		else
			open.add(name);
	}

	private static boolean isVoid(String name) {
		for (String v : VOID)
			if (v.equals(name))
				return true;
		return false;
	}

	private static String decode(String value) {
		if (value.indexOf('&') < 0)
			return value;
		ATLHtmlReader reader = new ATLHtmlReader();
		reader.text = value;
		while (reader.at < value.length()) {
			if (value.charAt(reader.at) == '&')
				reader.entity();
			else
				reader.characters.append(value.charAt(reader.at++));
		}
		return reader.characters.toString();
	}

	@Override
	public boolean getFeature(String name) throws SAXNotRecognizedException {
		return false;
	}

	@Override
	public void setFeature(String name, boolean value) {}

	@Override
	public Object getProperty(String name) {
		return null;
	}

	@Override
	public void setProperty(String name, Object value) {}

	@Override
	public void setEntityResolver(EntityResolver resolver) {}

	@Override
	public EntityResolver getEntityResolver() {
		return null;
	}

	@Override
	public void setDTDHandler(DTDHandler handler) {}

	@Override
	public DTDHandler getDTDHandler() {
		return null;
	}

	@Override
	public void setContentHandler(ContentHandler handler) {
		this.handler = handler;
	}

	@Override
	public ContentHandler getContentHandler() {
		return handler;
	}

	@Override
	public void setErrorHandler(ErrorHandler handler) {}

	@Override
	public ErrorHandler getErrorHandler() {
		return null;
	}

	@Override
	public void parse(String systemId) throws IOException, SAXException {
		throw new IOException("ATLHtmlReader parses only a character stream");
	}
}

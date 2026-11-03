package dev.dine.framework.text;

import android.graphics.Typeface;
import android.text.Html;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

public class HtmlTest extends android.test.InstrumentationTestCase {
	private static String spans(Spanned text) {
		StringBuilder out = new StringBuilder();
		for (Object span : text.getSpans(0, text.length(), Object.class)) {
			out.append(span.getClass().getSimpleName());
			if (span instanceof StyleSpan)
				out.append(((StyleSpan)span).getStyle());
			out.append(' ').append(text.getSpanStart(span)).append('-').append(text.getSpanEnd(span)).append(';');
		}
		return out.toString();
	}

	public void testTagsBecomeSpans() {
		Spanned text = Html.fromHtml("a <b>bold</b> and <i>it</i>");
		assertEquals("a bold and it", text.toString());
		assertEquals("StyleSpan" + Typeface.BOLD + " 2-6;StyleSpan" + Typeface.ITALIC + " 11-13;", spans(text));
	}

	public void testEntitiesAndBreaks() {
		assertEquals("x & y < z\nnext", Html.fromHtml("x &amp; y &lt; z<br>next").toString());
		assertEquals("© A", Html.fromHtml("&copy; &#65;").toString());
	}

	public void testUnclosedAndNested() {
		Spanned text = Html.fromHtml("<b>one <u>two</b> three");
		assertEquals("one two three", text.toString());
		assertEquals(1, text.getSpans(0, text.length(), UnderlineSpan.class).length);
		assertEquals(4, text.getSpanStart(text.getSpans(0, text.length(), UnderlineSpan.class)[0]));
	}

	public void testFontAndLink() {
		Spanned text = Html.fromHtml("<font color=\"#ff0000\">red</font> <a href=\"https://example.com\">link</a>");
		ForegroundColorSpan[] colors = text.getSpans(0, text.length(), ForegroundColorSpan.class);
		assertEquals(1, colors.length);
		assertEquals(0xffff0000, colors[0].getForegroundColor());
		URLSpan[] links = text.getSpans(0, text.length(), URLSpan.class);
		assertEquals("https://example.com", links[0].getURL());
	}

	public void testToHtmlRoundTrip() {
		Spanned text = Html.fromHtml("<b>bold</b> plain");
		assertEquals("<p dir=\"ltr\"><b>bold</b> plain</p>\n", Html.toHtml(text));
	}
}

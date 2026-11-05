package dev.dine.framework.text;

import android.graphics.Typeface;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
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

	// TagSoup closes <u> with </b> and opens it again after
	public void testMisnestedTagsRestart() {
		Spanned text = Html.fromHtml("<b>one <u>two</b> three");
		assertEquals("one two three", text.toString());
		UnderlineSpan[] underlines = text.getSpans(0, text.length(), UnderlineSpan.class);
		assertEquals(2, underlines.length);
		assertEquals("4-7 7-13", text.getSpanStart(underlines[0]) + "-" + text.getSpanEnd(underlines[0]) + " "
		                         + text.getSpanStart(underlines[1]) + "-" + text.getSpanEnd(underlines[1]));
	}

	public void testListItemsCloseEachOther() {
		Spanned implicit = Html.fromHtml("<ul><li>a<li>b</ul>");
		Spanned explicit = Html.fromHtml("<ul><li>a</li><li>b</li></ul>");
		assertEquals(explicit.toString(), implicit.toString());
		assertEquals(spans(explicit), spans(implicit));
	}

	public void testNamedEntities() {
		assertEquals("\u00e9\u00fc\u00bd\u2665& x", Html.fromHtml("&eacute;&uuml;&frac12;&hearts;&amp x").toString());
	}

	public void testCssStrikeAndBackground() {
		Spanned text = Html.fromHtml("<span style=\"text-decoration:line-through;\">s</span> <span style=\"background-color:#00FF00;\">g</span>");
		StrikethroughSpan[] strikes = text.getSpans(0, text.length(), StrikethroughSpan.class);
		assertEquals(1, strikes.length);
		assertEquals(0, text.getSpanStart(strikes[0]));
		assertEquals(1, text.getSpanEnd(strikes[0]));
		BackgroundColorSpan[] backgrounds = text.getSpans(0, text.length(), BackgroundColorSpan.class);
		assertEquals(1, backgrounds.length);
		assertEquals(0xff00ff00, backgrounds[0].getBackgroundColor());
		assertEquals(2, text.getSpanStart(backgrounds[0]));
	}

	public void testToHtmlKeepsStrikeAndBackground() {
		SpannableString source = new SpannableString("ab");
		source.setSpan(new StrikethroughSpan(), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		source.setSpan(new BackgroundColorSpan(0xff00ff00), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		Spanned text = Html.fromHtml(Html.toHtml(source));
		assertEquals(1, text.getSpans(0, text.length(), StrikethroughSpan.class).length);
		BackgroundColorSpan[] backgrounds = text.getSpans(0, text.length(), BackgroundColorSpan.class);
		assertEquals(1, backgrounds.length);
		assertEquals(0xff00ff00, backgrounds[0].getBackgroundColor());
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

package dev.dine.framework.graphics;

import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import junit.framework.TestCase;

/* Relations only: each build has its own fonts (headless: fixed metrics). */
public class TextTest extends TestCase {
	private static TextPaint paint(float size) {
		TextPaint paint = new TextPaint();
		paint.setTextSize(size);
		return paint;
	}

	private static StaticLayout layout(String text, TextPaint paint, int width) {
		return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
	}

	public void testWidthGrowsWithLengthAndSize() {
		Paint small = paint(20), big = paint(40);
		assertEquals(0f, small.measureText(""));
		assertTrue(small.measureText("aa") > small.measureText("a"));
		assertTrue(big.measureText("abc") > small.measureText("abc"));
		assertEquals(small.measureText("abc"), small.measureText("xabcx", 1, 4));
		assertEquals(small.measureText("abc"), small.measureText(new char[] {'x', 'a', 'b', 'c'}, 1, 3));
	}

	public void testDefaultSizeIsTwelve() {
		Paint paint = new Paint();
		assertEquals(12f, paint.getTextSize());
		assertTrue(paint.measureText("a") > 0);
	}

	public void testFontMetrics() {
		Paint paint = paint(30);
		Paint.FontMetrics metrics = paint.getFontMetrics();
		assertTrue(metrics.ascent < 0);
		assertTrue(metrics.descent > 0);
		assertTrue(metrics.top <= metrics.ascent);
		assertTrue(metrics.descent <= metrics.bottom);
		assertEquals(metrics.descent - metrics.ascent + metrics.leading, paint.getFontMetrics(null));
		assertEquals(metrics.ascent, paint.ascent());
		assertEquals(metrics.descent, paint.descent());
	}

	public void testFontMetricsIntRoundsAsSkia() {
		Paint paint = paint(30);
		Paint.FontMetrics metrics = paint.getFontMetrics();
		Paint.FontMetricsInt ints = paint.getFontMetricsInt();
		assertEquals((int)Math.floor(metrics.top), ints.top);
		assertEquals(Math.round(metrics.ascent), ints.ascent);
		assertEquals(Math.round(metrics.descent), ints.descent);
		assertEquals((int)Math.ceil(metrics.bottom), ints.bottom);
		assertEquals(Math.round(metrics.leading), ints.leading);
		assertEquals(ints.descent - ints.ascent + ints.leading, paint.getFontMetricsInt(null));
	}

	public void testWidthsSumToTheMeasure() {
		Paint paint = paint(30);
		String text = "Hello, world";
		float[] widths = new float[text.length()];
		assertEquals(text.length(), paint.getTextWidths(text, widths));
		float sum = 0;
		for (float width : widths)
			sum += width;
		assertEquals(paint.measureText(text), sum, 0.01f);
	}

	public void testBoundsAroundTheBaseline() {
		Paint paint = paint(30);
		Rect bounds = new Rect();
		paint.getTextBounds("Ag", 0, 2, bounds);
		assertTrue(bounds.left >= -1);
		assertTrue(bounds.right <= Math.ceil(paint.measureText("Ag")) + 1);
		assertTrue(bounds.top < 0);
		assertTrue(bounds.bottom > 0);
		paint.getTextBounds("", 0, 0, bounds);
		assertTrue(bounds.isEmpty());
	}

	public void testBreakText() {
		Paint paint = paint(30);
		float three = paint.measureText("abc") + 0.5f;
		float[] measured = new float[1];
		assertEquals(3, paint.breakText("abcdef", true, three, measured));
		assertTrue(measured[0] <= three);
		assertEquals(3, paint.breakText("abcdef", false, three, null));
		assertEquals(6, paint.breakText("abcdef", true, 10000, null));
	}

	public void testBoldIsNotNarrower() {
		Paint regular = paint(30), bold = paint(30);
		bold.setTypeface(Typeface.DEFAULT_BOLD);
		assertTrue(bold.measureText("Hello") >= regular.measureText("Hello"));
	}

	public void testMonospace() {
		Paint mono = paint(30);
		mono.setTypeface(Typeface.MONOSPACE);
		assertEquals(mono.measureText("WWW"), mono.measureText("iii"), 0.01f);
	}

	public void testTypefaceKeepsStyle() {
		Typeface typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC);
		assertTrue(typeface.isBold());
		assertTrue(typeface.isItalic());
		assertEquals(Typeface.BOLD_ITALIC, typeface.getStyle());
		Paint paint = new Paint();
		assertNull(paint.getTypeface());
		paint.setTypeface(typeface);
		assertSame(typeface, paint.getTypeface());
		assertSame(typeface, new Paint(paint).getTypeface());
		paint.reset();
		assertNull(paint.getTypeface());
	}

	public void testTypefacesAreShared() {
		assertSame(Typeface.DEFAULT_BOLD, Typeface.create((String)null, Typeface.BOLD));
		assertSame(Typeface.SERIF, Typeface.create("serif", Typeface.NORMAL));
		assertSame(Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC), Typeface.create("monospace", Typeface.ITALIC));
	}

	public void testLayoutLines() {
		StaticLayout layout = layout("a\nb", paint(30), 1000);
		assertEquals(2, layout.getLineCount());
		assertEquals(0, layout.getLineStart(0));
		assertEquals(2, layout.getLineEnd(0));
		assertEquals(2, layout.getLineStart(1));
		assertEquals(3, layout.getLineEnd(1));
		assertEquals(layout.getLineBottom(0), layout.getLineTop(1));
		assertTrue(layout.getLineBaseline(0) > 0);
		assertEquals(2 * layout.getLineBottom(0), layout.getHeight());
		assertEquals(1, layout.getLineForOffset(2));
		assertEquals(1, layout.getLineForVertical(layout.getLineTop(1) + 1));
	}

	public void testLayoutWrapsAtSpaces() {
		TextPaint paint = paint(30);
		assertEquals(1, layout("one two three", paint, 10000).getLineCount());
		StaticLayout narrow = layout("one two three", paint, (int)Math.ceil(paint.measureText("one two")));
		assertEquals(2, narrow.getLineCount());
		assertEquals(8, narrow.getLineStart(1));
		assertEquals(0f, narrow.getPrimaryHorizontal(0));
		assertEquals(paint.measureText("one"), narrow.getPrimaryHorizontal(3), 0.5f);
		assertEquals(3, narrow.getOffsetForHorizontal(0, paint.measureText("one")));
	}
}

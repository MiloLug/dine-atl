package dev.dine.framework.text;

import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.UnderlineSpan;

// what TextView reads back from a span's TextPaint changes to style its runs
public class SpanTest extends android.test.InstrumentationTestCase {
	private static TextPaint paint() {
		TextPaint paint = new TextPaint();
		paint.setTextSize(20);
		paint.setColor(0xff000000);
		return paint;
	}

	public void testDrawState() {
		TextPaint paint = paint();
		new ForegroundColorSpan(0xff00ff00).updateDrawState(paint);
		new BackgroundColorSpan(0xff0000ff).updateDrawState(paint);
		new UnderlineSpan().updateDrawState(paint);
		new StrikethroughSpan().updateDrawState(paint);
		assertEquals(0xff00ff00, paint.getColor());
		assertEquals(0xff0000ff, paint.bgColor);
		assertTrue(paint.isUnderlineText());
		assertTrue(paint.isStrikeThruText());
	}

	public void testMeasureState() {
		TextPaint paint = paint();
		new RelativeSizeSpan(1.5f).updateMeasureState(paint);
		assertEquals(30f, paint.getTextSize());
		new AbsoluteSizeSpan(12).updateMeasureState(paint);
		assertEquals(12f, paint.getTextSize());
		new StyleSpan(Typeface.BOLD).updateMeasureState(paint);
		Typeface face = paint.getTypeface();
		assertTrue("bold", (face != null && face.isBold()) || paint.isFakeBoldText());
	}

	public void testSuperscriptShiftsUp() {
		TextPaint paint = paint();
		new SuperscriptSpan().updateMeasureState(paint);
		assertTrue("shift " + paint.baselineShift, paint.baselineShift < 0);
	}
}

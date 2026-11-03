package android.text.style;

import android.graphics.Paint;
import android.text.TextPaint;

public abstract class ReplacementSpan extends MetricAffectingSpan {

	public abstract int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm);

	@Override
	public void updateMeasureState(TextPaint p) {}

	@Override
	public void updateDrawState(TextPaint ds) {}
}

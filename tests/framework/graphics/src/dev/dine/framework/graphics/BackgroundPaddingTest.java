package dev.dine.framework.graphics;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

// a view's padding and least size from its background, as Android 10's View
public class BackgroundPaddingTest extends android.test.InstrumentationTestCase {
	private Context context() {
		return getInstrumentation().getTargetContext();
	}

	private Drawable drawable(int id) {
		return context().getResources().getDrawable(id);
	}

	private static void assertPadding(View view, int left, int top, int right, int bottom) {
		assertEquals("padding", left + "," + top + "," + right + "," + bottom,
		             view.getPaddingLeft() + "," + view.getPaddingTop() + "," + view.getPaddingRight() + "," + view.getPaddingBottom());
	}

	private static final int UNSPECIFIED = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

	private static void laidOut(View view) {
		view.measure(UNSPECIFIED, UNSPECIFIED);
		view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
		assertFalse("laid out", view.isLayoutRequested());
	}

	public void testBackgroundPads() {
		View view = new View(context());
		view.setBackground(drawable(R.drawable.inset));
		assertPadding(view, 2, 2, 2, 2);
	}

	public void testXmlPaddingWinsOnItsEdge() {
		View view = LayoutInflater.from(context()).inflate(R.layout.padded, null);
		assertPadding(view, 7, 2, 2, 2);
	}

	public void testUnpaddedBackgroundKeepsPadding() {
		View view = new View(context());
		view.setPadding(1, 2, 3, 4);
		view.setBackground(new ColorDrawable(0xff000000));
		assertPadding(view, 1, 2, 3, 4);
		view.setBackground(drawable(R.drawable.layers_none));
		assertPadding(view, 1, 2, 3, 4);
	}

	// Android 10: a padded background replaces padding set before it
	public void testPaddedBackgroundReplacesPadding() {
		View view = new View(context());
		view.setPadding(9, 9, 9, 9);
		view.setBackground(drawable(R.drawable.frame));
		assertPadding(view, 1, 2, 1, 3);
	}

	public void testPaddingChangeLaysOutAgain() {
		View view = new View(context());
		laidOut(view);
		view.setPadding(1, 1, 1, 1);
		assertTrue(view.isLayoutRequested());
	}

	// a colour after a background with a least size: the least size goes
	public void testColorAfterImageLaysOutAgain() {
		View view = new View(context());
		view.setBackground(drawable(R.drawable.photo));
		laidOut(view);
		view.setBackgroundColor(0xff000000);
		assertTrue(view.isLayoutRequested());
	}

	private void assertAtLeastPhoto(View view) {
		Drawable photo = drawable(R.drawable.photo);
		view.setBackground(photo);
		view.measure(UNSPECIFIED, UNSPECIFIED);
		assertTrue(view.getClass().getSimpleName() + " " + view.getMeasuredWidth() + "x" + view.getMeasuredHeight(),
		           view.getMeasuredWidth() >= photo.getIntrinsicWidth() && view.getMeasuredHeight() >= photo.getIntrinsicHeight());
	}

	public void testLayoutIsAtLeastItsBackground() {
		assertAtLeastPhoto(new FrameLayout(context()));
	}

	public void testTextIsAtLeastItsBackground() {
		TextView text = new TextView(context());
		text.setText("x");
		assertAtLeastPhoto(text);
	}

	public void testImageIsAtLeastItsBackground() {
		assertAtLeastPhoto(new ImageView(context()));
	}

	// View's minWidth, which TextView's own minWidth attribute doesn't cover
	public void testTextMinimumWidth() {
		TextView text = new TextView(context());
		text.setText("x");
		text.setMinimumWidth(200);
		text.measure(MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST), UNSPECIFIED);
		assertEquals(200, text.getMeasuredWidth());
		text.measure(MeasureSpec.makeMeasureSpec(100, MeasureSpec.AT_MOST), UNSPECIFIED);
		assertEquals(100, text.getMeasuredWidth());
	}

	public void testRelativePaddingIsLeftToRight() {
		View view = new View(context());
		view.setPaddingRelative(5, 1, 6, 2);
		assertPadding(view, 5, 1, 6, 2);
	}
}

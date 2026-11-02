package dev.dine.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

/* A view drawn through Canvas, text, a Layout, paths, arcs, gradients, a layer and a nine-patch
 * included, for run-draw.sh: headless compares its display lists with draw.displaylist, gtk draws
 * it. It redraws after invalidate(), not after a layout at the same size, and after showing again
 * from INVISIBLE with an invalidate() while hidden; then it finishes. Beside it, a view scrolled by
 * 10 px keeps its selector background in place. */
public class DrawActivity extends Activity {
	static final String TAG = "widgets";
	protected FrameLayout root;

	private class DrawView extends View {
		private final Bitmap photo = BitmapFactory.decodeResource(getResources(), R.drawable.photo);
		private final Drawable frame = getResources().getDrawable(R.drawable.frame);
		private final BitmapDrawable filtered = new BitmapDrawable(getResources(), photo);
		private int draws;

		DrawView(Context context) {
			super(context);
		}

		@Override
		public void onDraw(Canvas canvas) {
			Paint fill = new Paint();
			fill.setColor(0xffff0000);
			canvas.drawRect(10, 10, 110, 60, fill);

			Paint stroke = new Paint();
			stroke.setColor(0xff0000ff);
			stroke.setStyle(Paint.Style.STROKE);
			stroke.setStrokeWidth(4);
			canvas.drawLine(0, 0, 100, 100, stroke);
			canvas.drawLines(new float[] {0, 120, 50, 120, 50, 130, 100, 130}, stroke);

			Matrix matrix = new Matrix();
			matrix.preScale(2, 2, 10, 10);
			matrix.preRotate(90, 50, 50);
			canvas.save();
			canvas.concat(matrix);
			canvas.drawRect(0, 0, 20, 10, fill);
			canvas.restore();

			Paint copy = new Paint(fill);
			fill.setColor(0xff00ff00);
			canvas.drawRect(120, 10, 170, 60, copy);

			Paint blue = new Paint();
			blue.setColor(0xff0000ff);
			canvas.save();
			canvas.clipRect(180, 10, 200, 30);
			canvas.drawRect(170, 0, 210, 40, blue);
			canvas.restore();
			canvas.save();
			canvas.clipRect(220, 10, 240, 30, Region.Op.DIFFERENCE);
			canvas.drawRect(210, 0, 250, 40, blue);
			canvas.restore();
			canvas.drawBitmap(photo, null, new Rect(260, 0, 324, 64), null);

			// a paint with an xfermode draws no rect (ATLCanvas.drawRect), its copy neither
			Paint clear = new Paint(fill);
			clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
			canvas.drawRect(0, 140, 10, 150, new Paint(clear));

			Paint text = new Paint();
			text.setTextSize(30);
			text.setTypeface(Typeface.DEFAULT_BOLD);
			canvas.drawText("Dine bold", 10, 190, text);
			Paint serif = new Paint(text);
			serif.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
			canvas.drawText("serif italic", 10, 230, serif);
			Paint mono = new Paint(text);
			mono.setTypeface(Typeface.MONOSPACE);
			mono.setTextAlign(Paint.Align.CENTER);
			canvas.drawText("mono, centered", 160, 270, mono);
			Paint outline = new Paint(stroke);
			outline.setStrokeWidth(1);
			outline.setTextSize(30);
			canvas.drawText("outline", 10, 310, outline);
			StaticLayout layout = new StaticLayout("two lines from a Layout", new TextPaint(text), 300, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
			canvas.save();
			canvas.translate(330, 160);
			layout.draw(canvas);
			canvas.restore();

			Path shape = new Path();
			shape.moveTo(10, 330);
			shape.lineTo(60, 330);
			shape.quadTo(60, 380, 10, 380);
			shape.close();
			Path circle = new Path();
			circle.addCircle(35, 355, 20, Path.Direction.CW);
			canvas.save();
			canvas.clipPath(circle);
			canvas.drawPath(shape, fill);
			canvas.restore();
			canvas.drawArc(new RectF(70, 330, 120, 380), 0, 90, true, blue);
			canvas.drawArc(new RectF(130, 330, 180, 380), 0, 360, true, stroke); // the whole oval: no radius line
			canvas.drawArc(new RectF(190, 330, 240, 380), 180, 90, false, stroke);
			Paint gradient = new Paint();
			gradient.setShader(new LinearGradient(250, 330, 300, 330, 0xffff0000, 0xff0000ff, Shader.TileMode.MIRROR));
			canvas.drawRect(250, 330, 350, 380, gradient);
			Matrix shift = new Matrix();
			shift.setTranslate(10, 0);
			gradient.getShader().setLocalMatrix(shift); // after setShader: the next draw takes it
			canvas.drawRect(250, 385, 350, 395, gradient);
			Paint sweep = new Paint();
			sweep.setShader(new SweepGradient(505, 355, new int[] {0xffff0000, 0xff00ff00, 0xff0000ff}, null));
			canvas.drawCircle(505, 355, 25, sweep);
			GradientDrawable ring = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xffff0000, 0xff0000ff});
			ring.setGradientType(GradientDrawable.SWEEP_GRADIENT);
			ring.setBounds(540, 330, 590, 380);
			ring.draw(canvas);
			canvas.saveLayerAlpha(360, 330, 410, 380, 128);
			canvas.drawRect(360, 330, 410, 380, blue);
			canvas.drawCircle(385, 355, 10, copy);
			canvas.restore();
			frame.setBounds(420, 330, 460, 350); // stretched to 40x20; tinted below
			frame.draw(canvas);
			frame.setTint(0xff00ff00);
			frame.setBounds(470, 330, 473, 333); // smaller than its fixed parts: they shrink
			frame.draw(canvas);
			frame.setTintList(null);
			filtered.setColorFilter(0xff0000ff, PorterDuff.Mode.SRC_ATOP);
			filtered.setBounds(480, 330, 496, 346);
			filtered.draw(canvas);

			draws++;
			Log.i(TAG, "widgets draw layout lines " + layout.getLineCount());
			Log.i(TAG, "widgets draw " + draws);
			if (draws == 1) {
				post(new Runnable() {
					public void run() {
						invalidate();
					}
				});
			} else if (draws == 2) {
				post(new Runnable() {
					public void run() {
						afterLayout(new Runnable() {
							public void run() {
								hide();
							}
						});
					}
				});
			} else if (draws == 3) {
				post(new Runnable() {
					public void run() {
						Log.i(TAG, "widgets draw done");
						done();
					}
				});
			}
		}

		private void hide() {
			Log.i(TAG, "widgets draw hidden");
			setVisibility(View.INVISIBLE);
			invalidate();
			afterLayout(new Runnable() {
				public void run() {
					Log.i(TAG, "widgets draw shown");
					setVisibility(View.VISIBLE);
				}
			});
		}

		// next after a layout pass at the same size, and after that pass's draws
		private void afterLayout(final Runnable next) {
			getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
				public void onGlobalLayout() {
					getViewTreeObserver().removeOnGlobalLayoutListener(this);
					post(next);
				}
			});
			requestLayout();
		}
	}

	protected void done() {
		finish();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		root = new FrameLayout(this);
		root.addView(new DrawView(this));
		View scrolled = new View(this);
		scrolled.setBackgroundResource(R.drawable.shared_selector);
		scrolled.scrollTo(0, 10);
		root.addView(scrolled, new FrameLayout.LayoutParams(40, 40, Gravity.BOTTOM));
		ImageView tinted = new ImageView(this);
		tinted.setImageResource(R.drawable.photo);
		tinted.setImageTintList(ColorStateList.valueOf(0xffff0000));
		root.addView(tinted, new FrameLayout.LayoutParams(16, 16, Gravity.BOTTOM | Gravity.RIGHT));
		setContentView(root);
	}
}

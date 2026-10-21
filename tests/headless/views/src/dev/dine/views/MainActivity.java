package dev.dine.views;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/* Drops views, a dialog and drawables and has them collected: during onCreate, and after the first
 * layout for a view that was laid out, with a layout pass after its free. Finishing stops the loop. */
public class MainActivity extends Activity {
	private static final String TAG = "views";

	private Handler main;
	private LinearLayout content;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		main = new Handler(Looper.getMainLooper());
		content = build();
		collect();
		removeWhenLaidOut();
	}

	// in a method of its own: the interpreter keeps every register of a live frame reachable
	private LinearLayout build() {
		setContentView(column("x", "y"));
		LinearLayout content = column("a", "b", "c", "d");
		View b = content.getChildAt(1);
		content.removeView(b);
		content.addView(b, 0);
		content.removeView(content.getChildAt(3)); // d
		setContentView(content); // drops x and y's column
		new AlertDialog.Builder(this).setMessage("never shown").create();
		new Drawable() { // draws itself: the backend keeps a record for it
			public void draw(Canvas canvas) {}
			public void setAlpha(int alpha) {}
			public void setColorFilter(ColorFilter filter) {}
			public int getOpacity() {
				return PixelFormat.TRANSLUCENT;
			}
		};
		gskDrawables();
		return content;
	}

	// gtk only: on headless and uikit the first call throws, before any object with a native finalizer exists
	private void gskDrawables() {
		try {
			Drawable missing = Drawable.createFromPath(new File(getFilesDir(), "missing.png").getPath());
			Log.i(TAG, "views createFromPath missing: " + (missing == null));
			Bitmap bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888);
			StateListDrawable states = new StateListDrawable();
			states.addState(new int[0], new NinePatchDrawable(getResources(), bitmap, ninePatchChunk(), new Rect(), null));
			Log.i(TAG, "views gsk drawables");
		} catch (UnsatisfiedLinkError e) {
		}
	}

	// Res_png_9patch, deserialized: ATL computes the offsets and reads native order; one colour per patch
	private static byte[] ninePatchChunk() {
		ByteBuffer chunk = ByteBuffer.allocate(32 + 4 * (2 + 2 + 9)).order(ByteOrder.nativeOrder());
		chunk.put((byte)1).put((byte)2).put((byte)2).put((byte)9);
		chunk.position(32);
		chunk.putInt(1).putInt(2).putInt(1).putInt(2);
		for (int i = 0; i < 9; i++)
			chunk.putInt(1);
		return chunk.array();
	}

	private LinearLayout column(String... texts) {
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		for (String text : texts) {
			TextView view = new TextView(this);
			view.setText(text);
			column.addView(view);
		}
		return column;
	}

	// the frees are posted to the main loop: they run after this callback
	private static void collect() {
		for (int i = 0; i < 2; i++) {
			Runtime.getRuntime().gc();
			System.runFinalization();
		}
	}

	private void removeWhenLaidOut() {
		if (content.getWidth() == 0) {
			main.postDelayed(new Runnable() {
				public void run() {
					removeWhenLaidOut();
				}
			}, 10);
			return;
		}
		removeC();
		collect();
		// delayed, so the queued layout pass and the frees run first: this pass walks the tree after c's free
		main.postDelayed(new Runnable() {
			public void run() {
				content.requestLayout();
				main.postDelayed(new Runnable() {
					public void run() {
						widgets();
						Log.i(TAG, "views ui mode type " + (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK));
						Log.i(TAG, "views done");
						finish();
					}
				}, 10);
			}
		}, 10);
	}

	// the inflate-time natives on every backend: attach, keep screen on, widgets, Matrix, Paint, fullscreen
	private void widgets() {
		LinearLayout row = new LinearLayout(this);
		row.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
			public void onViewAttachedToWindow(View v) {
				Log.i(TAG, "views row attached " + v.isAttachedToWindow());
			}
			public void onViewDetachedFromWindow(View v) {
				Log.i(TAG, "views row detached");
			}
		});
		ImageView image = new ImageView(this);
		image.setImageDrawable(new ColorDrawable(0xFF00FF00));
		image.setScaleType(ImageView.ScaleType.CENTER_CROP);
		ProgressBar progress = new ProgressBar(this);
		progress.setIndeterminate(true);
		View keep = new View(this);
		keep.setKeepScreenOn(true);
		row.addView(image);
		row.addView(progress);
		row.addView(keep);
		row.setBackground(new ColorDrawable(0xFF202020));
		content.addView(row);
		image.setVisibility(View.INVISIBLE);
		progress.setAlpha(0.5f);
		row.setTranslationX(4);
		Matrix matrix = new Matrix();
		matrix.setRotate(90);
		float[] point = {1, 0};
		matrix.mapPoints(point);
		Paint paint = new Paint();
		paint.setStrokeWidth(2);
		Log.i(TAG, "views matrix " + Math.round(point[0]) + "," + Math.round(point[1]) + " paint " + paint.getStrokeWidth());
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		content.removeView(row);
		Log.i(TAG, "views widgets");
	}

	// out of the caller's frame, like build
	private void removeC() {
		content.removeView(content.getChildAt(2));
	}
}

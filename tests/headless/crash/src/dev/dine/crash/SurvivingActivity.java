package dev.dine.crash;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

/* A view's first pass throws in the callback the subclass names. ATL prints and clears that one, so
 * the app goes on: a later message logs it and finishes. */
abstract class SurvivingActivity extends Activity {
	private final String where;
	private boolean thrown;

	SurvivingActivity(String where) {
		this.where = where;
	}

	void crashOnce(String callback) {
		if (!thrown && callback.equals(where)) {
			thrown = true;
			throw new IllegalStateException("crash in " + where);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		CreateActivity.logCheckJni();
		setContentView(new View(this) {
			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				crashOnce("onMeasure");
				super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			}

			@Override
			public void onDraw(android.graphics.Canvas canvas) {
				crashOnce("onDraw");
			}
		});
		new Handler(Looper.getMainLooper()).postDelayed(() -> {
			Log.i("crash", "crash survived " + where + " " + thrown);
			finish();
		}, 300);
	}
}

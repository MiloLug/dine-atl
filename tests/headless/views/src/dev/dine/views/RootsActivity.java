package dev.dine.views;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/* Two dialogs over the activity; measuring the lower one dismisses the upper, so the windows the
 * layout pass walks change under it. The run's MALLOC_PERTURB_ and the heap's own bookkeeping in a
 * freed block make a pass that goes on through the list it had crash. Then it stays, for
 * run-views.sh's dump (views-roots.viewtree): the lower dialog alone. */
public class RootsActivity extends Activity {
	private Dialog upper;
	private boolean dismissed;

	private final class Dismisser extends View {
		Dismisser(Activity activity) {
			super(activity);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			if (!dismissed) {
				dismissed = true;
				upper.dismiss();
			}
			setMeasuredDimension(100, 50);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new TextView(this));
		Dialog lower = new Dialog(this);
		lower.setContentView(new Dismisser(this));
		lower.show();
		upper = new Dialog(this);
		TextView text = new TextView(this);
		text.setText("upper");
		upper.setContentView(text);
		upper.show();
		getWindow().getDecorView().post(new Runnable() {
			public void run() {
				Log.i("views", "views roots dismissed " + dismissed);
			}
		});
	}
}

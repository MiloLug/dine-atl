package dev.dine.windows;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

/* A view of the activity and one of its dialog, each logging "draw <who>" when it is drawn: the
 * records draw a window over the activity as they draw the activity's own. */
public class DrawActivity extends Activity {
	private static View drawing(Context context, final String who) {
		View view = new View(context) {
			@Override
			public void onDraw(Canvas canvas) {
				Log.i("windows", "draw " + who);
			}
		};
		view.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
		return view;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(drawing(this, "activity"));
		Dialog dialog = new Dialog(this);
		dialog.setContentView(drawing(this, "dialog"));
		dialog.getWindow().setLayout(100, 50);
		dialog.show();
	}
}

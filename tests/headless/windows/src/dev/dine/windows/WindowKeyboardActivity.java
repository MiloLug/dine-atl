package dev.dine.windows;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;

/* A window over the activity under a keyboard the host reports as the IME inset, in the lines of
 * KeyboardActivity, with the content's top on screen after the size, and at the end whether the
 * field's bottom is above the keyboard's top (the root is the content of the window it names):
 * - DIALOG: a 400x300 dialog, its field at the bottom, taking the soft input mode of its theme
 *   (adjustPan) or adjustResize;
 * - BOTTOM: the pan one at the bottom of the window, wholly under the keyboard on a phone, with
 *   SOFT_INPUT_STATE_VISIBLE: the keyboard comes with it;
 * - TALL: the pan one as tall as the window;
 * - OVER: the pan one of WRAP_CONTENT height, 400 px: taller than the room above the keyboard;
 * - VISIBLE: the dialog with SOFT_INPUT_STATE_VISIBLE, which opens the keyboard with its window;
 * - POPUP: a ListPopupWindow shown below a field of the adjustPan activity, 400 px down, where the
 *   keyboard covers the field's bottom and the popup. */
public abstract class WindowKeyboardActivity extends Activity {
	static final int PAN = 0, RESIZE = 1, POPUP = 2, VISIBLE = 3, TALL = 4, BOTTOM = 5, OVER = 6;

	private final String name;
	private final int mode;
	private String last;

	WindowKeyboardActivity(String name, int mode) {
		this.name = name;
		this.mode = mode;
	}

	private void report(final View root, final View field) {
		root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			public void onGlobalLayout() {
				WindowInsets insets = root.getRootWindowInsets();
				int ime = insets != null ? insets.getInsets(WindowInsets.Type.ime()).bottom : 0;
				int[] at = new int[2], top = new int[2];
				field.getLocationInWindow(at);
				root.getLocationOnScreen(top);
				Rect visible = new Rect();
				root.getWindowVisibleDisplayFrame(visible);
				int[] on_screen = new int[2];
				field.getLocationOnScreen(on_screen);
				boolean above = on_screen[1] + field.getHeight() <= visible.bottom; // clear of the keyboard
				String line = "keyboard: " + name + " ime " + ime + " size " + root.getWidth() + "x" + root.getHeight() + " top " + top[1] + " field " + at[1]
				              + " above " + above;
				if (!line.equals(last))
					Log.i("windows", line);
				last = line;
			}
		});
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView behind = new TextView(this);
		behind.setText("behind");
		final EditText field = new EditText(this);
		field.setHint(name + " field");
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		if (mode == POPUP) {
			column.addView(behind, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400)); // the field's top
			column.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			Button open = new Button(this);
			open.setText("open");
			column.addView(open);
			setContentView(column);
			final ListPopupWindow popup = new ListPopupWindow(this);
			popup.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new String[] {"one", "two"}));
			popup.setAnchorView(field);
			popup.setWidth(200);
			popup.setHeight(100);
			popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
			open.setOnClickListener(new View.OnClickListener() {
				public void onClick(View view) {
					popup.show();
					Log.i("windows", "keyboard: popup shown");
				}
			});
			field.requestFocus();
			report(column, field);
			return;
		}
		// the field at the bottom: the spacer takes the rest of the window, or makes the content 400 px high
		column.addView(new View(this), mode == OVER ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 383)
		                                             : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		column.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		setContentView(behind);
		Dialog dialog = new Dialog(this);
		dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
		dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
		if (mode == RESIZE)
			dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		if (mode == BOTTOM) { // the keyboard comes with the dialog: stateVisible
			dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
			dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
		}
		if (mode == VISIBLE)
			dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		dialog.setContentView(column);
		dialog.getWindow().setLayout(400, mode == TALL ? ViewGroup.LayoutParams.MATCH_PARENT : mode == OVER ? ViewGroup.LayoutParams.WRAP_CONTENT : 300);
		field.requestFocus(); // before the show, as an input dialog does
		dialog.show();
		report(column, field);
		Log.i("windows", "dialog keyboard shown");
	}
}

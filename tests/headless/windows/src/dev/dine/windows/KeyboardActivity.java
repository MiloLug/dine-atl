package dev.dine.windows;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/* A field at the screen's bottom, focused from the start, under a keyboard the host reports as the
 * IME inset. Each layout that changes what the content sees logs "keyboard: <name> ime <px> nav <px>
 * size <w>x<h> field <y>": the bottom IME and navigation bar insets it gets, its size and the
 * field's top in the window. With scroll the field sits in a ScrollView, 140 px above the display's
 * bottom (400 px down on the headless one). */
public abstract class KeyboardActivity extends Activity {
	private final String name;
	private final boolean scroll;
	private String last;

	KeyboardActivity(String name, boolean scroll) {
		this.name = name;
		this.scroll = scroll;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final EditText field = new EditText(this);
		field.setHint(name + " field");
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);
		final View root;
		if (scroll) {
			int down = getResources().getDisplayMetrics().heightPixels - 140;
			column.addView(new View(this), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, down));
			column.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			ScrollView scroller = new ScrollView(this);
			scroller.addView(column, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			root = scroller;
		} else {
			column.addView(new View(this), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
			column.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			root = column;
		}
		setContentView(root);
		field.requestFocus();
		root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			public void onGlobalLayout() {
				WindowInsets insets = root.getRootWindowInsets();
				int ime = insets != null ? insets.getInsets(WindowInsets.Type.ime()).bottom : 0;
				int nav = insets != null ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom : 0;
				int[] at = new int[2];
				field.getLocationInWindow(at);
				String line = "keyboard: " + name + " ime " + ime + " nav " + nav + " size " + root.getWidth() + "x" + root.getHeight() + " field " + at[1];
				if (!line.equals(last))
					Log.i("windows", line);
				last = line;
			}
		});
	}
}

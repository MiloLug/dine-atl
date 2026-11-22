package dev.dine.widgets;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentCallbacks;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/* Text fields near the top of the screen, for run-text.sh on headless,
 * smoke atl-text on iOS and checks by hand on gtk. Logged text escapes newlines and non-ASCII. */
public class TextActivity extends Activity {
	private static final String TAG = "edits";

	private EditText name, notes;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);

		// a password a toggle shows and hides again
		final EditText reveal = field(column, "reveal");
		reveal.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		reveal.setImeOptions(EditorInfo.IME_ACTION_NEXT);

		// a custom keypad's display: a tap focuses it and shows no keyboard; the app shows one itself
		final EditText calc = field(column, "calc");
		calc.setInputType(InputType.TYPE_CLASS_TEXT);
		calc.setImeOptions(EditorInfo.IME_ACTION_DONE);
		calc.setShowSoftInputOnFocus(false);

		// no input type: a tap focuses it and shows no keyboard, the toolkit's edits are refused
		final EditText locked = field(column, "locked");
		locked.setImeOptions(EditorInfo.IME_ACTION_DONE);
		locked.setKeyListener(null);

		// a field no tap focuses
		EditText nofocus = field(column, "nofocus");
		nofocus.setInputType(InputType.TYPE_CLASS_TEXT);
		nofocus.setImeOptions(EditorInfo.IME_ACTION_DONE);
		nofocus.setFocusable(false);

		// a date: the key listener keeps digits and the separators, the keyboard is the one with them
		EditText date = field(column, "date");
		date.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
		date.setImeOptions(EditorInfo.IME_ACTION_DONE);

		name = field(column, "name");
		name.setInputType(InputType.TYPE_CLASS_TEXT);
		name.setImeOptions(EditorInfo.IME_ACTION_NEXT);
		name.setFilters(new InputFilter[] {new NoDigits(), new InputFilter.LengthFilter(10)});
		name.addTextChangedListener(new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				Log.i(TAG, "text before " + start + " " + count + " " + after + " \"" + escaped(s) + "\"");
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
				Log.i(TAG, "text on " + start + " " + before + " " + count + " \"" + escaped(s) + "\"");
			}

			public void afterTextChanged(Editable s) {
				Log.i(TAG, "text after \"" + escaped(s) + "\"");
			}
		});
		// what a hardware Enter finds in the field
		name.setOnKeyListener(new View.OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)
					Log.i(TAG, "text enter-key \"" + escaped(name.getText()) + "\"");
				return false;
			}
		});
		name.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				Log.i(TAG, "text action name " + actionId);
				return false;
			}
		});

		notes = field(column, "notes");
		final EditText pin = field(column, "pin");
		pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
		pin.setImeOptions(EditorInfo.IME_ACTION_DONE);
		pin.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4)});

		// return is an Enter key: the listener gets IME_NULL
		EditText enter = field(column, "enter");
		enter.setInputType(InputType.TYPE_CLASS_TEXT);
		enter.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
		enter.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				Log.i(TAG, "text action enter " + actionId + (event != null ? " key " + event.getAction() : ""));
				return true;
			}
		});

		// a bare EditText is multi-line; two lines cap its height
		final EditText capped = field(column, "capped");
		capped.setMaxLines(2);

		TextView copy = new TextView(this);
		copy.setText("copy me");
		copy.setTextIsSelectable(true);
		column.addView(copy);

		Button fill = new Button(this);
		fill.setText("fill");
		fill.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(TAG, "text fill");
				name.setText("filled");
				name.setSelection(name.length());
			}
		});
		column.addView(fill);

		Button show = new Button(this);
		show.setText("show");
		show.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(TAG, "text show \"" + escaped(name.getText()) + "\" " + name.getSelectionStart() + "," + name.getSelectionEnd());
			}
		});
		column.addView(show);

		// Java's selection may end before it starts; UIKit's range can't
		Button reverse = new Button(this);
		reverse.setText("reverse");
		reverse.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				name.setSelection(5, 2);
				Log.i(TAG, "text reversed " + name.getSelectionStart() + "," + name.getSelectionEnd());
			}
		});
		column.addView(reverse);

		// a red first word: making it selectable swaps its native view, and the rest stays the default color
		final TextView marked = new TextView(this);
		SpannableString error = new SpannableString("Error: disk full");
		error.setSpan(new ForegroundColorSpan(0xffff0000), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		marked.setText(error);
		column.addView(marked);

		Button select = new Button(this);
		select.setText("select");
		select.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				marked.setTextIsSelectable(true);
				Log.i(TAG, "text selectable");
			}
		});
		column.addView(select);

		// on top: the capped field's growth moves what is below it
		Button size = new Button(this);
		size.setText("size");
		size.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(TAG, "text capped " + capped.getHeight());
			}
		});
		column.addView(size, 0);

		Button eye = new Button(this);
		eye.setText("eye");
		eye.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				boolean hidden = reveal.getTransformationMethod() != null;
				reveal.setTransformationMethod(hidden ? null : PasswordTransformationMethod.getInstance());
				Log.i(TAG, "text eye " + (hidden ? "shown" : "hidden"));
			}
		});
		column.addView(eye, 1);

		column.addView(keysButton("calckeys", calc), 2);
		column.addView(keysButton("lockedkeys", locked), 3);

		// the keyboard on name, focus moves away and back at once: the keyboard stays on name
		Button swap = new Button(this);
		swap.setText("swap");
		swap.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				notes.requestFocus();
				name.requestFocus();
			}
		});
		column.addView(swap, 4);

		Button hide = new Button(this);
		hide.setText("hide");
		hide.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
				Log.i(TAG, "text hide " + imm.hideSoftInputFromWindow(name.getWindowToken(), 0));
			}
		});
		column.addView(hide, 5);

		// a message too long for the screen with a view under it: the message scrolls, the view keeps room
		Button longAlert = new Button(this);
		longAlert.setText("long alert");
		longAlert.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				StringBuilder message = new StringBuilder();
				for (int i = 0; i < 120; i++) // over 4 KB: the agent's tap sends the whole label
					message.append("A line of a long message that wraps. ");
				final CheckBox box = new CheckBox(TextActivity.this);
				box.setText("keep");
				box.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
					private int logged = -1;

					public void onGlobalLayout() {
						if (box.getHeight() != logged) {
							logged = box.getHeight();
							// what the box asks for at its width, against what the panel gave it
							box.measure(View.MeasureSpec.makeMeasureSpec(box.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
							Log.i(TAG, "text long content " + logged + " of " + box.getMeasuredHeight());
						}
					}
				});
				new AlertDialog.Builder(TextActivity.this)
				    .setTitle("Long")
				    .setMessage(message)
				    .setView(box)
				    .setPositiveButton("Close", new DialogInterface.OnClickListener() {
					    public void onClick(DialogInterface d, int which) {
						    Log.i(TAG, "text long closed");
					    }
				    })
				    .show();
			}
		});
		column.addView(longAlert, 6);

		column.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
			private int ime;

			public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
				int bottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
				if (bottom != ime)
					Log.i(TAG, "text ime " + bottom);
				ime = bottom;
				return insets;
			}
		});
		getApplication().registerComponentCallbacks(new ComponentCallbacks() {
			public void onConfigurationChanged(Configuration config) {
				Log.i(TAG, "text night " + (config.uiMode & Configuration.UI_MODE_NIGHT_MASK));
			}

			public void onLowMemory() {}
		});

		setContentView(column);
	}

	// the app shows the keyboard on a field itself, as a custom keypad's "abc" key does
	private Button keysButton(String text, final EditText field) {
		Button keys = new Button(this);
		keys.setText(text);
		keys.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
				Log.i(TAG, "text " + field.getHint() + "keys " + imm.showSoftInput(field, 0));
			}
		});
		return keys;
	}

	// the hint is the debug agent's label; name has its own watcher, the others log their text
	private EditText field(LinearLayout column, final String label) {
		EditText field = new EditText(this);
		field.setHint(label);
		field.setContentDescription(label);
		field.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				Log.i(TAG, "text focus " + label + " " + hasFocus);
			}
		});
		if (!label.equals("name")) {
			field.addTextChangedListener(new TextWatcher() {
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				public void afterTextChanged(Editable s) {
					Log.i(TAG, "text " + label + " \"" + escaped(s) + "\"");
				}
			});
		}
		column.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		return field;
	}

	private static String escaped(CharSequence text) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n')
				out.append("\\n");
			else if (c < 0x20 || c > 0x7e)
				out.append(String.format("\\u%04x", (int)c));
			else
				out.append(c);
		}
		return out.toString();
	}

	// drops the digits of an insertion
	private static class NoDigits implements InputFilter {
		public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
			StringBuilder kept = new StringBuilder();
			for (int i = start; i < end; i++) {
				if (!Character.isDigit(source.charAt(i)))
					kept.append(source.charAt(i));
			}
			return kept.length() == end - start ? null : kept;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i(TAG, "text shown");
	}
}

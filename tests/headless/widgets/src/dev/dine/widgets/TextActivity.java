package dev.dine.widgets;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/* Text fields near the top of the screen (no keyboard avoidance yet), for run-text.sh on headless,
 * smoke atl-text on iOS and checks by hand on gtk. Logged text escapes newlines and non-ASCII. */
public class TextActivity extends Activity {
	private static final String TAG = "edits";

	private EditText name;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout column = new LinearLayout(this);
		column.setOrientation(LinearLayout.VERTICAL);

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
		name.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				Log.i(TAG, "text action name " + actionId);
				return false;
			}
		});

		EditText notes = field(column, "notes");
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

		setContentView(column);
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

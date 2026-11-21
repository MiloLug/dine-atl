package android.widget;

import android.content.Context;
import android.util.AttributeSet;

import com.android.internal.R;

public class MultiAutoCompleteTextView extends AutoCompleteTextView {

	public static interface Tokenizer {}

	public MultiAutoCompleteTextView(Context context) {
		this(context, null);
	}

	public MultiAutoCompleteTextView(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.autoCompleteTextViewStyle);
	}

	public MultiAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public MultiAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	public void setTokenizer(Tokenizer tokenizer) {}
}

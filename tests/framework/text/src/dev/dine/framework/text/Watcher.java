package dev.dine.framework.text;

import android.text.Editable;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.List;

// each call as "before|on start count after" or "after text"
class Watcher implements TextWatcher {
	final List<String> calls = new ArrayList<String>();

	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		calls.add("before " + start + " " + count + " " + after);
	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {
		calls.add("on " + start + " " + before + " " + count);
	}

	public void afterTextChanged(Editable s) {
		calls.add("after " + s);
	}
}

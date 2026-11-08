package dev.dine.windows;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

// no uiMode in its manifest: a night change leaves it as it is, only the Application hears it
public class PlainConfigActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView text = new TextView(this);
		text.setText("plain config");
		setContentView(text);
		ConfigActivity.listen(this);
		Log.i("windows", "config: plain created");
	}

	@Override
	public void onConfigurationChanged(Configuration config) {
		Log.i("windows", "config: plain night " + ConfigActivity.night(config));
	}
}

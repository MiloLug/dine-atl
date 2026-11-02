package dev.dine.pkg.app;

import android.os.Bundle;

// finishes at once, so a run of the app ends
public class Main extends android.app.Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		System.out.println("app: started");
		finish();
	}
}

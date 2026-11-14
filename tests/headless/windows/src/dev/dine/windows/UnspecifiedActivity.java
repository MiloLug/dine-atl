package dev.dine.windows;

// no soft input mode in its manifest entry: its ScrollView decides
public class UnspecifiedActivity extends KeyboardActivity {
	public UnspecifiedActivity() {
		super("unspecified", SCROLL);
	}
}

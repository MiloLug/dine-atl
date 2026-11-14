package dev.dine.windows;

// no soft input mode in its manifest entry and no scroll container: it pans
public class UnspecifiedPlainActivity extends KeyboardActivity {
	public UnspecifiedPlainActivity() {
		super("plain", BOTTOM);
	}
}

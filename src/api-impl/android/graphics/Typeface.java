package android.graphics;

import android.content.res.AssetManager;
import java.io.File;

/* A family the gfx slot knows (enum gfx_font_family) and a style; fonts from files are DEFAULT. */
public class Typeface {

	// Style
	public static final int NORMAL = 0;
	public static final int BOLD = 1;
	public static final int ITALIC = 2;
	public static final int BOLD_ITALIC = 3;

	static final int FAMILY_SANS = 0;
	static final int FAMILY_SERIF = 1;
	static final int FAMILY_MONOSPACE = 2;

	// one instance per family and style, as AOSP caches them: apps compare typefaces with ==
	private static final Typeface[][] instances = new Typeface[3][4];

	public static final Typeface DEFAULT = of(FAMILY_SANS, NORMAL);
	public static final Typeface DEFAULT_BOLD = of(FAMILY_SANS, BOLD);
	public static final Typeface SANS_SERIF = of(FAMILY_SANS, NORMAL);
	public static final Typeface SERIF = of(FAMILY_SERIF, NORMAL);
	public static final Typeface MONOSPACE = of(FAMILY_MONOSPACE, NORMAL);

	public long native_instance = 0; // directly accessed by androidx

	final int family;
	private final int style;

	private Typeface(int family, int style) {
		this.family = family;
		this.style = style;
	}

	private static synchronized Typeface of(int family, int style) {
		style &= BOLD_ITALIC;
		if (instances[family][style] == null)
			instances[family][style] = new Typeface(family, style);
		return instances[family][style];
	}

	// Android's family names: "serif-monospace" is a monospace one
	private static int familyOf(String name) {
		if (name == null)
			return FAMILY_SANS;
		name = name.toLowerCase();
		if (name.contains("monospace"))
			return FAMILY_MONOSPACE;
		return name.startsWith("serif") ? FAMILY_SERIF : FAMILY_SANS;
	}

	public static Typeface createFromAsset(AssetManager mgr, String path) {
		return DEFAULT;
	}

	public static Typeface create(String family_name, int style) {
		return of(familyOf(family_name), style);
	}

	public static Typeface create(Typeface typeface, int style) {
		return of(typeface != null ? typeface.family : FAMILY_SANS, style);
	}

	public static Typeface createFromFile(String path) {
		return DEFAULT;
	}

	public static Typeface createFromFile(File file) {
		return DEFAULT;
	}

	public int getStyle() {
		return style;
	}

	public boolean isBold() {
		return (style & BOLD) != 0;
	}

	public boolean isItalic() {
		return (style & ITALIC) != 0;
	}

	public int getWeight() {
		return isBold() ? 700 : 400;
	}


	public static Typeface defaultFromStyle(int style) {
		return create((String)null, style);
	}

	public static Typeface createFromFamiliesWithDefault(FontFamily[] families) {
		return DEFAULT;
	}

	public static Typeface createFromFamiliesWithDefault(FontFamily[] families, int dummy1, int dummy2) {
		return createFromFamiliesWithDefault(families);
	}

	public static class Builder {
		public Builder(AssetManager mgr, String path) {}

		public Builder setFontVariationSettings(String settings) {
			return this;
		}

		public Typeface build() {
			return DEFAULT;
		}
	}
}

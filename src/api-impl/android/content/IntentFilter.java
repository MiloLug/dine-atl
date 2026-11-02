package android.content;

import android.os.Parcel;
import android.os.Parcelable;
import android.net.Uri;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class IntentFilter implements Parcelable {
	public static final int MATCH_CATEGORY_MASK = 0xfff0000;
	public static final int MATCH_ADJUSTMENT_MASK = 0x000ffff;
	public static final int MATCH_ADJUSTMENT_NORMAL = 0x8000;
	public static final int MATCH_CATEGORY_EMPTY = 0x0100000;
	public static final int MATCH_CATEGORY_SCHEME = 0x0200000;
	public static final int NO_MATCH_TYPE = -1;
	public static final int NO_MATCH_DATA = -2;
	public static final int NO_MATCH_ACTION = -3;
	public static final int NO_MATCH_CATEGORY = -4;

	private List<String> actions = new ArrayList<>();
	private Set<String> categories = new HashSet<>();
	private List<String> dataSchemes = new ArrayList<>();

	public IntentFilter() {}
	public IntentFilter(String action) {
		addAction(action);
	}

	public void addAction(String action) {
		actions.add(action);
	}
	public int countActions() {
		return actions.size();
	}

	public final boolean matchAction(String action) {
		return actions.contains(action);
	}

	public void addCategory(String category) {
		categories.add(category);
	}
	public int countCategories() {
		return categories.size();
	}

	public final boolean hasCategory(String category) {
		return categories.contains(category);
	}

	public String getAction(int index) {
		return actions.get(index);
	}

	public void setPriority(int priority) {}

	public void addDataScheme(String dataScheme) {
		dataSchemes.add(dataScheme);
	}

	public void addDataAuthority(String host, String port) {}

	public void addDataPath(String path, int type) {}

	public final void addDataSchemeSpecificPart(String ssp, int type) {
		/* FIXME */
	}

	public boolean hasDataScheme(String dataScheme) {
		return dataSchemes.contains(dataScheme);
	}

	public int countDataSchemes() {
		return dataSchemes.size();
	}

	public String getDataScheme(int index) {
		return dataSchemes.get(index);
	}

	public final Iterator<String> actionsIterator() {
		return actions.iterator();
	}

	public boolean hasAction(String action) {
		return actions.contains(action);
	}

	/** Android's rules for what this filter keeps: action, categories, data scheme; types aren't parsed, so they aren't matched. */
	public int match(String action, String type, String scheme, Uri data, Set<String> categories, String logTag) {
		if (action != null && !matchAction(action))
			return NO_MATCH_ACTION;
		int match;
		if (dataSchemes.isEmpty())
			match = scheme == null ? MATCH_CATEGORY_EMPTY | MATCH_ADJUSTMENT_NORMAL : NO_MATCH_DATA;
		else
			match = scheme != null && hasDataScheme(scheme) ? MATCH_CATEGORY_SCHEME | MATCH_ADJUSTMENT_NORMAL : NO_MATCH_DATA;
		if (match < 0)
			return match;
		if (categories != null)
			for (String category : categories)
				if (!hasCategory(category))
					return NO_MATCH_CATEGORY;
		return match;
	}

	public final int match(ContentResolver resolver, Intent intent, boolean resolve, String logTag) {
		return match(intent.getAction(), intent.getType(), intent.getScheme(), intent.getData(), intent.getCategories(), logTag);
	}

	public final Iterator<String> schemesIterator() {
		return dataSchemes.iterator();
	}

	public final Iterator authoritiesIterator() {
		return null;
	}

	public void writeToParcel(Parcel dest, int flags) {
		dest.writeStringList(actions);
		dest.writeStringList(new ArrayList<>(categories));
		dest.writeStringList(dataSchemes);
	}

	/** @hide */
	public IntentFilter(Parcel source) {
		readFromParcel(source);
	}

	/** @hide */
	public void readFromParcel(Parcel source) {
		source.readStringList(actions);
		List<String> list = new ArrayList<>();
		source.readStringList(list);
		categories.clear();
		categories.addAll(list);
		source.readStringList(dataSchemes);
	}

	public static final Parcelable.Creator<IntentFilter> CREATOR = new Parcelable.Creator<IntentFilter>() {
		public IntentFilter createFromParcel(Parcel source) {
			return new IntentFilter(source);
		}

		public IntentFilter[] newArray(int size) {
			return new IntentFilter[size];
		}
	};
}

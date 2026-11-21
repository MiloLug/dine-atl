package dev.dine.framework.basics;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

/** One provider under two authorities (ProviderTest): a query answers the authority it came by. */
public class TwoNamesProvider extends ContentProvider {
	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
		MatrixCursor cursor = new MatrixCursor(new String[] {"authority"});
		cursor.addRow(new Object[] {uri.getAuthority()});
		return cursor;
	}

	@Override
	public String getType(Uri uri) {
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		return null;
	}

	@Override
	public int delete(Uri uri, String selection, String[] args) {
		return 0;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] args) {
		return 0;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) {
		return null;
	}
}

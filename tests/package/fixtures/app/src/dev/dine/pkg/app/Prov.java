package dev.dine.pkg.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

public class Prov extends ContentProvider {
	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
		return null;
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

	// abstract in ATL's ContentProvider, unlike Android's
	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) {
		return null;
	}
}

package dev.dine.pkg.driver;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;

/** content://dev.dine.pkg.driver.apks/<fixture>: the fixture APK, for an install from a content URI. */
public class Apks extends ContentProvider {
	static final String AUTHORITY = "dev.dine.pkg.driver.apks";

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		return ParcelFileDescriptor.open(Main.fixture(uri.getLastPathSegment()), ParcelFileDescriptor.MODE_READ_ONLY);
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
		return null;
	}

	@Override
	public String getType(Uri uri) {
		return "application/vnd.android.package-archive";
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
}

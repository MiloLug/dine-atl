package android.content.pm;

import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A parsed {@link PackageParser.Package} as bytes, for the package registry. Shared references
 * come back shared: every component's info.applicationInfo is the package's, and a Bundle the
 * parser shared between a component and its info, or the package and its ApplicationInfo, stays one.
 *
 * @hide
 */
public final class PackageCodec {
	/** Rows written with another value are parsed again. Bump it with any change to the format. */
	public static final int PARSE_VERSION = 1;
	/**
	 * fieldsHash() when PARSE_VERSION was last bumped. When it differs, a field of CLASSES changed:
	 * write and read it here (or add it to SKIPPED), bump PARSE_VERSION, then update this value.
	 */
	public static final String FIELDS_HASH = "33d1a91d15f46a21d6c25856ae9b77fe475e3607c7955a7896faa0b8a3829f47";

	/** Fields not written: caches, and install-time state that only the parser itself reads. */
	public static final List<String> SKIPPED = Arrays.asList(
	    "PackageParser$Package.mExtras", "PackageParser$Package.mOperationPending",
	    "PackageParser$Package.manifestDigest", "PackageParser$Package.mSigningKeys",
	    "PackageParser$Package.mKeySetMapping", "PackageParser$Component.componentName",
	    "PackageParser$Component.componentShortName", "PackageParser$Permission.group");

	public static final Class<?>[] CLASSES = {
		PackageParser.Package.class, PackageParser.Component.class, PackageParser.Activity.class,
		PackageParser.Service.class, PackageParser.Provider.class, PackageParser.Instrumentation.class,
		PackageParser.Permission.class, PackageParser.PermissionGroup.class, PackageParser.IntentInfo.class,
		PackageParser.ActivityIntentInfo.class, PackageParser.ServiceIntentInfo.class,
		PackageParser.ProviderIntentInfo.class, IntentFilter.class, PackageItemInfo.class,
		ApplicationInfo.class, ComponentInfo.class, ActivityInfo.class, ServiceInfo.class,
		ProviderInfo.class, PermissionInfo.class, PermissionGroupInfo.class, InstrumentationInfo.class,
		FeatureInfo.class, ConfigurationInfo.class, Signature.class,
	};

	private static final int ELIDE = Parcelable.PARCELABLE_ELIDE_DUPLICATES;

	private PackageCodec() {}

	public static byte[] marshall(PackageParser.Package pkg) {
		Parcel p = Parcel.obtain();
		p.writeInt(PARSE_VERSION);
		p.writeString(pkg.packageName);
		pkg.applicationInfo.writeToParcel(p, 0);
		p.writeBundle(pkg.mAppMetaData);
		p.writeInt(pkg.mAppMetaData != null && pkg.mAppMetaData == pkg.applicationInfo.metaData ? 1 : 0);

		p.writeInt(pkg.permissions.size());
		for (PackageParser.Permission perm : pkg.permissions) {
			perm.info.writeToParcel(p, 0);
			p.writeInt(perm.tree ? 1 : 0);
			p.writeBundle(perm.metaData);
		}
		p.writeInt(pkg.permissionGroups.size());
		for (PackageParser.PermissionGroup group : pkg.permissionGroups) {
			group.info.writeToParcel(p, 0);
			p.writeBundle(group.metaData);
		}
		writeActivities(p, pkg.activities);
		writeActivities(p, pkg.receivers);
		p.writeInt(pkg.providers.size());
		for (PackageParser.Provider provider : pkg.providers) {
			writeComponent(p, provider, provider.info);
			p.writeInt(provider.syncable ? 1 : 0);
		}
		p.writeInt(pkg.services.size());
		for (PackageParser.Service service : pkg.services)
			writeComponent(p, service, service.info);
		p.writeInt(pkg.instrumentation.size());
		for (PackageParser.Instrumentation instr : pkg.instrumentation) {
			p.writeString(instr.className);
			instr.info.writeToParcel(p, 0);
			p.writeBundle(instr.metaData);
		}

		p.writeStringList(pkg.requestedPermissions);
		p.writeInt(pkg.requestedPermissionsRequired.size());
		for (Boolean required : pkg.requestedPermissionsRequired)
			p.writeInt(required ? 1 : 0);
		p.writeStringList(pkg.protectedBroadcasts);
		p.writeStringList(pkg.libraryNames);
		p.writeStringList(pkg.usesLibraries);
		p.writeStringList(pkg.usesOptionalLibraries);
		p.writeStringArray(pkg.usesLibraryFiles);
		p.writeInt(pkg.preferredActivityFilters == null ? -1 : pkg.preferredActivityFilters.size());
		if (pkg.preferredActivityFilters != null)
			for (PackageParser.ActivityIntentInfo filter : pkg.preferredActivityFilters)
				writeIntentInfo(p, filter);
		p.writeStringList(pkg.mOriginalPackages);
		p.writeString(pkg.mRealPackage);
		p.writeStringList(pkg.mAdoptPermissions);
		p.writeString(pkg.mPath);
		p.writeInt(pkg.mVersionCode);
		p.writeString(pkg.mVersionName);
		p.writeString(pkg.mSharedUserId);
		p.writeInt(pkg.mSharedUserLabel);
		p.writeInt(pkg.mSignatures == null ? -1 : pkg.mSignatures.length);
		if (pkg.mSignatures != null)
			for (Signature sig : pkg.mSignatures)
				sig.writeToParcel(p, 0);
		p.writeInt(pkg.mPreferredOrder);
		p.writeString(pkg.mScanPath);
		p.writeInt(pkg.mDidDexOpt ? 1 : 0);
		p.writeInt(pkg.configPreferences.size());
		for (ConfigurationInfo config : pkg.configPreferences)
			config.writeToParcel(p, 0);
		p.writeInt(pkg.reqFeatures == null ? -1 : pkg.reqFeatures.size());
		if (pkg.reqFeatures != null)
			for (FeatureInfo feature : pkg.reqFeatures)
				feature.writeToParcel(p, 0);
		p.writeInt(pkg.installLocation);
		p.writeInt(pkg.mRequiredForAllUsers ? 1 : 0);
		p.writeString(pkg.mRestrictedAccountType);
		p.writeString(pkg.mRequiredAccountType);
		return p.marshall();
	}

	/** Throws IllegalArgumentException for bytes of another PARSE_VERSION. */
	public static PackageParser.Package unmarshall(byte[] data) {
		Parcel p = Parcel.obtain();
		p.unmarshall(data, 0, data.length);
		int version = p.readInt();
		if (version != PARSE_VERSION)
			throw new IllegalArgumentException("parse version " + version + ", expected " + PARSE_VERSION);
		PackageParser.Package pkg = new PackageParser.Package(p.readString());
		pkg.applicationInfo.readFromParcel(p);
		pkg.mAppMetaData = readBundle(p);
		if (p.readInt() != 0)
			pkg.applicationInfo.metaData = pkg.mAppMetaData;

		for (int i = p.readInt(); i > 0; i--) {
			PackageParser.Permission perm = new PackageParser.Permission(pkg, new PermissionInfo(p));
			perm.tree = p.readInt() != 0;
			perm.metaData = readBundle(p);
			pkg.permissions.add(perm);
		}
		for (int i = p.readInt(); i > 0; i--) {
			PackageParser.PermissionGroup group = new PackageParser.PermissionGroup(pkg, new PermissionGroupInfo(p));
			group.metaData = readBundle(p);
			pkg.permissionGroups.add(group);
		}
		readActivities(p, pkg, pkg.activities);
		readActivities(p, pkg, pkg.receivers);
		for (int i = p.readInt(); i > 0; i--) {
			String className = p.readString();
			PackageParser.Provider provider = new PackageParser.Provider(pkg, className, new ProviderInfo(p));
			readComponentRest(p, provider, provider.info);
			for (int n = p.readInt(); n > 0; n--)
				provider.intents.add(readIntentInfo(p, new PackageParser.ProviderIntentInfo(provider)));
			provider.syncable = p.readInt() != 0;
			pkg.providers.add(provider);
		}
		for (int i = p.readInt(); i > 0; i--) {
			String className = p.readString();
			PackageParser.Service service = new PackageParser.Service(pkg, className, new ServiceInfo(p));
			readComponentRest(p, service, service.info);
			for (int n = p.readInt(); n > 0; n--)
				service.intents.add(readIntentInfo(p, new PackageParser.ServiceIntentInfo(service)));
			pkg.services.add(service);
		}
		for (int i = p.readInt(); i > 0; i--) {
			String className = p.readString();
			PackageParser.Instrumentation instr = new PackageParser.Instrumentation(pkg, className, new InstrumentationInfo(p));
			instr.metaData = readBundle(p);
			pkg.instrumentation.add(instr);
		}

		p.readStringList(pkg.requestedPermissions);
		for (int i = p.readInt(); i > 0; i--)
			pkg.requestedPermissionsRequired.add(p.readInt() != 0);
		pkg.protectedBroadcasts = p.createStringArrayList();
		pkg.libraryNames = p.createStringArrayList();
		pkg.usesLibraries = p.createStringArrayList();
		pkg.usesOptionalLibraries = p.createStringArrayList();
		pkg.usesLibraryFiles = p.createStringArray();
		int filters = p.readInt();
		if (filters >= 0) {
			pkg.preferredActivityFilters = new ArrayList<>(filters);
			for (int i = 0; i < filters; i++)
				pkg.preferredActivityFilters.add(readIntentInfo(p, new PackageParser.ActivityIntentInfo(null)));
		}
		pkg.mOriginalPackages = p.createStringArrayList();
		pkg.mRealPackage = p.readString();
		pkg.mAdoptPermissions = p.createStringArrayList();
		pkg.mPath = p.readString();
		pkg.mVersionCode = p.readInt();
		pkg.mVersionName = p.readString();
		pkg.mSharedUserId = p.readString();
		pkg.mSharedUserLabel = p.readInt();
		int sigs = p.readInt();
		if (sigs >= 0) {
			pkg.mSignatures = new Signature[sigs];
			for (int i = 0; i < sigs; i++)
				pkg.mSignatures[i] = Signature.CREATOR.createFromParcel(p);
		}
		pkg.mPreferredOrder = p.readInt();
		pkg.mScanPath = p.readString();
		pkg.mDidDexOpt = p.readInt() != 0;
		for (int i = p.readInt(); i > 0; i--)
			pkg.configPreferences.add(ConfigurationInfo.CREATOR.createFromParcel(p));
		int features = p.readInt();
		if (features >= 0) {
			pkg.reqFeatures = new ArrayList<>(features);
			for (int i = 0; i < features; i++)
				pkg.reqFeatures.add(FeatureInfo.CREATOR.createFromParcel(p));
		}
		pkg.installLocation = p.readInt();
		pkg.mRequiredForAllUsers = p.readInt() != 0;
		pkg.mRestrictedAccountType = p.readString();
		pkg.mRequiredAccountType = p.readString();
		if (p.dataAvail() != 0)
			throw new IllegalArgumentException(p.dataAvail() + " bytes left after the package");
		return pkg;
	}

	/** SHA-256 over every instance field of CLASSES: class, name and type. */
	public static String fieldsHash() {
		List<String> fields = new ArrayList<>();
		for (Class<?> cls : CLASSES)
			for (Field f : cls.getDeclaredFields())
				if (!Modifier.isStatic(f.getModifiers()))
					fields.add(cls.getName() + "." + f.getName() + ":" + f.getType().getName());
		java.util.Collections.sort(fields);
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			for (String field : fields)
				md.update((field + "\n").getBytes());
			StringBuilder hex = new StringBuilder();
			for (byte b : md.digest())
				hex.append(String.format("%02x", b));
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	private static void writeActivities(Parcel p, ArrayList<PackageParser.Activity> activities) {
		p.writeInt(activities.size());
		for (PackageParser.Activity activity : activities)
			writeComponent(p, activity, activity.info);
	}

	private static void readActivities(Parcel p, PackageParser.Package pkg, ArrayList<PackageParser.Activity> out) {
		for (int i = p.readInt(); i > 0; i--) {
			String className = p.readString();
			PackageParser.Activity activity = new PackageParser.Activity(pkg, className, new ActivityInfo(p));
			readComponentRest(p, activity, activity.info);
			for (int n = p.readInt(); n > 0; n--)
				activity.intents.add(readIntentInfo(p, new PackageParser.ActivityIntentInfo(activity)));
			out.add(activity);
		}
	}

	private static void writeComponent(Parcel p, PackageParser.Component<? extends PackageParser.IntentInfo> c, ComponentInfo info) {
		p.writeString(c.className);
		info.writeToParcel(p, ELIDE);
		p.writeBundle(c.metaData);
		p.writeInt(c.metaData != null && c.metaData == info.metaData ? 1 : 0);
		p.writeInt(c.intents.size());
		for (PackageParser.IntentInfo intent : c.intents)
			writeIntentInfo(p, intent);
	}

	private static void readComponentRest(Parcel p, PackageParser.Component<?> c, ComponentInfo info) {
		c.metaData = readBundle(p);
		if (p.readInt() != 0)
			info.metaData = c.metaData;
	}

	private static void writeIntentInfo(Parcel p, PackageParser.IntentInfo intent) {
		intent.writeToParcel(p, 0);
		p.writeInt(intent.hasDefault ? 1 : 0);
		p.writeInt(intent.labelRes);
		p.writeString(intent.nonLocalizedLabel == null ? null : intent.nonLocalizedLabel.toString());
		p.writeInt(intent.icon);
		p.writeInt(intent.logo);
		p.writeInt(intent.preferred);
		p.writeInt(intent.priority);
	}

	private static <T extends PackageParser.IntentInfo> T readIntentInfo(Parcel p, T intent) {
		intent.readFromParcel(p);
		intent.hasDefault = p.readInt() != 0;
		intent.labelRes = p.readInt();
		intent.nonLocalizedLabel = p.readString();
		intent.icon = p.readInt();
		intent.logo = p.readInt();
		intent.preferred = p.readInt();
		intent.priority = p.readInt();
		return intent;
	}

	private static Bundle readBundle(Parcel p) {
		try {
			return p.readBundle(PackageCodec.class.getClassLoader());
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}

package org.droidmod.updater;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public class DownloadHelper<T> {
	private static XMLElementDecorator xed = null;
	public static void reset() {
		xed = null;
	}
	private static void init(Context ctx) throws Exception {
		if(xed == null) {
			String url = ctx.getString(R.string.url_update);
			try {
				xed = XMLElementDecorator.parse(url).getChild("smupdater");
			} catch(Exception e) {
				throw new Exception("Unable to contact downloads server " + url, e);
			}
		}
	}
	private static XMLElementDecorator getFileXml(String type) throws Exception {
		for(XMLElementDecorator file : xed.getChild("files").getChildren("file")) {
			if(!type.equals(file.getAttribute("type")))
				continue;
			return file;
		}
		return null;
	}

	enum Downloadable {
		ROOT("root"),
		RECOVERY_TOOLS("recovery_tools"),
		NANDDUMP("nanddump"),
		RECOVERY_IMAGE("recovery");

		private final String type;
		private String url = null;
		private String md5 = null;

		private XMLElementDecorator xed = null;

		Downloadable(String type) {
			this.type = type;
		}

		public String getUrl() throws Exception {
			if(url != null)
				return url;
			if(xed == null)
				xed = getFileXml(type);
			url = xed.getChild("url").getString();
			return url;
		}

		public String getMd5() throws Exception {
			if(md5 != null)
				return md5;
			if(xed == null)
				xed = getFileXml(type);
			md5 = xed.getChild("md5").getString();
			return md5;
		}

	}

	enum RomType {
		ROM_TGZ,
		UPDATE_ZIP;
	}

	static class RomDescriptor implements Parcelable {
		public final RomType type;
		public final String name;
		public final int revision;
		public final String dispid;
		public final String url;
		public final String md5;
		public final int icon;
		private RomDescriptor(RomType type, String name, int revision, String dispid, String url, String md5, int icon) {
			this.type = type;
			this.name = name;
			this.revision = revision;
			this.dispid = dispid;
			this.url = url;
			this.md5 = md5;
			this.icon = icon;
		}

		public RomDescriptor(Parcel in) {
			this.type = RomType.values()[in.readInt()];
			this.name = in.readString();
			this.revision = in.readInt();
			this.dispid = in.readString();
			this.url = in.readString();
			this.md5 = in.readString();
			this.icon = in.readInt();
		}

		public int describeContents() {
			return 0;
		}

		public void writeToParcel(Parcel dest, int flags) {
			dest.writeInt(type.ordinal());
			dest.writeString(name);
			dest.writeInt(revision);
			dest.writeString(dispid);
			dest.writeString(url);
			dest.writeString(md5);
			dest.writeInt(icon);
		}

		public static final Parcelable.Creator<RomDescriptor> CREATOR = new Parcelable.Creator<RomDescriptor>() {
			public RomDescriptor createFromParcel(Parcel in) {
				return new RomDescriptor(in);
			}

			public RomDescriptor[] newArray(int size) {
				return new RomDescriptor[size];
			}
		};
	}

	private final Context ctx;
	private final Caller<T> caller;
	private final DownloadUtil<T> du;
	private int download_attempts = 0;

	public DownloadHelper(Context u, Caller<T> caller) throws Exception {
		init(u);
		this.ctx = u;
		this.caller = caller;
		du = new DownloadUtil<T>(u, caller);
	}

	public void resetDownloadAttempts() {
		download_attempts = 0;
	}

	public File downloadFile(Downloadable which, File where, T callback, T callback_cancel) throws Exception {
		return downloadFile(which.getUrl(), which.getMd5(), where, callback, callback_cancel);
	}

	public File downloadFile(String url, String expect_md5, File where, T callback, T callback_cancel) throws Exception {
		if(where == null) {
			String w = url;
			w = w.substring(w.lastIndexOf('/') + 1);
			where = new File("/sdcard/" + w);
		}

		while(where.exists()) {
			String actual_md5;
			try {
				actual_md5 = DownloadUtil.md5(where);
			} catch (Exception e) {
				// Re-download
				break;
			}
			String message = "Calculating md5 of " + where.getName() + "...";
			if(expect_md5.equals(actual_md5)) {
				caller.addText(message + "pass");
				// Got the file
				return where;
			} else {
				caller.addText(message + "fail: " + actual_md5);
				// Fall-through to re-download
				break;
			}
		}

		download_attempts++;
		if(download_attempts >= 3)
			throw new Exception("Failed to download " + url);
		du.downloadFile(where, new URL(url), callback, callback_cancel);
		return null;
	}

	public List<RomDescriptor> getRoms(int currentRevision) {
		List<RomDescriptor> roms = new ArrayList<RomDescriptor>();

		XMLElementDecorator e_zips = xed.getChild("zips");
		if(e_zips != null)
			for(XMLElementDecorator rom : e_zips.getChildren("zip")) {
				String name = rom.getAttribute("name");
				String dispid = rom.getAttribute("dispid");
				String url = rom.getChild("url").getString();
				String md5 = rom.getChild("md5").getString();
				roms.add(new RomDescriptor(RomType.UPDATE_ZIP, name, 0, dispid, url, md5, R.drawable.stock));
			}

		XMLElementDecorator e_roms = xed.getChild("roms");
		if(e_roms != null)
			for(XMLElementDecorator rom : e_roms.getChildren("rom")) {
				String name = rom.getAttribute("name");
				String dispid = rom.getAttribute("dispid");
				int rev = -1;
				try {
					rev = Integer.parseInt(rom.getAttribute("revision"));
				} catch(NumberFormatException e) {}
				String url = rom.getChild("url").getString();
				String md5 = rom.getChild("md5").getString();
				int icon = R.drawable.sholes;
				if((rev != -1) && (currentRevision == rev))
					icon = R.drawable.current;
				roms.add(new RomDescriptor(RomType.ROM_TGZ, name, rev, dispid, url, md5, icon));
			}

		return roms;
	}

	public RomDescriptor latestRom(int currentRevision) {
		RomDescriptor latest = null;
		for(RomDescriptor rd : getRoms(currentRevision)) {
			if((latest == null) || (rd.revision > latest.revision))
				latest = rd;
		}
		return latest;
	}

	public boolean checkVersion(PackageInfo pi) {
		XMLElementDecorator vc = xed.getChild("version_check");
		int code = vc.getChild("code").getInt().intValue();
		if(code <= pi.versionCode)
			return false;

		// Update available!
		final String name = vc.getChild("name").getString();
		final String uri = vc.getChild("uri").getString();
		new AlertDialog.Builder(ctx)
		.setTitle(R.string.update_available)
		.setMessage("Version " + name + " of " + ctx.getString(R.string.app_label) + " is available")
		.setPositiveButton(
				R.string.update_available_pos,
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
						System.exit(1);
					}
				}
		)
		.setNegativeButton(
				R.string.update_available_neg,
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						System.exit(1);
					}
				}
		)
		.show();

		return true;
	}

}

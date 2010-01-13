package info.sholes.camel.updater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.widget.TextView;

public class Updater extends Activity {
	private enum Callback {
		ROOT,
		FLASH,
		FLASH2
	}

	int download_attempts = 0;
	File flash_image = null;
	File recovery_image = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		String tmp = getDir("tmp", MODE_WORLD_READABLE).getAbsolutePath();
		flash_image = new File(tmp + "/flash_image");
		recovery_image = new File(tmp + "/recovery.img");

		boolean rooted = new File("/system/bin/su").exists();
		if(rooted) {
			try {
				String out = SuperUser.oneShot("ls -l /sdcard");
				if(out.length() == 0) {
					addText("ls -l /sdcard didn't do anything..what the heck!");
					return;
				}
			} catch (Exception e) {
				rooted = false;
			}
		}

		if(!rooted) {
			new AlertDialog.Builder(this)
			.setMessage(getString(R.string.not_rooted))
			.setPositiveButton(
					getString(R.string.not_rooted_pos),
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							doRoot();
						}
					}
			)
			.setNegativeButton(
					getString(R.string.not_rooted_neg),
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							System.exit(1);
						}
					}
			)
			.show();
		} else {
			download_attempts = 0;
			doFlash();
		}
	}

	private void doRoot() {
		File update = new File("/sdcard/update.zip");
		if(update.exists()) {
			String md5;
			try {
				md5 = md5(update);
			} catch (Exception e) {
				downloadSigned(update);
				return;
			}
			if(getString(R.string.md5_signed).equals(md5)) {
				// Got signed-voles...
				addText(getString(R.string.add_exploit));
				addExploit(update);
			} else if(getString(R.string.md5_exploit).equals(md5)) {
				addText(getString(R.string.exploit_ready));
				// Display a pop-up explaining how to root
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.reboot_recovery))
				.setPositiveButton("OK", new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						System.exit(1);
					}
				})
				.show();
			} else {
				addText("Unknown MD5: " + md5);
				downloadSigned(update);
			}
		} else {
			addText("update.zip not found");
			downloadSigned(update);
		}
	}

	private void doFlash() {
		if(flash_image.exists()) {
			try {
				String md5 = md5(flash_image);
				addText(flash_image.getAbsolutePath() + " = " + md5);
				if(getString(R.string.md5_flash_image).equals(md5)) {
					addText(flash_image.getAbsolutePath() + " looks okay");
					download_attempts = 0;
					doFlash2();
					return;
				}
			} catch (Exception e) {
				showException(e);
				return;
			}
		} else {
			addText(flash_image.getAbsolutePath() + " doesn't exist");
		}

		try {
			download_attempts++;
			if(download_attempts >= 3) {
				addText("It's hopeless; giving up");
				return;
			}
			downloadFile(flash_image, new URL(getString(R.string.url_flash_image)), Callback.FLASH);
		} catch (Exception e) {
			showException(e);
		}
	}

	private void doFlash2() {
		// We have flash_image, download the recovery image
		if(recovery_image.exists()) {
			try {
				String md5 = md5(recovery_image);
				addText(recovery_image.getAbsolutePath() + " = " + md5);
				if(getString(R.string.md5_recovery_image).equals(md5)) {
					addText(recovery_image.getAbsolutePath() + " looks okay");
					download_attempts = 0;
					doFlash3();
					return;
				}
			} catch (Exception e) {
				showException(e);
				return;
			}
		} else {
			addText(recovery_image.getAbsolutePath() + " doesn't exist");
		}

		try {
			download_attempts++;
			if(download_attempts >= 3) {
				addText("It's hopeless; giving up");
				return;
			}
			downloadFile(recovery_image, new URL(getString(R.string.url_recovery_image)), Callback.FLASH2);
		} catch (Exception e) {
			showException(e);
		}
	}

	private void doFlash3() {
		// Recovery image downloaded, flash it if needed

		//try {
		//	File goodrecovery = new File("/sdcard/recovery-0.99.2b.img");
		//	SuperUser.oneShot("cat /dev/block/mtdblock3 > /sdcard/recovery.md5chk");
		//	File tmp = new File("/sdcard/recovery.md5chk");
		//
		//	// Show the md5 of the two copies
		//	addText(md5(tmp, (int)goodrecovery.length()));
		//	addText(md5(goodrecovery));
		//
		//	tmp.delete();
		//} catch (Exception e) {
		//	showException(e);
		//	return;
		//}

		//new AlertDialog.Builder(this)
		//.setMessage("From here, we will check if you've got SPRecovery..if not, we'll download it and flash_image, and then set up the ROM")
		//.show();
	}

	private void downloadSigned(File update) {
		addText("Downloading signed-voles-ESD56-from-ESD20.84263456.zip");

		try {
			downloadFile(update, new URL(getString(R.string.url_signed)), Callback.ROOT);
		} catch(Exception e) {
			showException(e);
		}
	}

	private void addExploit(File update) {
		try {
			downloadFile(update, "droid-superuser.zip", true, Callback.ROOT);
		} catch (Exception e) {
			showException(e);
		}
	}

	private void downloadFile(File fout, String asset_filename, boolean append, Callback callback) throws Exception {
		AssetFileDescriptor fd = getAssets().openFd(asset_filename);
		downloadFile(fout, asset_filename, fd.createInputStream(), (int)fd.getLength(), append, callback);
	}

	private void downloadFile(File fout, URL url, Callback callback) throws Exception {
		URLConnection uc = url.openConnection();
		int length = 0;
		try {
			length = Integer.parseInt(uc.getHeaderField("content-length"));
		} catch(Exception e) {}
		addText("Downloading " + url.toString());
		downloadFile(fout, url.toString(), uc.getInputStream(), length, false, callback);
	}

	private void downloadFile(File fout, String from, InputStream is, int filelen, boolean append, final Callback callback) throws Exception {
		if(!append && fout.exists())
			fout.delete();
		final OutputStream os = new FileOutputStream(fout, append);

		final ProgressDialog pd = new ProgressDialog(this);
		pd.setTitle(append ? "Appending..." : "Downloading...");
		pd.setMessage("From " + from + "\nTo " + fout.toString());
		if(filelen == 0) {
			pd.setIndeterminate(true);
		} else {
			pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			pd.setMax(filelen);
		}
		pd.setCancelable(false);
		pd.show();

		new DownloadTask() {
			@Override
			protected void onProgressUpdate(Integer... values) {
				for(Integer i : values)
					pd.incrementProgressBy(i.intValue());
			}

			@Override
			protected void onPostExecute(Exception result) {
				pd.hide();
				if(result == null) {
					switch(callback) {
					case ROOT:
						doRoot();
						return;
					case FLASH:
						doFlash();
						return;
					case FLASH2:
						doFlash2();
						return;
					}
				} else {
					showException(result);
				}
			}
		}.execute(is, os);
	}

	private String md5(File f) throws Exception {
		FileInputStream fis = new FileInputStream(f);
		int bytes_read;
		byte[] buffer = new byte[1024];
		MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
		while((bytes_read = fis.read(buffer)) > 0)
			digest.update(buffer, 0, bytes_read);
		byte[] hash = digest.digest();
		String md5 = "";
		for(byte h : hash)
			md5 += toHex(h);
		return md5;
	}

	private String toHex(byte h) {
		return hexChar((h >> 4) & 0x0F) + hexChar(h & 0x0F);
	}

	private String hexChar(int i) {
		if(i < 10)
			return Integer.toString(i);
		switch(i) {
		case 10: return "a";
		case 11: return "b";
		case 12: return "c";
		case 13: return "d";
		case 14: return "e";
		case 15: return "f";
		}
		return "?";
	}

	private void showException(Exception ex) {
		StringWriter sw = new StringWriter();
		ex.printStackTrace(new PrintWriter(sw));
		addText(sw.toString());
	}

	private void addText(String text) {
		TextView tvText = (TextView) findViewById(R.id.TextView01);
		String ot = "" + tvText.getText();
		if(ot.length() > 0)
			ot += "\n";
		tvText.setText(ot + text);
	}
}
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
	private DownloadUtil du = null;
	private int download_attempts = 0;
	private File flash_image = null;
	private File recovery_image = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		du = new DownloadUtil(this);
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
							download_attempts = 0;
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
			doFlashImageDownload();
		}
	}
	
	public void callback(Callback c) {
		switch(c) {
		case ROOT:
			doRoot();
			return;
		case FLASH_IMAGE_DOWNLOAD:
			doFlashImageDownload();
			return;
		case RECOVERY_IMAGE_DOWNLOAD:
			doRecoveryImageDownload();
			return;
		case FLASH_RECOVERY:
			doFlashRecovery();
			return;
		case ROM_DOWNLOAD:
			doRomDownload();
			return;
		default:
			addText("Unknown callback: " + c.name());
			return;
		}
	}

	private void doRoot() {
		File update = new File("/sdcard/update.zip");
		while(update.exists()) {
			String md5;
			try {
				md5 = du.md5(update);
			} catch (Exception e) {
				// Re-download
				break;
			}
			if(getString(R.string.md5_signed).equals(md5)) {
				// Got signed-voles...
				addText(getString(R.string.add_exploit));
				try {
					du.downloadFile(update, "droid-superuser.zip", true, Callback.ROOT);
				} catch (Exception e) {
					showException(e);
				}
				return;
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
				return;
			} else {
				addText("Unknown MD5: " + md5);
				// Fall-through to re-download
				break;
			}
		}
		
		try {
			download_attempts++;
			if(download_attempts >= 3) {
				addText("It's hopeless; giving up");
				return;
			}
			du.downloadFile(update, new URL(getString(R.string.url_signed)), Callback.ROOT);
		} catch(Exception e) {
			showException(e);
		}
	}

	private void doFlashImageDownload() {
		if(flash_image.exists()) {
			try {
				String md5 = du.md5(flash_image);
				if(getString(R.string.md5_flash_image).equals(md5)) {
					addText(flash_image.getAbsolutePath() + " looks okay");
					download_attempts = 0;
					doRecoveryImageDownload();
					return;
				} else {
					addText(flash_image.getAbsolutePath() + " = " + md5);
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
			du.downloadFile(flash_image, new URL(getString(R.string.url_flash_image)), Callback.FLASH_IMAGE_DOWNLOAD);
		} catch (Exception e) {
			showException(e);
		}
	}

	private void doRecoveryImageDownload() {
		// We have flash_image, download the recovery image
		if(recovery_image.exists()) {
			try {
				String md5 = du.md5(recovery_image);
				if(getString(R.string.md5_recovery_image).equals(md5)) {
					addText(recovery_image.getAbsolutePath() + " looks okay");
					download_attempts = 0;
					doFlashRecovery();
					return;
				} else {
					addText(recovery_image.getAbsolutePath() + " = " + md5);
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
			du.downloadFile(recovery_image, new URL(getString(R.string.url_recovery_image)), Callback.RECOVERY_IMAGE_DOWNLOAD);
		} catch (Exception e) {
			showException(e);
		}
	}

	private void doFlashRecovery() {
		// Recovery image downloaded, flash it if needed
		addText("doFlashRecovery()");

		try {
			// Calculate md5 of the recovery block, mtdblock3
			String current_md5 = SuperUser.oneShotMd5("cat /dev/block/mtdblock3 | head -c " + recovery_image.length());
			
			if(getString(R.string.md5_recovery_image).equals(current_md5)) {
				addText("/dev/block/mtdblock3 looks okay");
				download_attempts = 0;
				doRomDownload();
				return;
			} else {
				new AlertDialog.Builder(this)
				.setMessage("Your recovery image is not flashed. This can be done for you programatically, but it has not yet been implemented.")
				.show();
				return;
			}
		} catch (Exception e) {
			showException(e);
			return;
		}
	}
	
	private void doRomDownload() {
		
	}

	protected void showException(Exception ex) {
		StringWriter sw = new StringWriter();
		ex.printStackTrace(new PrintWriter(sw));
		addText(sw.toString());
	}

	protected void addText(String text) {
		TextView tvText = (TextView) findViewById(R.id.TextView01);
		String ot = "" + tvText.getText();
		if(ot.length() > 0)
			ot += "\n";
		tvText.setText(ot + text);
	}
}
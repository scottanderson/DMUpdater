package info.sholes.camel.updater;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
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
				// Permission denied, most likely
				rooted = false;
			}
		}

		if(!rooted) {
			new AlertDialog.Builder(this)
			.setMessage(getString(R.string.not_rooted))
			.setCancelable(false)
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
		case ROM_INSTALL:
			doRomInstall();
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
				md5 = DownloadUtil.md5(update);
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
				.setCancelable(false)
				.setPositiveButton(
						"OK",
						new OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								System.exit(1);
							}
						}
				)
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
				String md5 = DownloadUtil.md5(flash_image);
				if(getString(R.string.md5_flash_image).equals(md5)) {
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
				String md5 = DownloadUtil.md5(recovery_image);
				if(getString(R.string.md5_recovery_image).equals(md5)) {
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

		try {
			// Calculate md5 of the recovery block, mtdblock3
			int length = (int)recovery_image.length();
			// TODO: validate length <= the block size
			String command = "dd if=/dev/block/mtdblock3 count=1 bs=" + length;
			String current_md5 = SuperUser.oneShotMd5(command, length);
			
			if(getString(R.string.md5_recovery_image).equals(current_md5)) {
				download_attempts = 0;
				doRomDownload();
				return;
			} else {
				addText(command + " = " + current_md5);
				new AlertDialog.Builder(this)
				.setMessage("Your recovery image is not flashed. This can be done for you programatically, but it has not yet been implemented.")
				.setCancelable(false)
				.setPositiveButton(
						"OK",
						new OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								System.exit(1);
							}
						}
				)
				.show();
				return;
			}
		} catch (Exception e) {
			showException(e);
			return;
		}
	}
	
	private void doRomDownload() {
		new AlertDialog.Builder(this)
		.setMessage("We're ready to download the ROM. This has not yet been implemented.")
		.setCancelable(false)
		.setPositiveButton(
				"OK",
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						System.exit(1);
					}
				}
		)
		.show();
	}
	
	private void doRomInstall() {
		new AlertDialog.Builder(this)
		.setMessage("We're ready to flash the ROM. This has not yet been implemented.")
		.setCancelable(false)
		.setPositiveButton(
				"OK",
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						System.exit(1);
					}
				}
		)
		.show();
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
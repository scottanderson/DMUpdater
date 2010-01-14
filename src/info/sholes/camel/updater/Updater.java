package info.sholes.camel.updater;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Properties;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

public class Updater extends Activity {
	private DownloadUtil du = null;
	private int download_attempts = 0;
	private File update_zip = null;
	private File flash_image = null;
	private File recovery_image = null;
	private File rom_tgz = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		try {
			Properties p = new Properties();
			p.load(new FileInputStream("/system/build.prop"));
			addText("Current ROM: " + p.getProperty("ro.product.model"));
			addText("Version: " + p.getProperty("ro.build.display.id"));
		} catch(Exception e) {
			showException(e);
			return;
		}

		du = new DownloadUtil(this);
		update_zip = new File("/sdcard/update.zip");
		String tmp = getDir("tmp", MODE_WORLD_READABLE).getAbsolutePath();
		flash_image = new File(tmp + "/flash_image");
		recovery_image = new File(tmp + "/recovery.img");
		rom_tgz = new File("/sdcard/sholes.rom.tgz");

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
			.setMessage(R.string.not_rooted)
			.setCancelable(false)
			.setPositiveButton(
					R.string.not_rooted_pos,
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							download_attempts = 0;
							doRoot();
						}
					}
			)
			.setNegativeButton(
					R.string.not_rooted_neg,
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
		while(update_zip.exists()) {
			String md5;
			try {
				md5 = DownloadUtil.md5(update_zip);
			} catch (Exception e) {
				// Re-download
				break;
			}
			if(getString(R.string.md5_signed).equals(md5)) {
				// Got signed-voles...
				addText(getString(R.string.add_exploit));
				try {
					du.downloadFile(update_zip, "droid-superuser.zip", true, Callback.ROOT);
				} catch (Exception e) {
					showException(e);
				}
				return;
			} else if(getString(R.string.md5_exploit).equals(md5)) {
				addText(getString(R.string.exploit_ready));
				// Display a pop-up explaining how to root
				new AlertDialog.Builder(this)
				.setMessage(R.string.reboot_recovery)
				.setCancelable(false)
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
			du.downloadFile(update_zip, new URL(getString(R.string.url_signed)), Callback.ROOT);
		} catch(Exception e) {
			showException(e);
		}
	}

	private void doFlashImageDownload() {
		if(flash_image.exists()) {
			try {
				String md5 = DownloadUtil.md5(flash_image);
				if(getString(R.string.md5_flash_image).equals(md5)) {
					try {
						Runtime.getRuntime().exec("chmod 755 " + flash_image.getAbsolutePath());
					} catch(Exception e) {
						showException(e);
						return;
					}

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
				.setMessage(R.string.confirm_flash_recovery)
				.setCancelable(false)
				.setPositiveButton(
						R.string.confirm_flash_recovery_yes,
						new OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								confirmFlashRecovery();
							}
						}
				)
				.setNegativeButton(
						R.string.confirm_flash_recovery_no,
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

	private void confirmFlashRecovery() {
		// Double-check that they are sure
		new AlertDialog.Builder(this)
		.setMessage(R.string.confirm_flash_recovery_2)
		.setCancelable(false)
		.setPositiveButton(
				R.string.confirm_flash_recovery_2_no,
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						System.exit(1);
					}
				}
		)
		.setNegativeButton(
				R.string.confirm_flash_recovery_2_yes,
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						confirmedFlashRecovery();
					}
				}
		)
		.show();
	}

	private void confirmedFlashRecovery() {
		// They really want this to happen

		final String command = flash_image.getAbsolutePath() + " recovery " + recovery_image.getAbsolutePath();

		ProgressDialog pd = new ProgressDialog(this);
		pd.setCancelable(false);
		pd.setMessage(command);
		pd.setIndeterminate(true);
		pd.show();

		try {
			SuperUser.oneShot(command);
		} catch (Exception e) {
			showException(e);
			pd.hide();
			return;
		}

		pd.hide();
		// Go back and check the MD5
		doFlashRecovery();
	}

	private void doRomDownload() {
		if(rom_tgz.exists()) {
			try {
				String md5 = DownloadUtil.md5(rom_tgz);
				if(getString(R.string.md5_rom).equals(md5)) {
					download_attempts = 0;
					doRomInstall();
					return;
				} else {
					addText(rom_tgz.getAbsolutePath() + " = " + md5);
				}
			} catch (Exception e) {
				showException(e);
				return;
			}
		} else {
			addText(rom_tgz.getAbsolutePath() + " doesn't exist");
		}

		try {
			download_attempts++;
			if(download_attempts >= 3) {
				addText("It's hopeless; giving up");
				return;
			}
			du.downloadFile(rom_tgz, new URL(getString(R.string.url_rom)), Callback.ROM_DOWNLOAD);
		} catch (Exception e) {
			showException(e);
		}
	}

	private void doRomInstall() {
		// ROM is downloaded, ready to ask user about options
		new AlertDialog.Builder(this)
		.setMessage(R.string.confirm_flash_rom)
		.setCancelable(false)
		.setPositiveButton(
				R.string.confirm_flash_rom_yes,
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						confirmedRomInstall();
					}
				}
		)
		.setNegativeButton(
				R.string.confirm_flash_rom_no,
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						System.exit(1);
					}
				}
		)
		.show();
	}

	private void confirmedRomInstall() {
		String path = rom_tgz.getAbsolutePath();
		if(!path.startsWith("/sdcard/")) {
			addText(path + " should be on /sdcard...what happened?");
			return;
		}
		path = "SDCARD:" + path.substring(8);

		try {
			SuperUser.oneShot("echo -n \"--install_tgz " + path + "\" > /cache/recovery/command");
			SuperUser.oneShot("/system/bin/reboot recovery");
		} catch(Exception e) {
			showException(e);
		}
	}

	protected void showException(Exception ex) {
		StringWriter sw = new StringWriter();
		ex.printStackTrace(new PrintWriter(sw));
		addText(sw.toString());
	}

	protected void addText(String text) {
		TextView tvText = new TextView(this);
		tvText.setText(text);
		LinearLayout ll = (LinearLayout) findViewById(R.id.LinearLayout01);
		ll.addView(tvText);
	}
}
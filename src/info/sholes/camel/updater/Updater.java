package info.sholes.camel.updater;

import info.sholes.camel.updater.DownloadHelper.Downloadable;
import info.sholes.camel.updater.DownloadHelper.RomDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Properties;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class Updater extends Activity implements Caller<Updater.Callback> {
	enum Callback {
		ROOT,
		FLASH_IMAGE_DOWNLOAD,
		RECOVERY_IMAGE_DOWNLOAD,
		ROM_DOWNLOAD,
		DOWNLOAD_CANCELLED
	}

	private DownloadHelper<Callback> dh = null;
	private int current_revision = -1;
	private File flash_image = null;
	private File recovery_image = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		try {
			Properties p = new Properties();
			p.load(new FileInputStream("/system/build.prop"));
			addText("Current ROM: " + p.getProperty("ro.build.display.id"));
			try {
				current_revision = Integer.parseInt(p.getProperty("ro.info.sholes.revision"));
			} catch(NumberFormatException e) {}

			DownloadHelper.reset();
			dh = new DownloadHelper<Callback>(this, this);

			PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
			addText("Version: " + pi.versionCode + " (" + pi.versionName + ")");

			String model = p.getProperty("ro.product.model");
			if(!"Droid".equals(model) && !"Droid Sholes.info Edition".equals(model)) {
				addText("Manufacturer: " + p.getProperty("ro.product.manufacturer"));
				addText("Device: " + p.getProperty("ro.product.device"));
				addText("Brand: " + p.getProperty("ro.product.brand"));
				addText("Model: " + model);
				addText(getString(R.string.droid_only));
				return;
			}

			if(dh.checkVersion(pi))
				return;
		} catch(Exception e) {
			showException(e);
			return;
		}

		RomDescriptor latest = dh.latestRom(current_revision);
		if(latest.revision > current_revision) {
			addText("Update available");
		}

		Button b = new Button(this);
		b.setText("Latest ROM: " + latest.name);
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				sendRomMenuIntent();
			}
		});
		LinearLayout ll = (LinearLayout) findViewById(R.id.LinearLayout01);
		ll.addView(b);

		String tmp = getFilesDir().getAbsolutePath();
		flash_image = new File(tmp + "/flash_image");
		recovery_image = new File(tmp + "/recovery.img");

		checkRoot();
	}

	private void checkRoot() {
		if(!new File("/system/bin/su").exists()) {
			notRooted();
			return;
		}

		// SU exists, check if it works
		try {
			if(SuperUser.isRemembered(this)) {
				rootVerified();
				return;
			}
		} catch (Exception e) {
			if(e.getMessage().toLowerCase().contains("permission denied")) {
				notRooted();
			} else {
				// Whoa! What happened?
				showException(e);
				notRooted();
			}
			return;
		}

		// Didn't work - tell them to check the remember box
		new AlertDialog.Builder(this)
		.setMessage(R.string.rooted_check_remember)
		.setPositiveButton(
				R.string.check_again,
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						checkRoot();
					}
				}
		)
		.show();
	}

	private void notRooted() {
		new AlertDialog.Builder(this)
		.setMessage(R.string.not_rooted)
		.setCancelable(false)
		.setPositiveButton(
				R.string.not_rooted_pos,
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dh.resetDownloadAttempts();
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
	}

	private void rootVerified() {
		// They have root, check if /sdcard is mounted
		try {
			String mounts = SuperUser.oneShot("/system/bin/toolbox mount");
			if(mounts.contains(" /sdcard ")) {
				rootAndSdcardVerified();
				return;
			} else {
				addText(getString(R.string.sdcard_not_mounted));
			}
		} catch(Exception e) {
			showException(e);
			return;
		}
	}

	private void rootAndSdcardVerified() {
		// /sdcard is mounted, check if they have enough space
		try {
			String result = SuperUser.oneShot("/system/bin/toolbox df /sdcard");
			/* /sdcard: 15654912K total, 13663360K used, 1991552K available (block size 32768) */
			String[] space = result.split(" ");
			String kb = space[5];
			if((space.length != 10) || !"available".equals(space[6]) || !kb.endsWith("K"))
				throw new IllegalStateException(result);
			kb = kb.substring(0, kb.length() - 1);
			int kb_i = Integer.parseInt(kb);
			if(kb_i >= 250 * 1024) {
				enoughSpaceVerified();
				return;
			}

			addText(getString(R.string.need_250mb) + Formatter.formatFileSize(this, kb_i * 1024));
		} catch(Exception e) {
			showException(e);
		}
	}

	private void enoughSpaceVerified() {
		dh.resetDownloadAttempts();
		doFlashImageDownload();
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
		case DOWNLOAD_CANCELLED:
			finish();
			return;
		default:
			addText("Unknown callback: " + c.name());
			return;
		}
	}

	private void doRoot() {
		try {
			File f = dh.downloadFile(Downloadable.ROOT, new File("/sdcard/update.zip"), Callback.ROOT, Callback.DOWNLOAD_CANCELLED);
			if(f == null) {
				// Wait for the callback
				return;
			}
		} catch(Exception e) {
			showException(e);
			return;
		}

		addText(getString(R.string.exploit_ready));
		// Display a pop-up explaining how to root
		new AlertDialog.Builder(this)
		.setMessage(R.string.reboot_recovery)
		.setCancelable(false)
		.show();
	}

	private void doFlashImageDownload() {
		try {
			File f = dh.downloadFile(Downloadable.FLASH_IMAGE, flash_image, Callback.FLASH_IMAGE_DOWNLOAD, Callback.DOWNLOAD_CANCELLED);
			if(f == null) {
				// Wait for the callback
				return;
			}

			Runtime.getRuntime().exec("chmod 755 " + flash_image.getAbsolutePath());
		} catch(Exception e) {
			showException(e);
			return;
		}

		dh.resetDownloadAttempts();
		doRecoveryImageDownload();
	}

	private void doRecoveryImageDownload() {
		try {
			File f = dh.downloadFile(Downloadable.RECOVERY_IMAGE, recovery_image, Callback.RECOVERY_IMAGE_DOWNLOAD, Callback.DOWNLOAD_CANCELLED);
			if(f == null) {
				// Wait for the callback
				return;
			}
		} catch(Exception e) {
			showException(e);
			return;
		}

		doFlashRecovery();
	}

	private void doFlashRecovery() {
		// Recovery image downloaded, flash it if needed

		try {
			// Calculate md5 of the recovery block, mtdblock3
			int length = (int)recovery_image.length();
			// TODO: validate length <= the block size
			String command = "dd if=/dev/block/mtdblock3 count=1 bs=" + length;
			String current_md5 = SuperUser.oneShotMd5(command, length);
			String expected_md5 = Downloadable.RECOVERY_IMAGE.getMd5();

			if(expected_md5.equals(current_md5)) {
				dh.resetDownloadAttempts();
				showRomMenu();
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

	private void showRomMenu() {
		Button b = new Button(this);
		b.setText("ROM menu");
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				sendRomMenuIntent();
			}
		});
		LinearLayout ll = (LinearLayout) findViewById(R.id.LinearLayout01);
		ll.addView(b);
	}

	private void sendRomMenuIntent() {
		final List<RomDescriptor> roms = dh.getRoms(current_revision);

		Intent i = new Intent(this, RomMenu.class);
		i.putExtra("roms", roms.toArray(new RomDescriptor[roms.size()]));
		startActivity(i);
	}

	public void showException(Throwable ex) {
		Log.e("SMUpdater", ex.getMessage(), ex);

		new AlertDialog.Builder(this)
		.setTitle("An error has occurred!")
		.setMessage(ex.getMessage())
		.show();

		StringWriter sw = new StringWriter();
		ex.printStackTrace(new PrintWriter(sw));
		addText(sw.toString());
	}

	public void addText(String text) {
		TextView tvText = new TextView(this);
		tvText.setText(text);
		LinearLayout ll = (LinearLayout) findViewById(R.id.LinearLayout01);
		ll.addView(tvText);
	}
}
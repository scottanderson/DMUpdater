package info.sholes.camel.updater;

import info.sholes.camel.updater.DownloadHelper.Downloadable;
import info.sholes.camel.updater.DownloadHelper.RomDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;
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
	private DownloadHelper dh = null;
	private File update_zip = null;
	private File flash_image = null;
	private File recovery_image = null;
	private RomDescriptor selected_rom = null;
	private File rom_tgz = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			public void uncaughtException(Thread thread, Throwable ex) {
				addText("Whoa! Uncaught exception!");
				showException(ex);
				ex.printStackTrace();
			}
		});
		setContentView(R.layout.main);

		try {
			Properties p = new Properties();
			p.load(new FileInputStream("/system/build.prop"));
			addText("Current ROM: " + p.getProperty("ro.product.model"));
			addText("Version: " + p.getProperty("ro.build.display.id"));

			dh = new DownloadHelper(this);
		} catch(Exception e) {
			showException(e);
			return;
		}

		update_zip = new File("/sdcard/update.zip");
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
		} else {
			dh.resetDownloadAttempts();
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
		case ROM_DOWNLOAD:
			doRomDownload();
			return;
		default:
			addText("Unknown callback: " + c.name());
			return;
		}
	}

	private void doRoot() {
		try {
			File f = dh.downloadFile(Downloadable.ROOT, update_zip, Callback.ROOT);
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
			File f = dh.downloadFile(Downloadable.FLASH_IMAGE, flash_image, Callback.FLASH_IMAGE_DOWNLOAD);
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
			File f = dh.downloadFile(Downloadable.RECOVERY_IMAGE, recovery_image, Callback.RECOVERY_IMAGE_DOWNLOAD);
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
		final List<RomDescriptor> roms = dh.getRoms();

		if(roms.size() == 0) {
			addText(getString(R.string.rom_menu_no_roms));
		} else if(roms.size() > 1) {
			String[] romNames = new String[roms.size()];
			for(int i = 0; i < romNames.length; i++)
				romNames[i] = roms.get(i).name;
			selected_rom = roms.get(roms.size()-1);

			new AlertDialog.Builder(this)
			.setTitle(R.string.rom_menu)
			.setCancelable(false)
			.setSingleChoiceItems(
					romNames,
					roms.indexOf(selected_rom),
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							selected_rom = roms.get(which);
						}
					}
			)
			.setPositiveButton(
					R.string.rom_menu_pos,
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
							if(selected_rom != null)
								selectRom(selected_rom);
							else
								showRomMenu();
						}
					}
			)
			.setNegativeButton(
					R.string.rom_menu_neg,
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							System.exit(1);
						}
					}
			)
			.show();
		} else {
			selectRom(roms.get(0));
		}
	}

	private void selectRom(RomDescriptor rom) {
		selected_rom = rom;
		String url = rom.url;
		rom_tgz = new File("/sdcard/" + url.substring(url.lastIndexOf('/')+1));

		String tgz = rom_tgz.getAbsolutePath();
		final String rom_tar = tgz.substring(0, tgz.length() - 3) + "tar";
		if(!rom_tgz.exists() && new File(rom_tar).exists()) {
			new AlertDialog.Builder(this)
			.setMessage("Found " + rom_tar + "; we probably shouldnt download the tgz again")
			.setCancelable(false)
			.setPositiveButton(
					R.string.select_rom_download_tgz,
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							doRomDownload();
						}
					}
			)
			.setNeutralButton(
					R.string.select_rom_flash_tar,
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							doRomInstall(new File(rom_tar));
						}
					}
			)
			.setNegativeButton(
					R.string.select_rom_neg,
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							System.exit(1);
						}
					}
			)
			.show();
			return;
		}

		doRomDownload();
	}

	private void doRomDownload() {
		try {
			File f = dh.downloadRom(selected_rom, rom_tgz);
			if(f == null) {
				// Wait for the callback
				return;
			}

			doRomInstall(f);
		} catch(Exception e) {
			showException(e);
			return;
		}
	}

	private void doRomInstall(final File rom) {
		// ROM is downloaded, ready to ask user about options
		new AlertDialog.Builder(this)
		.setMessage(R.string.confirm_flash_rom)
		.setCancelable(false)
		.setPositiveButton(
				R.string.confirm_flash_rom_yes,
				new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						confirmedRomInstall(rom);
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

	private void confirmedRomInstall(File rom) {
		try {
			SuperUser.oneShot("echo \"--install_tgz\" > /cache/recovery/command");
			SuperUser.oneShot("echo -n \"" + rom.getAbsolutePath() + "\" >> /cache/recovery/command");
			SuperUser.oneShot("/system/bin/reboot recovery");
		} catch(Exception e) {
			showException(e);
		}
	}

	protected void showException(Throwable ex) {
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
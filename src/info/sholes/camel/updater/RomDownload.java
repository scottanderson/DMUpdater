package info.sholes.camel.updater;

import info.sholes.camel.updater.DownloadHelper.RomDescriptor;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RomDownload extends Activity implements Caller<RomDownload.Callback> {
	enum Callback {
		ROM_DOWNLOAD,
		DOWNLOAD_CANCELLED
	}

	private DownloadHelper<Callback> dh = null;
	private RomDescriptor selected_rom = null;
	private File rom_tgz = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		try {
			dh = new DownloadHelper<Callback>(this, this);
		} catch (Exception e) {
			showException(e);
			return;
		}

		selectRom(getIntent().<RomDescriptor>getParcelableExtra("rom"));

		addText("This is the ROM download page. You selected " + getIntent().<RomDescriptor>getParcelableExtra("rom").name + ".");
	}

	private void selectRom(RomDescriptor rom) {
		selected_rom = rom;
		String url = rom.url;
		rom_tgz = new File("/sdcard/" + url.substring(url.lastIndexOf('/')+1));

		String tgz = rom_tgz.getAbsolutePath();
		String rom_tar_path = tgz.substring(0, tgz.length() - 3) + "tar";
		final File rom_tar = new File(rom_tar_path);
		if(!rom_tgz.exists() && rom_tar.exists()) {
			new AlertDialog.Builder(this)
			.setMessage("Found " + rom_tar_path + "; we probably shouldnt download the tgz again")
			.setCancelable(false)
			.setPositiveButton(
					R.string.select_rom_download_tgz,
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							rom_tar.delete();
							doRomDownload();
						}
					}
			)
			.setNeutralButton(
					R.string.select_rom_flash_tar,
					new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							doRomInstall(rom_tar);
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

	public void callback(Callback c) {
		switch(c) {
		case ROM_DOWNLOAD:
			doRomDownload();
			return;
		case DOWNLOAD_CANCELLED:
			finish();
			return;
		default:
			addText("Unknown callback: " + c.name());
			return;
		}
	}

	private void doRomDownload() {
		try {
			File f = dh.downloadFile(selected_rom.url, selected_rom.md5, rom_tgz, Callback.ROM_DOWNLOAD, Callback.DOWNLOAD_CANCELLED);
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
			switch(selected_rom.type) {
			case ROM_TGZ:
				SuperUser.oneShot("echo -n \"--install_tgz=" + rom.getAbsolutePath() + "\" > /cache/recovery/command");
				break;
			case UPDATE_ZIP:
				String path = rom.getAbsolutePath();
				if(!path.startsWith("/sdcard/"))
					throw new Exception("Roms go on /sdcard...what happened? " + path);
				path = "SDCARD:" + path.substring(8);
				// SPRecovery blocks update.zip via --update_pacakge, --allow_update_package let it know we're not Google
				SuperUser.oneShot("echo \"--allow_update_package\" > /cache/recovery/command");
				SuperUser.oneShot("echo -n \"--update_package=" + path + "\" >> /cache/recovery/command");
				break;
			default:
				throw new Exception("Unknown rom type: " + selected_rom.type.name());
			}
			SuperUser.oneShot("/system/bin/reboot recovery");
		} catch(Exception e) {
			showException(e);
		}
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

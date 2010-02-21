package org.droidmod.updater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.Formatter;

public class DownloadUtil<T> {

	private final Context ctx;
	private final Caller<T> caller;

	public DownloadUtil(Context u, Caller<T> caller) {
		this.ctx = u;
		this.caller = caller;
	}

	public void downloadFile(File fout, URL url, T callback, T callback_cancel) throws Exception {
		URLConnection uc = url.openConnection();
		int length = 0;
		try {
			length = Integer.parseInt(uc.getHeaderField("content-length"));
		} catch(Exception e) {}
		caller.addText("Downloading " + url.toString());
		downloadFile(fout, url.toString(), uc.getInputStream(), length, callback, callback_cancel);
	}

	private void downloadFile(final File fout, String from, InputStream is, int filelen, final T callback, final T callback_cancel) throws Exception {
		final OutputStream os = new FileOutputStream(fout);

		String msg = fout.getName();
		if(!from.endsWith(msg))
			msg = from.substring(from.lastIndexOf('/') + 1) + "\n" + msg;
		final String baseMessage = msg;

		final ProgressDialog pd = new ProgressDialog(ctx);
		final DownloadTask dt = new DownloadTask() {
			@Override
			protected void onProgressUpdate(Integer... values) {
				pd.incrementProgressBy(values[0].intValue());

				String c = Formatter.formatFileSize(ctx, pd.getProgress());
				String m = Formatter.formatFileSize(ctx, pd.getMax());
				String bps = Formatter.formatFileSize(ctx, values[1].intValue()) + "/s";
				int secondsleft = (pd.getMax() - pd.getProgress()) / values[1].intValue();
				String timeleft = "";
				if(secondsleft > 60) {
					int minutesleft = secondsleft / 60;
					secondsleft %= 60;
					if(minutesleft > 60) {
						int hoursleft = minutesleft / 60;
						minutesleft %= 60;
						timeleft += hoursleft + "h ";
					}
					timeleft += minutesleft + "m ";
				}
				timeleft += secondsleft + "s";
				pd.setMessage(baseMessage + "\n" + c + "/" + m + "\n" + bps + "\nETA: " + timeleft);
			}

			@Override
			protected void onPostExecute(Exception result) {
				pd.hide();
				if(result == null) {
					caller.callback(callback);
				} else {
					caller.showException(result);
				}
			}
		};

		pd.setTitle("Downloading");
		pd.setMessage(baseMessage);
		if(filelen == 0) {
			pd.setIndeterminate(true);
		} else {
			pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			pd.setMax(filelen);
		}
		pd.setButton("Cancel", new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dt.cancel(true);
				pd.hide();
				fout.delete();
				caller.callback(callback_cancel);
			}});
		pd.setCancelable(false);
		pd.show();

		dt.execute(is, os);
	}

	public static String md5(File f) throws Exception {
		return md5(new FileInputStream(f));
	}

	public static String md5(InputStream is) throws Exception {
		int bytes_read;
		byte[] buffer = new byte[1024];
		MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
		while((bytes_read = is.read(buffer)) > 0)
			digest.update(buffer, 0, bytes_read);
		byte[] hash = digest.digest();
		String md5 = "";
		for(byte h : hash)
			md5 += toHex(h);
		return md5;
	}

	public static String md5(InputStream is, int length) throws Exception {
		int bytes_read;
		byte[] buffer = new byte[1024];
		MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
		int total = 0;
		while((bytes_read = is.read(buffer)) > 0) {
			digest.update(buffer, 0, bytes_read);

			total += bytes_read;
			if(total >= length) {
				if(total == length)
					break;
				throw new RuntimeException("too much");
			}
		}
		if(total < length)
			throw new RuntimeException("not enough");
		byte[] hash = digest.digest();
		String md5 = "";
		for(byte h : hash)
			md5 += toHex(h);
		return md5;
	}

	private static String toHex(byte h) {
		return hexChar((h >> 4) & 0x0F) + hexChar(h & 0x0F);
	}

	private static String hexChar(int i) {
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
}

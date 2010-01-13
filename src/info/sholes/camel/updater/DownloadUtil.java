package info.sholes.camel.updater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;

import android.app.ProgressDialog;
import android.content.res.AssetFileDescriptor;

public class DownloadUtil {
	
	private final Updater u;
	
	public DownloadUtil(Updater u) {
		this.u = u;
	}

	public void downloadFile(File fout, String asset_filename, boolean append, Callback callback) throws Exception {
		AssetFileDescriptor fd = u.getAssets().openFd(asset_filename);
		downloadFile(fout, asset_filename, fd.createInputStream(), (int)fd.getLength(), append, callback);
	}

	public void downloadFile(File fout, URL url, Callback callback) throws Exception {
		URLConnection uc = url.openConnection();
		int length = 0;
		try {
			length = Integer.parseInt(uc.getHeaderField("content-length"));
		} catch(Exception e) {}
		u.addText("Downloading " + url.toString());
		downloadFile(fout, url.toString(), uc.getInputStream(), length, false, callback);
	}

	private void downloadFile(File fout, String from, InputStream is, int filelen, boolean append, final Callback callback) throws Exception {
		if(!append && fout.exists())
			fout.delete();
		final OutputStream os = new FileOutputStream(fout, append);

		final ProgressDialog pd = new ProgressDialog(u);
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
					u.callback(callback);
				} else {
					u.showException(result);
				}
			}
		}.execute(is, os);
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
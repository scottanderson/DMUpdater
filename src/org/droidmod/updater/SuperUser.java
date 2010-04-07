package org.droidmod.updater;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.droidmod.updater.DownloadUtil.MD5Callback;

public class SuperUser {
	private final Process p;
	private final BufferedWriter in;
	private final BufferedReader out;
	private final BufferedReader err;

	/**
	 * To prevent the force close dialog
	 */
	public static boolean isRemembered(Updater u) throws Exception {
		SuperUser su = new SuperUser();
		su.in.write("exit");
		su.in.newLine();
		su.in.flush();

		// Wait up to 1 second for 'su' to return control to us
		long start = System.currentTimeMillis();
		while(System.currentTimeMillis() - start < 2000) {
			try {
				int ret = su.p.exitValue();
				su.checkErr();
				return (ret == 0);
			} catch(IllegalThreadStateException e) {
				// su hasn't returned yet, keep waiting
				Thread.sleep(50);
			}
		}

		// timeout
		return false;
	}

	public static String oneShot(String command) throws Exception {
		SuperUser su = new SuperUser();
		su.in.write(command);
		su.in.newLine();
		su.in.write("exit");
		su.in.newLine();
		su.in.flush();
		su.p.waitFor();

		String output = null;
		try {
			String line;
			while (su.out.ready() && (line = su.out.readLine()) != null) {
				if(output == null)
					output = line;
				else
					output += "\n" + line;
			}
		} catch (IOException e) {
			// It seems IOException is thrown when it reaches EOF.
		}
		su.checkErr();
		return output;
	}

	public static <T> void oneShotMd5(String description, String command, int length, DownloadUtil<T> du, MD5Callback callback) throws Exception {
		SuperUser su = new SuperUser();
		su.in.write(command);
		su.in.newLine();
		su.in.write("exit");
		su.in.newLine();
		su.in.flush();

		du.md5(description, su.p.getInputStream(), length, callback);
	}

	private SuperUser() throws Exception {
		p = Runtime.getRuntime().exec("su");

		in = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
		out = new BufferedReader(new InputStreamReader(p.getInputStream()));
		err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		checkErr();
	}

	private void checkErr() throws Exception {
		String line;
		String error = null;
		while (err.ready() && (line = err.readLine()) != null) {
			if(error == null)
				error = line;
			else
				error += "\n" + line;
		}
		if((error != null) && (error.length() > 0))
			throw new Exception(error);
	}
}

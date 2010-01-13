package info.sholes.camel.updater;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class SuperUser {
	private final Process p;
	private final BufferedWriter in;
	private final BufferedReader out;
	private final BufferedReader err;

	public static String oneShot(String command) throws Exception {
		SuperUser su = new SuperUser();
		su.in.write(command);
		su.in.newLine();
		su.in.write("exit");
		su.in.newLine();
		su.in.flush();
		su.p.waitFor();

		String output = "";
		try {
			String line;
			while (su.out.ready() && (line = su.out.readLine()) != null) {
				output += line + "\n";
			}
		} catch (IOException e) {
			// It seems IOException is thrown when it reaches EOF.
		}
		su.checkErr();
		return output;
	}
	
	public static String oneShotMd5(String command, int length) throws Exception {
		SuperUser su = new SuperUser();
		su.in.write(command);
		su.in.newLine();
		su.in.write("exit");
		su.in.newLine();
		su.in.flush();
		
		return DownloadUtil.md5(su.p.getInputStream(), length);
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
		String error = "";
		while (err.ready() && (line = err.readLine()) != null) {
			error += line + "\n";
		}
		if(error.length() > 0)
			throw new Exception(error);
	}

	public String exec(String command) throws Exception {
		in.write(command);
		in.newLine();
		in.flush();

		long start = System.currentTimeMillis();

		String output = "";
		try {
			String line;
			// wait up to 5 seconds for something to appear
			while((output.length() == 0) && (System.currentTimeMillis() - start < 5000)) {
				while (out.ready() && (line = out.readLine()) != null) {
					output += line + "\n";
				}
				Thread.yield();
				Thread.sleep(10);
			}
		} catch (IOException e) {
			// It seems IOException is thrown when it reaches EOF.
		}

		checkErr();

		return output;
	}
}

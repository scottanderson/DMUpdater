package org.droidmod.updater;

import java.io.InputStream;
import java.io.OutputStream;

import android.os.AsyncTask;

public class DownloadTask extends AsyncTask<Object, Integer, Exception> {

	@Override
	protected Exception doInBackground(Object... params) {
		try {
			InputStream in = (InputStream)params[0];
			OutputStream os = (OutputStream)params[1];

			byte[] buf = new byte[32768];
			int read;
			int readSinceProgress = 0;
			long lastupdate = System.currentTimeMillis();
			float bps_avg = 0;
			int avg_count = 0;

			while((read = in.read(buf)) > 0) {
				os.write(buf, 0, read);
				readSinceProgress += read;

				long now = System.currentTimeMillis();
				if(now - lastupdate > 250) {
					// Calculate instantaneous bps
					float bps = readSinceProgress * 1000 / (now - lastupdate);
					// Average it out
					bps_avg = ((bps_avg * avg_count) + bps) / (avg_count + 1);
					if(avg_count < 20)
						avg_count++;
					// Update the progress bar
					publishProgress(new Integer(readSinceProgress), new Integer((int) bps_avg));

					lastupdate = now;
					readSinceProgress = 0;
					Thread.yield();
				}
			}
			os.close();
			in.close();

			return null;
		} catch(Exception e) {
			return e;
		}
	}

}

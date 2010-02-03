package info.sholes.camel.updater;

import java.io.InputStream;
import java.io.OutputStream;

import android.os.AsyncTask;

public class DownloadTask extends AsyncTask<Object, Integer, Exception> {

	@Override
	protected Exception doInBackground(Object... params) {
		try {
			InputStream in = (InputStream)params[0];
			OutputStream os = (OutputStream)params[1];

			byte[] buf = new byte[40960];
			int read;
			int readSinceProgress = 0;
			long lastupdate = System.currentTimeMillis();
			long downloadStarted = System.currentTimeMillis();
			float bps_avg = 0;
			int avg_count = 0;

			while((read = in.read(buf)) > 0) {
				os.write(buf, 0, read);
				readSinceProgress += read;

				if(readSinceProgress < 4096)
					continue;

				long now = System.currentTimeMillis();
				if(now - lastupdate > 100) {
					float bps = readSinceProgress * 1000 / (now - lastupdate);
					bps_avg = ((bps_avg * avg_count) + bps) / (avg_count + 1);
					if(avg_count < 200)
						avg_count++;
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

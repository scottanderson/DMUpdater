package info.sholes.camel.updater;

import info.sholes.camel.updater.DownloadHelper.RomDescriptor;

import java.io.PrintWriter;
import java.io.StringWriter;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RomDownload extends Activity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		RomDescriptor rd = getIntent().getParcelableExtra("rom");

		addText("This is the ROM download page. You selected " + rd.name + ".");
	}

	protected void showException(Throwable ex) {
		Log.e("SMUpdater", ex.getMessage(), ex);

		new AlertDialog.Builder(this)
		.setTitle("An error has occurred!")
		.setMessage(ex.getMessage())
		.show();

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

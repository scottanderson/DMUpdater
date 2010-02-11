package info.sholes.camel.updater;

import info.sholes.camel.updater.DownloadHelper.RomDescriptor;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TwoLineListItem;

public class RomMenu extends ListActivity {
	private RomDescriptor[] roms;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Parcelable[] proms = getIntent().getParcelableArrayExtra("roms");
		roms = new RomDescriptor[proms.length];
		for(int i = 0; i < proms.length; i++)
			roms[i] = (RomDescriptor)proms[i];

		setListAdapter(new RomMenuAdapter(this, roms));
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		RomDescriptor selected_rom = roms[position];

		// Kick off a download activity
		Intent i = new Intent(this, RomDownload.class);
		i.putExtra("rom", selected_rom);
		startActivity(i);
	}

	public class RomMenuAdapter extends BaseAdapter {
		private final RomDescriptor[] roms;
		private final LayoutInflater inflater;

		public RomMenuAdapter(Context context, RomDescriptor[] roms) {
			this.roms = roms;
			inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public int getCount() {
			return roms.length;
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			LinearLayout view;
			TwoLineListItem line;
			if(convertView != null) {
				view = (LinearLayout)convertView;
				line = (TwoLineListItem) view.getChildAt(1);
			} else {
				view = (LinearLayout)inflater.inflate(R.layout.roms, parent, false);
				line = (TwoLineListItem)inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
				view.addView(line);
			}

			ImageView icon = (ImageView) view.getChildAt(0);
			icon.setImageResource(roms[position].icon);

			line.getText1().setText(roms[position].name);
			line.getText2().setText(roms[position].dispid);
			return view;
		}

	}

}

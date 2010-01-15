package info.sholes.camel.updater;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DownloadHelper {
	private static XMLElementDecorator xed = null;
	private static XMLElementDecorator getFileXml(String type) throws Exception {
		if(xed == null)
			xed = XMLElementDecorator.parse("http://sp.sholes.info/smupdater.xml").getChild("smupdater");
		for(XMLElementDecorator file : xed.getChild("files").getChildren("file")) {
			if(!type.equals(file.getAttribute("type")))
				continue;
			return file;
		}
		return null;
	}
	
	enum Downloadable {
		ROOT("root"),
		FLASH_IMAGE("flash_image"),
		RECOVERY_IMAGE("recovery");
		
		public final String url;
		public final String md5;
		
		Downloadable(String type) {
			XMLElementDecorator xed;
			try {
				xed = getFileXml(type);
			} catch (Exception e) {
				// HACK: prevent the compiler from complaining about this not being allowed
				throw new RuntimeException(e);
			}
			url = xed.getChild("url").getString();
			md5 = xed.getChild("md5").getString();
		}
		
	}
	
	class RomDescriptor {
		public final String name;
		public final String dispid;
		public final String url;
		public final String md5;
		private RomDescriptor(String name, String dispid, String url, String md5) {
			this.name = name;
			this.dispid = dispid;
			this.url = url;
			this.md5 = md5;
		}
	}
	
	private final Updater u;
	private final DownloadUtil du;
	private int download_attempts = 0;
	
	public DownloadHelper(Updater u) {
		this.u = u;
		du = new DownloadUtil(u);
	}
	
	public void resetDownloadAttempts() {
		download_attempts = 0;
	}
	
	public File downloadFile(Downloadable which, File where, Callback cb) throws Exception {
		return downloadFile(which.url, which.md5, where, cb);
	}
	
	private File downloadFile(String url, String expect_md5, File where, Callback cb) throws Exception {
		while(where.exists()) {
			String actual_md5;
			try {
				actual_md5 = DownloadUtil.md5(where);
			} catch (Exception e) {
				// Re-download
				break;
			}
			if(expect_md5.equals(actual_md5)) {
				// Got the file
				return where;
			} else {
				u.addText("Unknown MD5: " + actual_md5);
				// Fall-through to re-download
				break;
			}
		}

		download_attempts++;
		if(download_attempts >= 3)
			throw new Exception("It's hopeless; giving up");
		du.downloadFile(where, new URL(url), cb);
		return null;
	}
	
	public List<RomDescriptor> getRoms() {
		List<RomDescriptor> roms = new ArrayList<RomDescriptor>();
		
		for(XMLElementDecorator rom : xed.getChild("roms").getChildren("rom")) {
			String name = rom.getAttribute("name");
			String dispid = rom.getAttribute("dispid");
			String url = rom.getChild("url").getString();
			String md5 = rom.getChild("md5").getString();
			roms.add(new RomDescriptor(name, dispid, url, md5));
		}
		
		return roms;
	}
	
	public File downloadRom(RomDescriptor rd, File rom_tgz) throws Exception {
		return downloadFile(rd.url, rd.md5, rom_tgz, Callback.ROM_DOWNLOAD);
	}

}

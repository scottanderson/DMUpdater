package org.droidmod.updater;

public interface Caller<T> {

	void callback(T callback);
	void showException(Throwable exception);
	void addText(String text);

}

package org.util.npci.pos.util;

public class TranUtil {

	public static final String truncateString(final String s, final int len) {
		if(s == null) return "";
		if(s.length() > len) return s.substring(0,len);
		return s;
	}
}

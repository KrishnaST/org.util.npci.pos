package org.util.npci.pos.transaction;

import org.util.iso8583.ISO8583Message;

public class TranUtil {

	public static final void removeNotRequired(ISO8583Message issuerResponse) {
		issuerResponse.remove(14);
		issuerResponse.remove(35);
		issuerResponse.remove(40);
		issuerResponse.remove(45);
		issuerResponse.remove(52);
		issuerResponse.remove(61);
		issuerResponse.remove(63);
	}
	
	public static final String truncateString(final String s, final int len) {
		if(s == null) return "";
		if(s.length() > len) return s.substring(0,len);
		return s;
	}
}

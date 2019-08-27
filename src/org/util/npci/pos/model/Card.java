package org.util.npci.pos.model;

import java.util.Date;

public class Card {

	public final int    brcode;
	public final int    status;
	public final int    badpin;
	public final Date   expiry;
	public final String offset;

	public Card(int brcode, int status, int badpin, Date expiry, String offset) {
		this.brcode = brcode;
		this.status = status;
		this.badpin = badpin;
		this.expiry = expiry;
		this.offset = offset;
	}

}

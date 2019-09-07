package org.util.npci.pos.model;

public final class Account {

	public int    branchCode;
	public String account32;
	public String account15;

	public Account() {}

	public Account(int branchCode, String account32) {
		this.branchCode = branchCode;
		this.account32  = account32;
	}

}

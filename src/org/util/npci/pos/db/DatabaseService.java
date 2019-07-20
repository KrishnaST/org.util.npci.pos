package org.util.npci.pos.db;

import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.pos.POSDispatcher;

public abstract class DatabaseService {

	public final POSDispatcher dispatcher;
	public final CoreConfig     config;

	public DatabaseService(final CoreConfig config, final POSDispatcher dispatcher) {
		this.config     = config;
		this.dispatcher = dispatcher;
	}

	public abstract String getName();

}

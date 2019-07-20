package org.util.npci.pos.cbs;

import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.pos.POSDispatcher;

public abstract class CoreBankingService {

	public final POSDispatcher dispatcher;
	public final CoreConfig     config;

	public CoreBankingService(final CoreConfig config, final POSDispatcher dispatcher) {
		this.config     = config;
		this.dispatcher = dispatcher;
	}

	public abstract String getName();


}

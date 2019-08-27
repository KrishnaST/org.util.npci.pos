package org.util.npci.pos.cbs;

import org.util.iso8583.ISO8583Message;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.pos.POSDispatcher;

public abstract class CoreBankingService {

	public final POSDispatcher dispatcher;
	public final CoreConfig    config;

	public CoreBankingService(final CoreConfig config, final POSDispatcher dispatcher) {
		this.config     = config;
		this.dispatcher = dispatcher;
	}

	public abstract String getName();

	public abstract ISO8583Message transaction(final ISO8583Message request, final Logger logger);

	public abstract ISO8583Message reversal(final ISO8583Message request, final Logger logger);

}

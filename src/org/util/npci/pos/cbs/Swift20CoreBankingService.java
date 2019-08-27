package org.util.npci.pos.cbs;

import org.util.iso8583.ISO8583Message;
import org.util.nanolog.Logger;
import org.util.npci.api.ConfigurationNotFoundException;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.pos.POSDispatcher;

public final class Swift20CoreBankingService extends CoreBankingService {

	public Swift20CoreBankingService(final CoreConfig config, final POSDispatcher dispatcher) throws ConfigurationNotFoundException {
		super(config, dispatcher);
	}

	@Override
	public final String getName() {
		return "SWIFT20";
	}

	@Override
	public final ISO8583Message transaction(ISO8583Message request, final Logger logger) {
		return null;
	}

	@Override
	public final ISO8583Message reversal(ISO8583Message request, final Logger logger) {
		return null;
	}


}

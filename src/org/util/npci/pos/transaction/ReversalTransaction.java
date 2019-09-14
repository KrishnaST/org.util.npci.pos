package org.util.npci.pos.transaction;

import org.util.iso8583.ISO8583Message;
import org.util.iso8583.npci.ResponseCode;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.issuer.IssuerTransaction;
import org.util.npci.pos.POSDispatcher;

public final class ReversalTransaction extends IssuerTransaction<POSDispatcher> {

	public ReversalTransaction(final ISO8583Message request, final POSDispatcher dispatcher) {
		super(request, dispatcher);
	}

	@Override
	protected final boolean execute(final Logger logger) {
		try {
			final ISO8583Message cbsResponse = dispatcher.coreBankingService.reversal(request, logger);
			if (ResponseCode.SUCCESS.equals(cbsResponse.get(39))) {
				logger.info("cbs successful response.");
				dispatcher.databaseService.reversePOSLimit(request.get(2), Double.parseDouble(request.get(4)) / 100.0, logger);
				logger.info("reversing transaction limit.");
				return sendResponseToNPCI(request, cbsResponse.get(39), logger);
			} else {
				logger.info("cbs unsuccessful.");
				return sendResponseToNPCI(request, cbsResponse.get(39), logger);
			}

		} catch (Exception e) {
			logger.info(e);
		}
		return false;
	}
}

package org.util.npci.pos.transaction;

import org.util.datautil.TLV;
import org.util.iso8583.ISO8583Message;
import org.util.iso8583.npci.ResponseCode;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.issuer.IssuerTransaction;
import org.util.npci.pos.POSDispatcher;

public final class ECommerceReversal extends IssuerTransaction<POSDispatcher> {

	public ECommerceReversal(final ISO8583Message request, final POSDispatcher dispatcher) {
		super(request, dispatcher);
	}

	protected static final String CVD_TAG = "054";

	@Override
	protected boolean execute(final Logger logger) {
		try {
			
			final ISO8583Message cbsResponse = dispatcher.coreBankingService.reversal(request, logger);
			request.put(48, new TLV().put("051", "POS01").build());
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

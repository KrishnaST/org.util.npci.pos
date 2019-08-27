package org.util.npci.pos.transaction;

import org.util.iso8583.ISO8583Message;
import org.util.iso8583.npci.ResponseCode;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.issuer.IssuerTransaction;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.model.Account;
import org.util.npci.pos.model.Card;

public final class PurchaseReversal extends IssuerTransaction<POSDispatcher> {

	public PurchaseReversal(final ISO8583Message request, final POSDispatcher dispatcher) {
		super(request, dispatcher);
	}

	private static final String CVD_TAG = "054";

	@Override
	protected boolean execute(final Logger logger) {
		try {

			final String key = request.getUniqueKey();
			logger.info("key : " + key);
			final Card    card    = dispatcher.databaseService.getCard(request.get(2), logger);
			final Account account = dispatcher.databaseService.getAccount(request.get(2), logger);

			final ISO8583Message original = dispatcher.databaseService.getTransactionByKey(key, logger);
			if (original == null) {
				logger.info("original transaction not found.");
				return sendResponseToNPCI(request, ResponseCode.DO_NOT_HONOR, logger);
			}

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

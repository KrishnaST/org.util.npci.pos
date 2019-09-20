package org.util.npci.pos.transaction;

import org.util.iso8583.ISO8583Message;
import org.util.iso8583.npci.DE90;
import org.util.iso8583.npci.ResponseCode;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.issuer.IssuerTransaction;
import org.util.npci.pos.POSDispatcher;

public final class ReversalTransaction extends IssuerTransaction<POSDispatcher> {

	protected static final String TYPE = "IREVERSAL";
	
	public ReversalTransaction(final ISO8583Message request, final POSDispatcher dispatcher) {
		super(request, dispatcher);
	}

	@Override
	protected final boolean execute(final Logger logger) {
		try {
			final long txId = dispatcher.databaseService.registerPOSRequest(request, TYPE, logger);
			logger.info("transaction id", Long.toString(txId));
			
			final DE90 de90 = DE90.parse(request.get(90));
			if(de90 == null) {
				logger.info("invalid original data elements.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.DO_NOT_HONOR, logger);
			}
			
			final ISO8583Message cbsresponse = dispatcher.coreBankingService.reversal(request, logger);
			if (cbsresponse != null && ResponseCode.SUCCESS.equals(cbsresponse.get(39))) {
				logger.info("cbs successful reversal.");
				dispatcher.databaseService.reversePOSLimit(request.get(2), Double.parseDouble(request.get(4)) / 100.0, logger);
				logger.info("reversing transaction limit.");
				return dispatcher.sendResponseToNPCI(txId, request, cbsresponse.get(39), logger);
			} else {
				logger.info("cbs unsuccessful.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.ISSUER_INOPERATIVE, logger);
			}
		} catch (Exception e) {logger.info(e);}
		return false;
	}
}

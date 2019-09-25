package org.util.npci.pos.transaction;

import org.util.iso8583.ISO8583Message;
import org.util.iso8583.ISO8583PropertyName;
import org.util.iso8583.npci.DE90;
import org.util.iso8583.npci.ResponseCode;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.issuer.IssuerTransaction;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.model.Account;

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
			
			logger.info(de90.toString());
			
			final ISO8583Message original = dispatcher.databaseService.getTransactionByTKey(de90.MTI, request.getTransactionKey(), logger);
			if(original == null) {
				logger.info("original transaction not found.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.DO_NOT_HONOR, logger);
			}
			
			final Account account = dispatcher.databaseService.getAccount(request.get(2), logger);
			logger.info(account);
			
			if(account != null) {
				request.put(102, account.account15);
			}
			
			final long original_txid = (long) original.getAdditional(ISO8583PropertyName.TRANSACTION_ID);
			logger.info("original transaction id", Long.toString(original_txid));
			final boolean isreversed = (boolean) original.getAdditional(ISO8583PropertyName.IS_REVERSED);
			
			
			final ISO8583Message cbsresponse = dispatcher.coreBankingService.reversal(request, logger);
			
			if (cbsresponse != null && ResponseCode.SUCCESS.equals(cbsresponse.get(39))) {
				logger.info("successful reversal.");
				if(!isreversed) {
					final boolean setreversed = dispatcher.databaseService.setReversalStatus(original_txid, true, logger);
					logger.info("set as reversed", Boolean.toString(setreversed));
					final boolean limit = dispatcher.databaseService.reversePOSLimit(request.get(2), Double.parseDouble(request.get(4)) / 100.0, logger);
					logger.info("reversed transaction limit", Boolean.toString(limit));
				}
				request.put(39, cbsresponse.get(39));
				return dispatcher.sendResponseToNPCI(txId, request, cbsresponse.get(39), logger);
			} else {
				if(!isreversed) dispatcher.databaseService.setReversalStatus(original_txid, false, logger);
				logger.info("cbs unsuccessful.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.ISSUER_INOPERATIVE, logger);
			}
		} catch (Exception e) {logger.info(e);}
		return false;
	}
}

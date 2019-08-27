package org.util.npci.pos.transaction;

import org.util.datautil.TLV;
import org.util.hsm.api.ThalesResponseCode;
import org.util.hsm.api.model.HSMResponse;
import org.util.iso8583.ISO8583Message;
import org.util.iso8583.ext.ISO8583DateField;
import org.util.iso8583.npci.ResponseCode;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.issuer.IssuerTransaction;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.model.Account;
import org.util.npci.pos.model.Card;
import org.util.npci.pos.model.Keys;

public final class ECommerceTransaction extends IssuerTransaction<POSDispatcher> {

	public ECommerceTransaction(final ISO8583Message request, final POSDispatcher dispatcher) {
		super(request, dispatcher);
	}

	protected static final String CVD_TAG = "053";

	protected boolean isExpired = true;
	protected boolean validCVV  = false;

	@Override
	protected boolean execute(final Logger logger) {
		try {
			final String key = request.getUniqueKey();
			logger.info("key : " + key);
			final Card    card    = dispatcher.databaseService.getCard(request.get(2), logger);
			final Account account = dispatcher.databaseService.getAccount(request.get(2), logger);
			final TLV     de48    = TLV.parse(request.get(48));
			request.put(48, new TLV().put("051", "POS01").put(CVD_TAG, "M").build());
			final ISO8583Message duplicate = dispatcher.databaseService.getTransactionByKey(key, logger);
			if (duplicate != null) {
				logger.info("duplicate transaction");
				return sendResponseToNPCI(request, ResponseCode.NO_ROUTING_AVAILABLE, logger);
			}

			if (ISO8583DateField.isExpired(request.get(14))) {
				logger.info("card is expired.");
				return sendResponseToNPCI(request, ResponseCode.EXPIRED_CARD, logger);
			} else isExpired = false;

			final Keys keys = dispatcher.databaseService.getKeys(request.get(2), logger);

			final HSMResponse cvvResponse = config.hsmService.cvv().validateCVV(config.hsmConfig, request.get(2), request.get(14), "000", keys.cvk1, keys.cvk2,
					de48.get("052"), logger);
			if (cvvResponse.isSuccess) validCVV = true;
			else if (ThalesResponseCode.FAILURE.equals(cvvResponse.responseCode)) {
				logger.info("invalid cvv.");
				request.put(48, new TLV().put("051", "POS01").put(CVD_TAG, "N").build());
				return sendResponseToNPCI(request, ResponseCode.DO_NOT_HONOR, logger);
			} else {
				logger.info("hsm error.");
				return sendResponseToNPCI(request, ResponseCode.ISSUER_INOPERATIVE, logger);
			}

			if (card.status != 1) {
				logger.info("card hotlisted. ");
				String responseCode = ResponseCode.RESTRICTED_CARD_CAPTURE;
				if (card.status == 2) responseCode = ResponseCode.PIN_TRIES_EXCEEDED;
				else if (card.status == 3) responseCode = ResponseCode.LOST_CARD_CAPTURE;
				else if (card.status == 4) responseCode = ResponseCode.STOLLEN_CARD_CAPTURE;
				return sendResponseToNPCI(request, responseCode, logger);
			}

			if (!dispatcher.databaseService.checkAndUpdatePOSLimit(request.get(2), Double.parseDouble(request.get(4)) / 100.0, logger)) {
				logger.info("limit exceeded.");
				String responseCode = ResponseCode.EXCEEDS_LIMIT;
				return sendResponseToNPCI(request, responseCode, logger);
			}

			final ISO8583Message cbsResponse = dispatcher.coreBankingService.transaction(request, logger);
			if (ResponseCode.SUCCESS.equals(cbsResponse.get(39)) && validCVV && !isExpired) {
				logger.info("cbs successful response.");
				request.put(39, cbsResponse.get(39));
				request.put(38, cbsResponse.get(38));
				request.put(102, account.accountNo);
				return sendResponseToNPCI(request, cbsResponse.get(39), logger);
			} else {
				logger.info("cbs unsuccessful or validCVV : " + validCVV + " isExpired : " + isExpired);
				dispatcher.databaseService.reversePOSLimit(request.get(2), Double.parseDouble(request.get(4)) / 100.0, logger);
				return sendResponseToNPCI(request, ResponseCode.SYSTEM_MALFUNCTION, logger);
			}
		} catch (Exception e) {
			logger.info(e);
		}
		return false;
	}

}

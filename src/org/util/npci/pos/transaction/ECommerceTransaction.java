package org.util.npci.pos.transaction;

import org.util.datautil.Strings;
import org.util.datautil.TLV;
import org.util.hsm.api.ThalesResponseCode;
import org.util.hsm.api.model.HSMResponse;
import org.util.iso8583.ISO8583Message;
import org.util.iso8583.ext.ISO8583DateField;
import org.util.iso8583.ext.PANUtil;
import org.util.iso8583.npci.ResponseCode;
import org.util.iso8583.npci.de48.DE48Tag;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.issuer.IssuerTransaction;
import org.util.npci.coreconnect.util.NPCIISOUtil;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.model.Account;
import org.util.npci.pos.model.Card;
import org.util.npci.pos.model.Keys;

public final class ECommerceTransaction extends IssuerTransaction<POSDispatcher> {

	public ECommerceTransaction(final ISO8583Message request, final POSDispatcher dispatcher) {
		super(request, dispatcher);
	}

	private static final String   TYPE    = "ECOMMERCE";

	@Override
	protected final boolean execute(final Logger logger) {
		try {
			boolean isExpired = true;
			boolean validCVV  = false;

			final long txId = dispatcher.databaseService.registerTransaction(request, TYPE, logger);
			final TLV de48 = TLV.parse(request.get(48));

			final Card card = dispatcher.databaseService.getCard(request.get(2), logger);
			logger.info(card);

			if (card == null) {
				logger.info("invalid track or card.");
				request.put(48, new TLV().put("051", "POS01").put(DE48Tag.CVD2_MATCH_RESULT, "N").build());
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.INVALID_CARD, logger);
			}

			final Account account = dispatcher.databaseService.getAccount(request.get(2), logger);
			logger.info(account);

			if (account == null || Strings.isNullOrEmpty(account.account15)) {
				logger.info("invalid account");
				request.put(48, new TLV().put("051", "POS01").put(DE48Tag.CVD2_MATCH_RESULT, "N").build());
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.DO_NOT_HONOR, logger);
			}

			request.put(48, new TLV().put("051", "POS01").put(DE48Tag.CVD2_MATCH_RESULT, "M").build());

			if (card.status != 1) {
				String responseCode = ResponseCode.RESTRICTED_CARD_CAPTURE;
				if (card.status == 2) responseCode = ResponseCode.PIN_TRIES_EXCEEDED;
				else if (card.status == 3) responseCode = ResponseCode.LOST_CARD_CAPTURE;
				else if (card.status == 4) responseCode = ResponseCode.STOLLEN_CARD_CAPTURE;
				logger.info("card hotlisted.");
				return dispatcher.sendResponseToNPCI(txId, request, responseCode, logger);
			}

			request.put(102, account.account15);

			if (ISO8583DateField.isExpired(request.get(14))) {
				logger.info("card is expired.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.EXPIRED_CARD, logger);
			} else isExpired = false;

			final Keys keys = dispatcher.databaseService.getKeys(PANUtil.getBIN(request.get(2)), logger);
			logger.info(keys);

			if (keys == null) {
				logger.info("keys not configured.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.INVALID_CARD, logger);
			}

			final HSMResponse cvvResponse = config.hsmService.cvv().validateCVV(config.hsmConfig, request.get(2), request.get(14), "000", keys.cvk1, keys.cvk2, de48.get(DE48Tag.CVD2), logger);
			if (cvvResponse.isSuccess) validCVV = true;
			else if (ThalesResponseCode.FAILURE.equals(cvvResponse.responseCode)) {
				request.put(48, new TLV().put("051", "POS01").put(DE48Tag.CVD2_MATCH_RESULT, "N").build());
				logger.info("invalid cvv.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.DO_NOT_HONOR, logger);
			} else {
				logger.info(cvvResponse);
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.ISSUER_INOPERATIVE, logger);
			}

			if (!dispatcher.databaseService.checkAndUpdatePOSLimit(request.get(2), Double.parseDouble(request.get(4)) / 100.0, logger)) {
				logger.info("limit exceeded.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.EXCEEDS_LIMIT, logger);
			}

			final ISO8583Message cbsresponse = dispatcher.coreBankingService.transaction(request, logger);
			if (ResponseCode.SUCCESS.equals(cbsresponse.get(39)) && validCVV && !isExpired) {
				request.put(39, cbsresponse.get(39));
				request.put(38, cbsresponse.get(38));
				request.put(102, account.account15);
				logger.info("cbs successful response.");
				return dispatcher.sendResponseToNPCI(txId, request, cbsresponse.get(39), logger);
			} else if (!ResponseCode.SUCCESS.equals(cbsresponse.get(39))) {
				logger.info("transaction declined at CBS.");
				dispatcher.databaseService.reversePOSLimit(request.get(2), Double.parseDouble(request.get(4)) / 100.0, logger);
				return dispatcher.sendResponseToNPCI(txId, request, cbsresponse.get(39), logger);
			} else {
				logger.info("validCVV : " + validCVV + " isExpired : " + isExpired);
				dispatcher.databaseService.reversePOSLimit(request.get(2), Double.parseDouble(request.get(4)) / 100.0, logger);
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.SYSTEM_MALFUNCTION, logger);
			}
		} catch (Exception e) {
			logger.info(e);
		}
		return false;
	}

	protected final boolean sendResponseToNPCI(final ISO8583Message response, final String responseCode, final String account, final Logger logger) {
		request.put(39, responseCode);
		if (request.get(39) == null) logger.error(new Exception("empty response code"));
		NPCIISOUtil.removeNotRequiredElements(response);
		return config.coreconnect.sendResponseToNPCI(request, logger);
	}
}

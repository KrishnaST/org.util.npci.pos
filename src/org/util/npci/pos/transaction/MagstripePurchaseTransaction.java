package org.util.npci.pos.transaction;

import org.util.datautil.Strings;
import org.util.datautil.TLV;
import org.util.hsm.api.ThalesResponseCode;
import org.util.hsm.api.constants.PinBlockFormat;
import org.util.hsm.api.model.HSMResponse;
import org.util.hsm.api.util.Utils;
import org.util.iso8583.ISO8583Message;
import org.util.iso8583.ext.ISO8583DateField;
import org.util.iso8583.ext.PANUtil;
import org.util.iso8583.ext.Track2;
import org.util.iso8583.npci.ResponseCode;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.issuer.IssuerTransaction;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.model.Account;
import org.util.npci.pos.model.Card;
import org.util.npci.pos.model.Keys;

public final class MagstripePurchaseTransaction extends IssuerTransaction<POSDispatcher> {

	protected static final String CVD_TAG = "054";
	protected static final String TYPE    = "IPURCHASE";

	public MagstripePurchaseTransaction(final ISO8583Message request, final POSDispatcher dispatcher) {
		super(request, dispatcher);
	}

	@Override
	protected final boolean execute(final Logger logger) {
		try {
			boolean isExpired = true;
			boolean validCVV  = false;
			boolean validPIN  = false;

			final long txId = dispatcher.databaseService.registerTransaction(request, TYPE, logger);
			
			final Card card = dispatcher.databaseService.getCard(request.get(2), logger);
			logger.info(card);

			final Track2 track2 = Track2.parse(request.get(35));
			logger.info("track2", track2 != null ? track2.buildMaskedTrack2() : null);

			if (track2 == null || card == null) {
				logger.info("invalid track or card.");
				request.put(48, new TLV().put("051", "POS01").put(CVD_TAG, "N").build());
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.INVALID_CARD, logger);
			}

			final Account account = dispatcher.databaseService.getAccount(request.get(2), logger);
			logger.info(account);
			
			if (account == null || Strings.isNullOrEmpty(account.account15)) {
				logger.info("invalid account");
				request.put(48, new TLV().put("051", "POS01").put(CVD_TAG, "N").build());
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.DO_NOT_HONOR, logger);
			}

			request.put(48, new TLV().put("051", "POS01").put(CVD_TAG, "M").build());
			
			if (card.status != 1) {
				String responseCode = ResponseCode.RESTRICTED_CARD_CAPTURE;
				if (card.status == 2) responseCode = ResponseCode.PIN_TRIES_EXCEEDED;
				else if (card.status == 3) responseCode = ResponseCode.LOST_CARD_CAPTURE;
				else if (card.status == 4) responseCode = ResponseCode.STOLLEN_CARD_CAPTURE;
				logger.info("card hotlisted.");
				return dispatcher.sendResponseToNPCI(txId, request, responseCode, logger);
			}
			
			request.put(102, account.account15);
			
			if (ISO8583DateField.isExpired(track2.expiry)) {
				logger.info("card is expired.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.EXPIRED_CARD, logger);
			} else isExpired = false;

			
			final Keys keys = dispatcher.databaseService.getKeys(PANUtil.getBIN(request.get(2)), logger);
			logger.info(keys);
			
			if(keys == null) {
				logger.info("keys not configured.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.INVALID_CARD, logger);
			}
			
			final HSMResponse cvvResponse = config.hsmService.cvv().validateCVV(config.hsmConfig, request.get(2), track2.expiry, track2.servicecode, keys.cvk1,
					keys.cvk2, track2.cvv, logger);
			if (cvvResponse.isSuccess) validCVV = true;
			else if (ThalesResponseCode.FAILURE.equals(cvvResponse.responseCode)) {
				request.put(48, new TLV().put("051", "POS01").put(CVD_TAG, "N").build());
				logger.info("invalid cvv.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.DO_NOT_HONOR, logger);
			} else {
				logger.info(cvvResponse);
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.ISSUER_INOPERATIVE, logger);
			}

			final HSMResponse pinResponse = config.hsmService.ibm().validateInterchangePin(config.hsmConfig, Utils.getPAN12(request.get(2)),
					Utils.getValidationData(request.get(2)), request.get(52), PinBlockFormat.ANSIX98_FORMAT0, card.offset, keys.pvk, keys.zpk, logger);

			if (pinResponse.isSuccess) {
				validPIN = true;
				dispatcher.databaseService.clearBadPin(request.get(2), logger);
			}
			else if (ThalesResponseCode.FAILURE.equals(pinResponse.responseCode)) {
				logger.info("invalid pin.");
				dispatcher.databaseService.updateBadPin(request.get(2), logger);
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.INCORRECT_PIN, logger);
			} else {
				logger.info(pinResponse);
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.ISSUER_INOPERATIVE, logger);
			}

			if (!dispatcher.databaseService.checkAndUpdatePOSLimit(request.get(2), Double.parseDouble(request.get(4)) / 100.0, logger)) {
				logger.info("limit exceeded.");
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.EXCEEDS_LIMIT, logger);
			}

			final ISO8583Message cbsresponse = dispatcher.coreBankingService.transaction(request, logger);
			if (ResponseCode.SUCCESS.equals(cbsresponse.get(39)) && validCVV && validPIN && !isExpired) {
				request.put(39, cbsresponse.get(39));
				request.put(38, cbsresponse.get(38));
				request.put(102, account.account15);
				logger.info("cbs successful response.");
				return dispatcher.sendResponseToNPCI(txId, request, cbsresponse.get(39), logger);
			} else if(!ResponseCode.SUCCESS.equals(cbsresponse.get(39))) {
				logger.info("transaction declined at CBS.");
				dispatcher.databaseService.reversePOSLimit(request.get(2), Double.parseDouble(request.get(4)) / 100.0, logger);
				return dispatcher.sendResponseToNPCI(txId, request, cbsresponse.get(39), logger);
			} else {
				logger.info("validCVV : " + validCVV + " validPIN : " + validPIN + " isExpired : " + isExpired);
				dispatcher.databaseService.reversePOSLimit(request.get(2), Double.parseDouble(request.get(4)) / 100.0, logger);
				return dispatcher.sendResponseToNPCI(txId, request, ResponseCode.SYSTEM_MALFUNCTION, logger);
			}
		} catch (Exception e) {logger.info(e);}
		return false;
	}
	
	

}

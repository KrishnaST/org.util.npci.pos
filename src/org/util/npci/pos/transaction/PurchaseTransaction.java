package org.util.npci.pos.transaction;


import org.util.datautil.TLV;
import org.util.iso8583.ISO8583Message;
import org.util.iso8583.ext.ISO8583DateField;
import org.util.iso8583.ext.PANUtil;
import org.util.iso8583.ext.Track2;
import org.util.iso8583.npci.MTI;
import org.util.iso8583.npci.ResponseCode;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.issuer.IssuerTransaction;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.model.CBSRequest;
import org.util.npci.pos.model.CBSResponse;
import org.util.npci.pos.model.Card;

public final class PurchaseTransaction extends IssuerTransaction<POSDispatcher> {

	public PurchaseTransaction(ISO8583Message request, POSDispatcher dispatcher) {
		super(request, dispatcher);
	}

	protected static final String CVD_TAG 	= "054";
	
	private final CBSRequest	cbsRequest	= new CBSRequest();
	private final CBSResponse	cbsResponse	= new CBSResponse();
	
	protected boolean isExpired = true;
	protected boolean validCVV 	= false;
	protected boolean validPIN 	= false;
	
	protected TLV DE48 = new TLV();
	protected Track2 track2 = null;
	protected Card card = null;
	protected String account = null;
	protected String key = null;
	
	
	@Override
	protected void execute(final Logger logger) {
		try
		{
			key = request.getKey();
			logger.info("key : "+key);
			DE48 = TLV.parse(request.get(48));
			track2 = Track2.parse(request.get(35));
			logger.info("track2 : "+LoggingUtil.toJson(track2));
			card = DBUtil.getCard(request.get(2), logger);
			logger.info("card : "+LoggingUtil.toJson(card));
			account = DBUtil.getAccountNo(request.get(2), logger);
			request.put(48, new TLV().put("051", "POS01").put(CVD_TAG, "M").build());
			boolean isDuplicate = DBUtil.isDuplicateTransaction(txid, MTI.TRANS_REQUEST, key, request.get(13), logger);
			if(isDuplicate) {
				logger.info("duplicate transaction");
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, ResponseCode.NO_ROUTING_AVAILABLE, null, null, logger);
				return;
			}
			
			if(!DBUtil.localBINs.contains(PANUtil.getBIN(request.get(2)))) {
				logger.info("invalid bin.");
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, ResponseCode.NO_ROUTING_AVAILABLE, null, null, logger);
				return;
			}
			logger.info("request.get(32) : "+!DBUtil.issuerIds.contains(request.get(32)));
			
			if(!DBUtil.issuerIds.contains(request.get(32))) {
				logger.info("invalid issuerid.");
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, ResponseCode.NO_ROUTING_AVAILABLE, null, null, logger);
				return;
			}
			
			if(track2 == null || card == null || account == null) {
				logger.info("invalid track data.");
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, ResponseCode.INVALID_CARD, null, null, logger);
				return;
			}
			
			if(ISO8583DateField.isExpired(track2.expiry)) {
				logger.info("card is expired.");
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, ResponseCode.EXPIRED_CARD, null, null, logger);
				return;
			}
			else isExpired = false;
			
			String[] cvks = DBUtil.getCVK(PANUtil.getBIN(request.get(2)));
			String cvvResponse = HSM.validateCVV(cvks[0], cvks[1], request.get(2), track2.expiry, track2.servicecode, track2.cvv, logger);
			if(ResponseCode.SUCCESS.equals(cvvResponse)) validCVV = true;
			else if(ResponseCode.FAILURE.equals(cvvResponse)){
				logger.info("invalid cvv.");
				removeNotRequired(request);
				request.put(48, new TLV().put("051", "POS01").put(CVD_TAG, "N").build());
				sendResponse(request, MTI.TRANS_RESPONSE, ResponseCode.DO_NOT_HONOR, null, null, logger);
				return;
			}
			else {
				logger.info("hsm error.");
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, ResponseCode.ISSUER_INOPERATIVE, null, null, logger);
				return;
			}
			
			String zpk = DBUtil.getZPK(PANUtil.getBIN(request.get(2)));
			String pvk = DBUtil.getPVK(PANUtil.getBIN(request.get(2)));
			
			String pinResponse = HSM.validateInterchangePin(zpk, pvk, request.get(52), request.get(2), card.offset, logger);
			if(ResponseCode.SUCCESS.equals(pinResponse)) validPIN = true;
			else if(ResponseCode.FAILURE.equals(pinResponse)) {
				logger.info("invalid pin.");
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, ResponseCode.INCORRECT_PIN, null, null, logger);
				return;
			}
			else {
				logger.info("hsm error.");
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, ResponseCode.ISSUER_INOPERATIVE, null, null, logger);
				return;
			}
			
			if(card.status != 1) {
				logger.info("card hotlisted. ");
				String responseCode = ResponseCode.RESTRICTED_CARD_CAPTURE;
				if(card.status == 2)  responseCode = ResponseCode.PIN_TRIES_EXCEEDED;
				else if(card.status == 3) responseCode = ResponseCode.LOST_CARD_CAPTURE;
				else if(card.status == 4) responseCode = ResponseCode.STOLLEN_CARD_CAPTURE;
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, responseCode, null, null, logger);
				return;
			}
			
			if(!DBUtil.checkUpdatePOSLimit(request.get(2), Double.parseDouble(request.get(4))/100.0, logger)) {
				logger.info("limit exceeded.");
				String responseCode = ResponseCode.EXCEEDS_LIMIT;
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, responseCode, null, null, logger);
				return;
			}
			
			cbsRequest.mti 			= request.get(0);
			cbsRequest.pan 			= request.get(2);
			cbsRequest.pcode 		= request.get(3);
			cbsRequest.amount 		= request.get(4);
			cbsRequest.de7 			= request.get(7);
			cbsRequest.stan 		= request.get(11);
			cbsRequest.time 		= request.get(12);
			cbsRequest.day 			= request.get(13);
			cbsRequest.countrycode 	= request.get(19);
			cbsRequest.acqid 		= request.get(32);
			cbsRequest.rrn 			= request.get(37);
			cbsRequest.tid 			= request.get(41);
			cbsRequest.currencycode = request.get(49);
			cbsRequest.cashback 	= request.get(54);
			cbsRequest.account 		= account;
			
			CBSConnector.cbcon.send(cbsRequest, cbsResponse, logger);
			if(ResponseCode.SUCCESS.equals(cbsResponse.responsecode) && validCVV && validPIN && !isExpired) {
				logger.info("cbs successful response.");
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, cbsResponse.responsecode, cbsResponse.authcode, account, logger);
				return;
			}
			else {
				logger.info("cbs unsuccessful or validCVV : "+validCVV+" validPIN : "+validPIN+" isExpired : "+isExpired);
				DBUtil.reversePOSLimit(request.get(2), Double.parseDouble(request.get(4))/100.0, logger);
				removeNotRequired(request);
				sendResponse(request, MTI.TRANS_RESPONSE, cbsResponse.responsecode, null, null, logger);
				return;
			}
		} catch (Exception e) {logger.info(e);}
	}

	
	public static final void removeNotRequired(ISO8583Message issuerResponse) {
		issuerResponse.remove(14);
		issuerResponse.remove(18);
		issuerResponse.remove(22);
		//issuerResponse.remove(23);
		issuerResponse.remove(25);
		issuerResponse.remove(35);
		issuerResponse.remove(40);
		issuerResponse.remove(42);
		issuerResponse.remove(43);
		issuerResponse.remove(45);
		issuerResponse.remove(52);
		issuerResponse.remove(61);
		issuerResponse.remove(63);
	}

}

package org.util.npci.pos.transaction;


import org.util.iso8583.ISO8583Message;
import org.util.iso8583.ext.PANUtil;
import org.util.iso8583.npci.MTI;
import org.util.iso8583.npci.ResponseCode;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.issuer.IssuerTransaction;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.model.CBSRequest;
import org.util.npci.pos.model.CBSResponse;
import org.util.npci.pos.model.Card;
import org.util.npci.pos.model.Transaction;


public class PurchaseReversal extends IssuerTransaction<POSDispatcher> {

	protected static final String CVD_TAG 	= "054";
	
	private final CBSRequest	cbsRequest	= new CBSRequest();
	private final CBSResponse	cbsResponse	= new CBSResponse();
	
	protected Card card = null;
	protected String account = null;
	protected String key = null;
	
	public PurchaseReversal(ISO8583Message request) {
		super(request);
	}
	
	@Override
	protected void execute(final Logger logger) {
		try
		{
			key = request.getKey();
			logger.info("key : "+key);
			card = DBUtil.getCard(request.get(2), logger);
			account = DBUtil.getAccountNo(request.get(2), logger);

			Transaction original = DBUtil.getTransaction(MTI.TRANS_REQUEST, key, logger);
			logger.info("original transaction : "+LoggingUtil.toJson(original));
			
			if(original == null) {
				logger.info("original transaction not found.");
				removeNotRequired(request);
				sendResponse(request, MTI.ISS_REVERSAL_RESPONSE, ResponseCode.DO_NOT_HONOR, null, null, logger);
				return;
			}
			
			Transaction reversal = DBUtil.getDuplicateTransaction(txid, MTI.ISS_REVERSAL_REQUEST, key, logger);
			logger.info("duplicate reversal : "+LoggingUtil.toJson(reversal));
			
			if(!DBUtil.localBINs.contains(PANUtil.getBIN(request.get(2)))) {
				logger.info("invalid bin.");
				removeNotRequired(request);
				sendResponse(request, MTI.ISS_REVERSAL_RESPONSE, ResponseCode.NO_ROUTING_AVAILABLE, null, null, logger);
				return;
			}
			logger.info("request.get(32) : "+!DBUtil.issuerIds.contains(request.get(32)));
			
			if(!DBUtil.issuerIds.contains(request.get(32))) {
				logger.info("invalid issuerid.");
				removeNotRequired(request);
				sendResponse(request, MTI.ISS_REVERSAL_RESPONSE, ResponseCode.NO_ROUTING_AVAILABLE, null, null, logger);
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
			
			if(ResponseCode.SUCCESS.equals(cbsResponse.responsecode)) {
				logger.info("cbs successful response.");
				removeNotRequired(request);
				if(!original.reversed) {
					DBUtil.reversePOSLimit(request.get(2), Double.parseDouble(request.get(4))/100.0, logger);
					DBUtil.updateReversalStatus(original.txid, logger);
					logger.info("reversing transaction limit.");
				}
				sendResponse(request, MTI.ISS_REVERSAL_RESPONSE, cbsResponse.responsecode, null, null, logger);
				return;
			}
			else {
				logger.info("cbs unsuccessful.");
				removeNotRequired(request);
				sendResponse(request, MTI.ISS_REVERSAL_RESPONSE, cbsResponse.responsecode, null, null, logger);
				return;
			}
		
			
		} catch (Exception e) {logger.info(e);}
	}

	
	public static final void removeNotRequired(ISO8583Message issuerResponse) {
		issuerResponse.remove(14);
		issuerResponse.remove(18);
		issuerResponse.remove(22);
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

package org.util.npci.pos.cbs;

import java.util.Set;

import org.util.iso8583.EncoderDecoder;
import org.util.iso8583.ISO8583Message;
import org.util.iso8583.format.CBSFormat;
import org.util.iso8583.format.ISOFormat;
import org.util.iso8583.npci.ResponseCode;
import org.util.nanolog.Logger;
import org.util.npci.api.ConfigurationNotFoundException;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.PropertyName;

public final class Swift20CoreBankingService extends CoreBankingService {

	private static final ISOFormat CBS_FORMAT = CBSFormat.getInstance();
	private final String           CBS_IP     = config.getString(PropertyName.CBS_IP);
	private final int              CBS_PORT   = config.getInt(PropertyName.CBS_PORT);

	public Swift20CoreBankingService(final CoreConfig config, final POSDispatcher dispatcher) throws ConfigurationNotFoundException {
		super(config, dispatcher);
	}

	@Override
	public final String getName() {
		return "SWIFT20";
	}

	@Override
	public final ISO8583Message transaction(final ISO8583Message request, final Logger logger) {
		try {
			final ISO8583Message cbsrequest  = request.copy(Set.of(0, 2, 3, 4, 11, 12, 13, 19, 32, 37, 38, 41, 49, 54, 102));
			final ISO8583Message cbsresponse = EncoderDecoder.send(CBS_IP, CBS_PORT, cbsrequest, CBS_FORMAT, config.issuerTimeout * 1000);
			if(cbsresponse != null) {
				request.put(54, cbsresponse.get(54));
				request.put(38, cbsresponse.get(38));
				request.put(39, cbsresponse.get(39));
				return request;
			}
		} catch (Exception e) {logger.error(e);}
		request.put(39, ResponseCode.ISSUER_INOPERATIVE);
		return request;
	}

	@Override
	public final ISO8583Message reversal(final ISO8583Message request, final Logger logger) {
		try {
			final ISO8583Message cbsrequest  = request.copy(Set.of(0, 2, 3, 4, 11, 12, 13, 19, 32, 37, 38, 41, 49, 54, 102));
			final ISO8583Message cbsresponse = EncoderDecoder.send(CBS_IP, CBS_PORT, cbsrequest, CBS_FORMAT, config.issuerTimeout * 1000);
			if(cbsresponse != null) {
				request.put(38, cbsresponse.get(38));
				request.put(39, cbsresponse.get(39));
				return request;
			}
		} catch (Exception e) {logger.error(e);}
		request.put(39, ResponseCode.ISSUER_INOPERATIVE);
		return request;
	}
	
	/**
	 	-----------------------------------------------------------------------------------------------------------------------
	|000 : '0200'                                              |002 : '6077990020000011'                                  |
	|003 : '000000'                                            |004 : '000000060000'                                      |
	|007 : '0529080731'                                        |011 : '000001'                                            |
	|012 : '133731'                                            |013 : '0529'                                              |
	|019 : '356'                                               |032 : '720001'                                            |
	|037 : '814913000001'                                      |041 : 'TEST1234'                                          |
	|049 : '356'                                               |102 : '0002SB    00079250'                                |
	-----------------------------------------------------------------------------------------------------------------------
	-----------------------------------------------------------------------------------------------------------------------
	|000 : '0210'                                              |002 : '6077990020000011'                                  |
	|003 : '000000'                                            |004 : '000000060000'                                      |
	|007 : '0529080731'                                        |011 : '000001'                                            |
	|012 : '133731'                                            |013 : '0529'                                              |
	|019 : '356'                                               |032 : '720001'                                            |
	|037 : '814913000001'                                      |038 : '002917'                                            |
	|039 : '00'                                                |041 : 'TEST1234        '                                  |
	|049 : '356'                                               |054 : '1001356C000094973760 1002356C000094973760'         |
	|102 : '0002SB    00079250'                                |                                                          |
	-----------------------------------------------------------------------------------------------------------------------
	 */

}

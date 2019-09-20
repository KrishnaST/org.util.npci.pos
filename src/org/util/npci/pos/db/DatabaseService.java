package org.util.npci.pos.db;

import org.util.datautil.Pair;
import org.util.iso8583.ISO8583Message;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.CoreDatabaseService;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.model.Account;
import org.util.npci.pos.model.Card;
import org.util.npci.pos.model.Keys;

public abstract class DatabaseService extends CoreDatabaseService {

	public final POSDispatcher dispatcher;

	public DatabaseService(final POSDispatcher dispatcher) {
		super(dispatcher.config);
		this.dispatcher = dispatcher;
	}

	public abstract String getName();
	
	public abstract boolean isBankCard(final String pan, final Logger logger);
	
	public abstract boolean isEcommerceSuccess(final String tran_id, final Logger logger);
	
	public abstract Card getCard(final String pan, final Logger logger);
	
	public abstract Account getAccount(final String pan, final Logger logger);
	
	public abstract Keys getKeys(final String pan, final Logger logger);
	
	public abstract Pair<String, Integer> getCBSAddress(final String bin, final Logger logger);
	
	public abstract boolean checkAndUpdatePOSLimit(final String pan, final double amount, final Logger logger);
	
	public abstract boolean reversePOSLimit(final String pan, final double amount, final Logger logger);

	public abstract boolean updateBadPin(final String pan, final Logger logger);
	
	public abstract boolean clearBadPin(final String pan, final Logger logger);
	
	public abstract boolean registerPOSResponse(final long id, final ISO8583Message response, final Logger logger);
	
	public abstract boolean registerLegacyRequest(final ISO8583Message request, final Logger logger);

	public abstract boolean registerLegacyResponse(final ISO8583Message request, final Logger logger);

	public abstract long registerPOSRequest(final ISO8583Message request, final String type, final Logger logger);
}

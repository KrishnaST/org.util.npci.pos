package org.util.npci.pos.db;

import org.util.nanolog.Logger;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.coreconnect.CoreDatabaseService;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.model.Account;
import org.util.npci.pos.model.Card;
import org.util.npci.pos.model.Keys;

public abstract class DatabaseService extends CoreDatabaseService {

	public final POSDispatcher dispatcher;
	public final CoreConfig     config;

	public DatabaseService(final CoreConfig config, final POSDispatcher dispatcher) {
		this.config     = config;
		this.dispatcher = dispatcher;
	}

	public abstract String getName();
	
	public abstract Card getCard(final String card, final Logger logger);
	
	public abstract Account getAccount(final String card, final Logger logger);
	
	public abstract Keys getKeys(final String card, final Logger logger);
	
	public abstract boolean checkAndUpdatePOSLimit(final String card, final double amount, final Logger logger);
	
	public abstract boolean reversePOSLimit(final String card, final double amount, final Logger logger);

}

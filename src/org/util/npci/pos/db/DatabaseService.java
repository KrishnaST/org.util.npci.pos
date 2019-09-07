package org.util.npci.pos.db;

import javax.sql.DataSource;

import org.util.datautil.Pair;
import org.util.nanolog.Logger;
import org.util.npci.coreconnect.CoreDatabaseService;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.model.Account;
import org.util.npci.pos.model.Card;
import org.util.npci.pos.model.Keys;

public abstract class DatabaseService extends CoreDatabaseService {

	public final POSDispatcher dispatcher;
	public final DataSource dataSource;

	public DatabaseService(final POSDispatcher dispatcher) {
		super(dispatcher.config);
		this.dispatcher = dispatcher;
		this.dataSource = dispatcher.config.dataSource;
	}

	public abstract String getName();
	
	public abstract Card getCard(final String pan, final Logger logger);
	
	public abstract Account getAccount(final String pan, final Logger logger);
	
	public abstract Keys getKeys(final String pan, final Logger logger);
	
	public abstract Pair<String, Integer> getCBSAddress(final String bin, final Logger logger);
	
	public abstract boolean checkAndUpdatePOSLimit(final String pan, final double amount, final Logger logger);
	
	public abstract boolean reversePOSLimit(final String pan, final double amount, final Logger logger);

}

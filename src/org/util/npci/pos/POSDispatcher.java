package org.util.npci.pos;

import org.util.iso8583.ISO8583Message;
import org.util.npci.api.ConfigurationNotFoundException;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.coreconnect.issuer.LogonDispatcher;
import org.util.npci.pos.cbs.CoreBankingService;
import org.util.npci.pos.cbs.CoreBankingServiceBuilder;
import org.util.npci.pos.db.DatabaseService;
import org.util.npci.pos.db.DatabaseServiceBuilder;

public final class POSDispatcher extends LogonDispatcher {

	public final DatabaseService    databaseService;
	public final CoreBankingService coreBankingService;

	public POSDispatcher(CoreConfig config) throws ConfigurationNotFoundException {
		super(config);
		databaseService    = DatabaseServiceBuilder.getDatabaseService(config, this);
		config.corelogger.error(config.bankId+" : loaded database service :"+databaseService.getName());
		coreBankingService = CoreBankingServiceBuilder.getCoreBankingService(config, this);
		config.corelogger.error(config.bankId+" : loaded core banking service :"+coreBankingService.getName());
	}

	@Override
	public final boolean dispatch(ISO8583Message request) {
		boolean isDispatched = false;
		isDispatched = super.dispatch(request);
		if (!isDispatched) {
		}
		config.corelogger.info(config.bankId, request.get(37)+" dispatched : "+isDispatched);
		return isDispatched;
	}

}

package org.util.npci.pos;

import org.util.datautil.TLV;
import org.util.iso8583.ISO8583Message;
import org.util.iso8583.npci.constants.IMPSTransactionType;
import org.util.npci.api.ConfigurationNotFoundException;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.coreconnect.issuer.LogonDispatcher;
import org.util.npci.pos.cbs.CoreBankingService;
import org.util.npci.pos.cbs.CoreBankingServiceBuilder;
import org.util.npci.pos.db.DatabaseService;
import org.util.npci.pos.db.DatabaseServiceBuilder;
import org.util.npci.pos.transaction.ECommerceReversal;
import org.util.npci.pos.transaction.ECommerceTransaction;
import org.util.npci.pos.transaction.PurchaseReversal;
import org.util.npci.pos.transaction.PurchaseTransaction;

public final class POSDispatcher extends LogonDispatcher {

	public final DatabaseService    databaseService;
	public final CoreBankingService coreBankingService;

	public POSDispatcher(final CoreConfig config) throws ConfigurationNotFoundException {
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
			final String transactionType = TLV.parse(request.get(120)).get("001");
			if (IMPSTransactionType.P2A_TRANSACTION.equals(transactionType)) isDispatched = config.schedular.execute(new PurchaseTransaction(request, this));
			else if (IMPSTransactionType.P2A_VERIFICATION.equals(transactionType)) isDispatched = config.schedular.execute(new PurchaseReversal(request, this));
			else if (IMPSTransactionType.P2P_TRANSACTION.equals(transactionType)) isDispatched = config.schedular.execute(new ECommerceTransaction(request, this));
			else if (IMPSTransactionType.P2P_VERIFICATION.equals(transactionType)) isDispatched = config.schedular.execute(new ECommerceReversal(request, this));
		}
		config.corelogger.info(config.bankId, request.get(37)+" dispatched : "+isDispatched);
		return isDispatched;
	}

}

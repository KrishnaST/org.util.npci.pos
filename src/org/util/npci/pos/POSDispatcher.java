package org.util.npci.pos;

import org.util.iso8583.ISO8583Message;
import org.util.iso8583.npci.CurrencyCode;
import org.util.iso8583.npci.MTI;
import org.util.iso8583.npci.constants.PANEntryMode;
import org.util.iso8583.npci.constants.ProcessingCode;
import org.util.npci.api.ConfigurationNotFoundException;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.coreconnect.CorePropertyName;
import org.util.npci.coreconnect.issuer.LogonDispatcher;
import org.util.npci.pos.cbs.CoreBankingService;
import org.util.npci.pos.cbs.CoreBankingServiceBuilder;
import org.util.npci.pos.db.DatabaseService;
import org.util.npci.pos.db.DatabaseServiceBuilder;
import org.util.npci.pos.transaction.ECommerceTransaction;
import org.util.npci.pos.transaction.FullEMVCashAtPosTransaction;
import org.util.npci.pos.transaction.FullEMVPurchaseTransaction;
import org.util.npci.pos.transaction.MagstripeCashAtPosTransaction;
import org.util.npci.pos.transaction.MagstripePurchaseTransaction;
import org.util.npci.pos.transaction.QuickEMVCashAtPosTransaction;
import org.util.npci.pos.transaction.QuickEMVPurchaseTransaction;
import org.util.npci.pos.transaction.ReversalTransaction;

public final class POSDispatcher extends LogonDispatcher {

	public final boolean            isFullEMV = config.getBooleanSupressException(CorePropertyName.IS_FULL_EMV);
	public final DatabaseService    databaseService;
	public final CoreBankingService coreBankingService;

	public POSDispatcher(final CoreConfig config) throws ConfigurationNotFoundException {
		super(config);
		databaseService = DatabaseServiceBuilder.getDatabaseService(config, this);
		config.corelogger.error(config.bankId + " : loaded database service : " + databaseService.getName());
		coreBankingService = CoreBankingServiceBuilder.getCoreBankingService(config, this);
		config.corelogger.error(config.bankId + " : loaded core banking service :" + coreBankingService.getName());
	}

	@Override
	public final boolean dispatch(final ISO8583Message request) {
		boolean isDispatched = false;
		isDispatched = super.dispatch(request);
		if (!isDispatched) {
			final String mti       = request.get(0);
			final String pcode     = request.get(3).substring(0, 2);
			final String entrymode = request.get(22).substring(0, 2);
			if(!CurrencyCode.INR.equals(request.get(19)) || !CurrencyCode.INR.equals(request.get(49))) {
				config.corelogger.error("currency not supported : "+request.get(19)+" : "+request.get(49));
			}
			else if (MTI.TRANS_REQUEST.equals(mti)) {
				if (ProcessingCode.POS_PURCHASE.equals(pcode)) {
					if (PANEntryMode.ECOMMERCE.equals(entrymode)) 
						isDispatched = config.schedular.execute(new ECommerceTransaction(request, this));
					else if (PANEntryMode.FULL_MAGSTRIPE.equals(entrymode) || PANEntryMode.MAG_STRIPE_READ.equals(entrymode))
						isDispatched = config.schedular.execute(new MagstripePurchaseTransaction(request, this));
					else if (PANEntryMode.ICC.equals(entrymode) && isFullEMV)
						isDispatched = config.schedular.execute(new FullEMVPurchaseTransaction(request, this));
					else if (PANEntryMode.ICC.equals(entrymode) && !isFullEMV)
						isDispatched = config.schedular.execute(new QuickEMVPurchaseTransaction(request, this));
				}
				else if (ProcessingCode.CASH_ATM_POS.equals(pcode)) {
					if (PANEntryMode.FULL_MAGSTRIPE.equals(entrymode) || PANEntryMode.MAG_STRIPE_READ.equals(entrymode))
						isDispatched = config.schedular.execute(new MagstripeCashAtPosTransaction(request, this));
					else if (PANEntryMode.ICC.equals(entrymode) && isFullEMV)
						isDispatched = config.schedular.execute(new FullEMVCashAtPosTransaction(request, this));
					else if (PANEntryMode.ICC.equals(entrymode) && !isFullEMV)
						isDispatched = config.schedular.execute(new QuickEMVCashAtPosTransaction(request, this));
				}
			} else if (MTI.ISS_REVERSAL_REQUEST.equals(mti)) {
				isDispatched = config.schedular.execute(new ReversalTransaction(request, this));
			}
		}
		config.corelogger.info(config.bankId, request.get(37) + " dispatched : " + isDispatched);
		return isDispatched;
	}

}

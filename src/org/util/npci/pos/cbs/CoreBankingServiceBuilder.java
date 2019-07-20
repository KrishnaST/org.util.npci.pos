package org.util.npci.pos.cbs;

import java.util.List;
import java.util.ServiceLoader;

import org.util.npci.api.ConfigurationNotFoundException;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.pos.POSDispatcher;

public abstract class CoreBankingServiceBuilder {

	public abstract List<String> getCoreBankingServices();

	public abstract CoreBankingService build(final CoreConfig coreConfig, final POSDispatcher dispatcher) throws ConfigurationNotFoundException;

	public static final CoreBankingService getCoreBankingService(final CoreConfig config, final POSDispatcher dispatcher) throws ConfigurationNotFoundException {
		final ServiceLoader<CoreBankingServiceBuilder> serviceLoader = ServiceLoader.load(CoreBankingServiceBuilder.class, CoreBankingServiceBuilder.class.getClassLoader());
		for (CoreBankingServiceBuilder builder : serviceLoader) { if (builder.getCoreBankingServices().contains(config.coreBankingType)) return builder.build(config, dispatcher); }
		throw new ConfigurationNotFoundException("could not find core banking service with name : " + config.coreBankingType);
	}
}

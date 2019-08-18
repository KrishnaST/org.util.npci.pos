package org.util.npci.pos.cbs;

import java.util.concurrent.TimeUnit;

import org.util.npci.api.ConfigurationNotFoundException;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.coreconnect.util.RetroClientBuilder;
import org.util.npci.pos.POSDispatcher;

import retrofit2.Retrofit;

public final class Swift63CoreBankingService extends CoreBankingService {


	private final Retrofit retrofit;

	public Swift63CoreBankingService(final CoreConfig config, final POSDispatcher dispatcher) throws ConfigurationNotFoundException {
		super(config, dispatcher);
		System.out.println(PropertyName.CBS_IP+" : "+config.getString(PropertyName.CBS_IP));
		retrofit = RetroClientBuilder.newBuilder().baseURL(config.getString(PropertyName.CBS_IP))
				.withLogging(config.getStringSupressException(PropertyName.CBS_LOGGING_LEVEL))
				.readTimeout(config.issuerTimeout, TimeUnit.SECONDS).build();
		config.corelogger.info("retrofit initialized : ", retrofit.toString());
	}

	@Override
	public final String getName() {
		return "SWIFT63";
	}


}

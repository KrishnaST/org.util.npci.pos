package org.util.npci.pos;

import java.util.List;

import org.util.npci.api.ConfigurationNotFoundException;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.coreconnect.issuer.IssuerDispatcher;
import org.util.npci.coreconnect.issuer.IssuerDispatcherBuilder;

public final class POSDispatcherBuilder extends IssuerDispatcherBuilder {

	@Override
	public final List<String> getDispatcherTypes() {
		return List.of("POS");
	}

	@Override
	public final IssuerDispatcher build(CoreConfig config) throws ConfigurationNotFoundException {
		return new POSDispatcher(config);
	}

}

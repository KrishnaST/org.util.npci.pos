package org.util.npci.pos.db;

import java.util.List;

import org.util.npci.api.ConfigurationNotFoundException;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.pos.POSDispatcher;

public final class InternalDatabaseServiceBuilder extends DatabaseServiceBuilder {

	@Override
	public final List<String> getDatabaseServices() {
		return List.of("POS");
	}

	@Override
	public final DatabaseService build(final CoreConfig config, final POSDispatcher dispatcher) throws ConfigurationNotFoundException {
		return null;
	}

}

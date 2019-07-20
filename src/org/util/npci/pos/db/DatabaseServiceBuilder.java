package org.util.npci.pos.db;

import java.util.List;
import java.util.ServiceLoader;

import org.util.npci.api.ConfigurationNotFoundException;
import org.util.npci.coreconnect.CoreConfig;
import org.util.npci.pos.POSDispatcher;

public abstract class DatabaseServiceBuilder {

	public abstract List<String> getDatabaseServices();

	public abstract DatabaseService build(final CoreConfig config, final POSDispatcher dispatcher) throws ConfigurationNotFoundException;

	public static final DatabaseService getDatabaseService(final CoreConfig config, final POSDispatcher dispatcher) throws ConfigurationNotFoundException {
		final ServiceLoader<DatabaseServiceBuilder> serviceLoader = ServiceLoader.load(DatabaseServiceBuilder.class, DatabaseServiceBuilder.class.getClassLoader());
		for (DatabaseServiceBuilder builder : serviceLoader) { if (builder.getDatabaseServices().contains(config.databaseType)) return builder.build(config, dispatcher); }
		throw new ConfigurationNotFoundException("could not find database service with name : " + config.databaseType);
	}

}

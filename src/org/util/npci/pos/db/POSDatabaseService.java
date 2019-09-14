package org.util.npci.pos.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.util.datautil.Pair;
import org.util.datautil.ThalesKey;
import org.util.datautil.db.PseudoClosable;
import org.util.datautil.db.ResultSetBuilder;
import org.util.iso8583.ext.PANUtil;
import org.util.nanolog.Logger;
import org.util.npci.pos.POSDispatcher;
import org.util.npci.pos.model.Account;
import org.util.npci.pos.model.Card;
import org.util.npci.pos.model.Keys;

//@formatter:off
public final class POSDatabaseService extends DatabaseService {

	public POSDatabaseService(final POSDispatcher dispatcher) {
		super(dispatcher);
	}

	@Override
	public final String getName() {
		return "SWIFT20";
	}

	@Override
	public final Card getCard(final String pan, final Logger logger) {
		try(final Connection connection = config.dataSource.getConnection();
			final PreparedStatement ps = connection.prepareStatement("SELECT * FROM D390060 where CardId = ? and CONVERT(VARCHAR, DECRYPTBYKEY(encry_pan , 1, SUBSTRING(LTRIM(RTRIM(CardId)), DATALENGTH(LTRIM(RTRIM(CardId)))-3, 4))) = ?");
			final ResultSet rs = ResultSetBuilder.getResultSet(ps, PANUtil.getMaskedPAN(pan), pan)){
			if(rs.next()) {
				return new Card(0, rs.getInt("Status"), rs.getInt("BadPin"), rs.getDate("ExpDate"), rs.getString("PinOffset"));
			}
		} catch (Exception e) {logger.error(e);}
		return null;
	}

	private final String getAccountFormatFlag(final String bin, final Logger logger) {
		String accountFlag = "N";
		try(final Connection connection = config.dataSource.getConnection();
			final PreparedStatement ps = connection.prepareStatement("SELECT ActFormat FROM LOCALBINMASTER WHERE BIN =?");
			final ResultSet rs = ResultSetBuilder.getResultSet(ps, bin)){
			if(rs.next()) return rs.getString("ActFormat");
		} catch (Exception e) {logger.error(e);}
		return accountFlag;
	}
	
	
	@Override
	public final Account getAccount(final String pan, final Logger logger) {
		final String bin = PANUtil.getBIN(pan);
		final String accountFormat = getAccountFormatFlag(bin, logger);
		logger.info("AcctFlag : '"+accountFormat);
		final char accountFlag = "K".equalsIgnoreCase(accountFormat) ? 'N' : 'L';
		try(final Connection connection = config.dataSource.getConnection();
			final PreparedStatement ps = connection.prepareStatement("SELECT "+accountFlag+"BrCode, PrdAcctId FROM D390061 where CardId = ? and convert(varchar(16), decryptbykey(encry_pan , 1, rtrim(substring(CardId, 13,16)))) = ?");
			final ResultSet rs = ResultSetBuilder.getResultSet(ps, PANUtil.getMaskedPAN(pan), pan)) {
			if(rs.next()) {
				final Account account = new Account(rs.getInt(accountFlag+"BrCode"), rs.getString("PrdAcctId").trim());
				if("Y".equalsIgnoreCase(accountFormat)) account.account15 = account.account32;
				else account.account15 = String.format("%04d", account.branchCode) + account.account32.substring(0, 6) + account.account32.substring(account.account32.length()-16, account.account32.length()-8);
				return account;
			}
			else return null;
		} catch (Exception e) {logger.error(e);}
		return null;
	}

	@Override
	public final Keys getKeys(final String bin, final Logger logger) {
		try(final Connection connection = config.dataSource.getConnection();
			final PreparedStatement ps = connection.prepareStatement("SELECT * FROM KEYMASTER_POS WHERE ACQ_ID = ?");
			final ResultSet rs = ResultSetBuilder.getResultSet(ps, bin)){
			if(rs.next()) {
				final Keys keys = new Keys();
				keys.cvk1 = rs.getString("CVK");
				keys.cvk2 = rs.getString("CVK2");
				keys.pvk = ThalesKey.toThalesKey(rs.getString("PVK"));
				keys.zpk = ThalesKey.toThalesKey(rs.getString("ZPK"));
				return keys;
			}
		} catch (Exception e) {logger.error(e);}
		return null;
	}

	@Override
	public final Pair<String, Integer> getCBSAddress(final String bin, final Logger logger) {
		if(bin == null) return null;
		try(final Connection connection = config.dataSource.getConnection();
			final PreparedStatement ps = connection.prepareStatement("SELECT ISSPOSIP, ISSPOSPORT FROM LOCALBINMASTER WHERE BIN = ?");
			final AutoCloseable closeable = PseudoClosable.getClosable(ps, bin);
			final ResultSet rs = ps.executeQuery()){
			if(rs.next()) return new Pair<String, Integer>(rs.getString("ISSPOSIP"), rs.getInt("ISSPOSPORT"));
		} catch (Exception e) {logger.error(e);}
		return null;
	
	}

	@Override
	public final boolean checkAndUpdatePOSLimit(String pan, double amount, Logger logger) {
		return true;
	}

	@Override
	public final boolean reversePOSLimit(String pan, double amount, Logger logger) {
		return true;
	}

}


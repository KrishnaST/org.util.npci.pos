package org.util.npci.pos.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.util.datautil.Pair;
import org.util.datautil.Strings;
import org.util.datautil.ThalesKey;
import org.util.datautil.db.PseudoClosable;
import org.util.datautil.db.ResultSetBuilder;
import org.util.iso8583.ISO8583Message;
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
			final PreparedStatement ps = connection.prepareStatement("SELECT * FROM D390060 where CardId = ? and CONVERT(VARCHAR, DECRYPTBYKEY(encry_pan , 1, SUBSTRING(RTRIM(CardId), DATALENGTH(RTRIM(CardId))-3, 4))) = ?");
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
			final PreparedStatement ps = connection.prepareStatement("SELECT "+accountFlag+"BrCode, PrdAcctId FROM D390061 where CardId = ? and CONVERT(VARCHAR, DECRYPTBYKEY(encry_pan , 1, SUBSTRING(RTRIM(CardId), DATALENGTH(RTRIM(CardId))-3, 4))) = ?");
			final ResultSet rs = ResultSetBuilder.getResultSet(ps, PANUtil.getMaskedPAN(pan), pan)) {
			if(rs.next()) {
				final Account account = new Account(rs.getInt(accountFlag+"BrCode"), Strings.trim(rs.getString("PrdAcctId")));
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
				keys.cvk1 = Strings.trimSpecial(rs.getString("CVK"));
				keys.cvk2 = Strings.trimSpecial(rs.getString("CVK2"));
				keys.pvk = ThalesKey.toThalesKey(Strings.trimSpecial(rs.getString("PVK")));
				keys.zpk = ThalesKey.toThalesKey(Strings.trimSpecial(rs.getString("ZPK")));
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
			if(rs.next()) return new Pair<String, Integer>(Strings.trim(rs.getString("ISSPOSIP")), rs.getInt("ISSPOSPORT"));
		} catch (Exception e) {logger.error(e);}
		return null;
	
	}

	@Override
	public final boolean checkAndUpdatePOSLimit(final String pan, final double amount, final Logger logger) {
		final String query = "UPDATE D390060 SET "
				+ "POStrnDate = CASE WHEN (CAST(POStrnDate AS DATE) = CAST(GETDATE() AS DATE)) THEN POStrnDate ELSE GETDATE() END, "
				+ "TrnPOSAmount = CASE WHEN (CAST(POStrnDate AS DATE) = CAST(GETDATE() AS DATE))  "
				+ "THEN CASE WHEN (CardPOSLimit-(TrnPOSAmount + ?) >= 0) THEN (TrnPOSAmount+ ?) ELSE TrnPOSAmount END  "
				+ "ELSE CASE WHEN ((CardPOSLimit- ?) >= 0) THEN ? ELSE 0 END END "
				+ "OUTPUT CASE WHEN ((CAST(DELETED.POStrnDate AS DATE) <> CAST(INSERTED.POStrnDate AS DATE) AND INSERTED.TrnPOSAmount <> 0.0)  "
				+ "OR (CAST(DELETED.POStrnDate AS DATE) = CAST(INSERTED.POStrnDate AS DATE) AND DELETED.TrnPOSAmount <> INSERTED.TrnPOSAmount)) THEN 'TRUE' ELSE 'FALSE' END "
				+ "WHERE CardId = ? AND CONVERT(VARCHAR, DECRYPTBYKEY(encry_pan , 1, SUBSTRING(RTRIM(CardId), DATALENGTH(RTRIM(CardId))-3, 4))) = ?";
		if (pan == null || amount == 0) return false;
		try(final Connection con = config.dataSource.getConnection();
			final PreparedStatement ps = con.prepareStatement(query)) {
			ps.setDouble(1, amount);
			ps.setDouble(2, amount);
			ps.setDouble(3, amount);
			ps.setDouble(4, amount);
			ps.setString(5, PANUtil.getMaskedPAN(pan));
			ps.setString(6, pan);
			try (final ResultSet rs = ps.executeQuery()) {
				if (rs.next() && "TRUE".equalsIgnoreCase(rs.getString(1))) return true;
			} catch (Exception e) {logger.info(e);}
		} catch (Exception e) {logger.info(e);}
		return false;
	}

	@Override
	public final boolean reversePOSLimit(String pan, double amount, Logger logger) {
		return true;
	}

	public final boolean updateBadPin(final String pan, final Logger logger){
		try(final Connection connection = config.dataSource.getConnection();
			final PreparedStatement ps = connection.prepareStatement("UPDATE D390060 SET BadPin = BadPin + 1,  Status = CASE WHEN (BadPin >= 2) THEN 2 ELSE Status END WHERE CardId = ? and CONVERT(VARCHAR, DECRYPTBYKEY(encry_pan , 1, SUBSTRING(LTRIM(RTRIM(CardId)), DATALENGTH(LTRIM(RTRIM(CardId)))-3, 4))) = ?")){
			ps.setString(1, PANUtil.getMaskedPAN(pan));
			ps.setString(2, pan);
			return ps.executeUpdate() > 0;
		} catch (Exception e) {logger.info(e);}
		return false;

	}
	
	public final boolean clearBadPin(final String pan, final Logger logger){
		try(final Connection connection = config.dataSource.getConnection();
			final PreparedStatement ps = connection.prepareStatement("UPDATE D390060 SET BadPin = 0 WHERE CardId = ? and CONVERT(VARCHAR, DECRYPTBYKEY(encry_pan , 1, SUBSTRING(RTRIM(CardId), DATALENGTH(RTRIM(CardId))-3, 4))) = ?")){
			ps.setString(1, PANUtil.getMaskedPAN(pan));
			ps.setString(2, pan);
			return ps.executeUpdate() > 0;
		} catch (Exception e) {logger.info(e);}
		return false;
	}
	
	@Override
	public final boolean registerResponse(final long id, final ISO8583Message response, final Logger logger) {
		if (isdisabled || id == 0 || Strings.isNullOrEmpty(txTableName)) return false;
		try(final Connection con = config.dataSource.getConnection();
			final PreparedStatement ps = con.prepareStatement("UPDATE " + txTableName + " SET R039 = ?, R038 = ?, F102 = ?, RXTIME = GETDATE() WHERE TXID = ?")) {
			ps.setString(1, response.get(39));
			ps.setString(2, response.get(38));
			ps.setString(3, response.get(102));
			ps.setLong(4, id);
			return ps.executeUpdate() > 0;
		} catch (final Exception e) {logger.error(e);}
		return false;
	}
}


package jdbc2rest.services;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jdbc2rest.MainProcessing;
import jdbc2rest.dao.Db;
import jdbc2rest.entities.Request;
import jdbc2rest.entities.Response;
import jdbc2rest.entities.configuration.User;

public class SqlExecutor {

	private static final Logger log = LoggerFactory.getLogger(SqlExecutor.class);

	public Response Executor(Request req) {
		Response res = new Response();

		try {
			log.info("new request...");
			log.info("Token....:" + req.getToken());
			log.info("Limit...:" + req.getLimit());
			log.info("Offset..:" + req.getOffset());
			log.info("SQL.....:" + req.getSql());

			if (req.getSql() == null) {
				log.warn("sql is null");
				res.setMessage("sql is null");
				return res;
			}

			if (req.getToken() == null) {
				log.warn("token is null");
				res.setMessage("token is null");
				return res;
			}

			// Estrazione sicura del comando SQL
			String sqlTrimmed = req.getSql().trim();
			int firstSpaceIndex = sqlTrimmed.indexOf(" ");
			String SqlClause = firstSpaceIndex > 0 ? 
					sqlTrimmed.substring(0, firstSpaceIndex).toUpperCase() : 
					sqlTrimmed.toUpperCase();

			boolean isAuthorized = false;
			List<User> users = MainProcessing.getJdbc2RestConfiguration().getUsers();
			for (User user : users) {
				if (user.getToken().compareTo(req.getToken()) == 0) {
					for (String auth : user.getAuth()) {
						if (auth.compareToIgnoreCase(SqlClause) == 0) {
							isAuthorized = true;
						}
					}
				}
			}

			if (!isAuthorized) {
				log.warn("request NOT authorized");
				res.setMessage("request not authorized");
				return res;
			}

			log.info("request authorized");

			try (Connection conn = Db.getInstance().getConnection();
					Statement stmt = conn.createStatement()) {
                
                // Determinare se è una query di tipo SELECT o altro (UPDATE, INSERT, DELETE)
                boolean isSelectQuery = SqlClause.equals("SELECT");
                
                if (isSelectQuery) {
                    // Esecuzione per query SELECT
                    try (ResultSet rs = stmt.executeQuery(req.getSql())) {
                        List<LinkedHashMap<String, Object>> recs = resultSetToList(rs, req.getOffset(), req.getLimit());
                        res.setRecords(recs);
                    }
                } else {
                    // Esecuzione per query non-SELECT (INSERT, UPDATE, DELETE)
                    int rowsAffected = stmt.executeUpdate(req.getSql());
                    LinkedHashMap<String, Object> resultMap = new LinkedHashMap<>();
                    resultMap.put("rowsAffected", rowsAffected);
                    
                    List<LinkedHashMap<String, Object>> resultList = new ArrayList<>();
                    resultList.add(resultMap);
                    res.setRecords(resultList);
                    res.setMessage(SqlClause + " completed. Affected rows: " + rowsAffected);
                }

			} catch (SQLException e) {
				System.err.println("SQL Error: " + e.getMessage());
				e.printStackTrace();
				throw new Exception("Error executing query: " + e.getMessage(), e);
			} catch (Exception e) {
				System.err.println("Generic Error: " + e.getMessage());
				e.printStackTrace();
				throw new Exception("Error during processing: " + e.getMessage(), e);
			}

			log.info("...request closed");

		} catch (Exception e) {
			log.error(e.getMessage());
			e.printStackTrace();
			res.setMessage("Error: " + e.getMessage());
		}

		return res;
	}

	private List<LinkedHashMap<String, Object>> resultSetToList(ResultSet rs, Integer offset, Integer limit)
			throws SQLException {

		int maxRecordReturned = MainProcessing.getJdbc2RestConfiguration().getDatasource().getMaxRecordReturned();

		if (offset == null) {
			offset = 0;
		}

		if (limit == null) {
			limit = maxRecordReturned;
		}

		if (limit > maxRecordReturned) {
			limit = maxRecordReturned;
		}

		ResultSetMetaData md = rs.getMetaData();
		int columns = md.getColumnCount();
		List<LinkedHashMap<String, Object>> rows = new ArrayList<LinkedHashMap<String, Object>>();
		int rowNbr = 0;
		while (rs.next()) {
			rowNbr++;
			if (rowNbr <= offset) {
				continue;
			}
			if (rows.size() > limit) {
				break;
			}

			LinkedHashMap<String, Object> row = new LinkedHashMap<String, Object>(columns);
			for (int i = 1; i <= columns; ++i) {
				String ColumnName = md.getColumnName(i);
				String KeyName = "";
				KeyName = ColumnName;
				row.put(KeyName, rs.getObject(i));
			}
			rows.add(row);
		}

		rs.close();
		rs = null;

		return rows;
	}
}
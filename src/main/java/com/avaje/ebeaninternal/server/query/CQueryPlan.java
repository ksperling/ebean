package com.avaje.ebeaninternal.server.query;

import com.avaje.ebean.bean.ObjectGraphNode;
import com.avaje.ebean.config.dbplatform.SqlLimitResponse;
import com.avaje.ebeaninternal.api.CQueryPlanKey;
import com.avaje.ebeaninternal.api.SpiEbeanServer;
import com.avaje.ebeaninternal.server.core.OrmQueryRequest;
import com.avaje.ebeaninternal.server.deploy.BeanProperty;
import com.avaje.ebeaninternal.server.query.CQueryPlanStats.Snapshot;
import com.avaje.ebeaninternal.server.type.DataBind;
import com.avaje.ebeaninternal.server.type.DataReader;
import com.avaje.ebeaninternal.server.core.timezone.DataTimeZone;
import com.avaje.ebeaninternal.server.type.RsetDataReader;
import com.avaje.ebeaninternal.server.util.Md5;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Represents a query for a given SQL statement.
 * <p>
 * This can be executed multiple times with different bind parameters.
 * </p>
 * <p>
 * That is, the sql including the where clause, order by clause etc must be
 * exactly the same to share the same query plan with the only difference being
 * bind values.
 * </p>
 * <p>
 * This is useful in that is common in OLTP type applications that the same
 * query will be executed quite a lot just with different bind values. With this
 * query plan we can bypass some of the query statement generation (for
 * performance) and collect statistics on the number and average execution
 * times. This is turn can be used to identify queries that could be looked at
 * for performance tuning.
 * </p>
 */
public class CQueryPlan {

  private static final Logger logger = LoggerFactory.getLogger(CQueryPlan.class);

  private final SpiEbeanServer server;

  private final boolean autoTuned;

  private final CQueryPlanKey planKey;

  private final boolean rawSql;

  private final boolean rowNumberIncluded;

  private final String sql;

  private final String logWhereSql;

  private final SqlTree sqlTree;

  /**
   * Encrypted properties required additional binding.
   */
  private final BeanProperty[] encryptedProps;

  private final CQueryPlanStats stats;

  private final Class<?> beanType;

  protected final DataTimeZone dataTimeZone;

  private final int asOfTableCount;

  /**
   * Key used to identify the query plan in audit logging.
   */
  private volatile String auditQueryHash;

  /**
   * Create a query plan based on a OrmQueryRequest.
   */
  CQueryPlan(OrmQueryRequest<?> request, SqlLimitResponse sqlRes, SqlTree sqlTree, boolean rawSql, String logWhereSql) {

    this.server = request.getServer();
    this.dataTimeZone = server.getDataTimeZone();
    this.beanType = request.getBeanDescriptor().getBeanType();
    this.planKey = request.getQueryPlanKey();
    this.autoTuned = request.getQuery().isAutoTuned();
    this.asOfTableCount = request.getQuery().getAsOfTableCount();
    this.sql = sqlRes.getSql();
    this.rowNumberIncluded = sqlRes.isIncludesRowNumberColumn();
    this.sqlTree = sqlTree;
    this.rawSql = rawSql;
    this.logWhereSql = logWhereSql;
    this.encryptedProps = sqlTree.getEncryptedProps();
    this.stats = new CQueryPlanStats(this, server.isCollectQueryOrigins());
  }

  /**
   * Create a query plan for a raw sql query.
   */
  CQueryPlan(OrmQueryRequest<?> request, String sql, SqlTree sqlTree,
                    boolean rawSql, boolean rowNumberIncluded, String logWhereSql) {

    this.server = request.getServer();
    this.dataTimeZone = server.getDataTimeZone();
    this.beanType = request.getBeanDescriptor().getBeanType();
    this.planKey = buildPlanKey(sql, rawSql, rowNumberIncluded, logWhereSql);
    this.autoTuned = false;
    this.asOfTableCount = 0;
    this.sql = sql;
    this.sqlTree = sqlTree;
    this.rawSql = rawSql;
    this.rowNumberIncluded = rowNumberIncluded;
    this.logWhereSql = logWhereSql;
    this.encryptedProps = sqlTree.getEncryptedProps();
    this.stats = new CQueryPlanStats(this, server.isCollectQueryOrigins());
  }


  private CQueryPlanKey buildPlanKey(String sql, boolean rawSql, boolean rowNumberIncluded, String logWhereSql) {

    return new RawSqlQueryPlanKey(sql, rawSql, rowNumberIncluded, logWhereSql);
  }

  public String toString() {
    return beanType + " hash:" + planKey;
  }

  public Class<?> getBeanType() {
    return beanType;
  }

  public DataReader createDataReader(ResultSet rset) {
    return new RsetDataReader(dataTimeZone, rset);
  }

  /**
   * Bind keys for encrypted properties if necessary returning the DataBind.
   */
  DataBind bindEncryptedProperties(PreparedStatement stmt, Connection conn) throws SQLException {
    DataBind dataBind = new DataBind(dataTimeZone, stmt, conn);
    if (encryptedProps != null) {
      for (BeanProperty encryptedProp : encryptedProps) {
        String key = encryptedProp.getEncryptKey().getStringValue();
        dataBind.setString(key);
      }
    }
    return dataBind;
  }

  int getAsOfTableCount() {
    return asOfTableCount;
  }

  boolean isAutoTuned() {
    return autoTuned;
  }

  CQueryPlanKey getPlanKey() {
    return planKey;
  }

  /**
   * Return a key used in audit logging to identify the query.
   */
  String getAuditQueryKey() {
    if (auditQueryHash == null) {
      // volatile object assignment (so happy for multithreaded access)
      auditQueryHash = calcAuditQueryKey();
    }
    return auditQueryHash;
  }

  private String calcAuditQueryKey() {
    // rawSql needs to include the MD5 hash of the sql
    return rawSql ? planKey.getPartialKey() + "_" + getSqlMd5Hash() : planKey.getPartialKey();
  }

  /**
   * Return the MD5 hash of the underlying sql.
   */
  private String getSqlMd5Hash() {
    try {
      return Md5.hash(sql);
    } catch (Exception e) {
      logger.error("Failed to MD5 hash the rawSql query", e);
      return "error";
    }
  }

  public String getSql() {
    return sql;
  }

  SqlTree getSqlTree() {
    return sqlTree;
  }

  public boolean isRawSql() {
    return rawSql;
  }

  boolean isRowNumberIncluded() {
    return rowNumberIncluded;
  }

  String getLogWhereSql() {
    return logWhereSql;
  }

  /**
   * Reset the query statistics.
   */
  public void resetStatistics() {
    stats.reset();
  }

  /**
   * Register an execution time against this query plan;
   */
  void executionTime(long loadedBeanCount, long timeMicros, ObjectGraphNode objectGraphNode) {

    stats.add(loadedBeanCount, timeMicros, objectGraphNode);
    if (objectGraphNode != null) {
      // collect stats based on objectGraphNode for lazy loading reporting
      server.collectQueryStats(objectGraphNode, loadedBeanCount, timeMicros);
    }
  }

  public Snapshot getSnapshot(boolean reset) {
    return stats.getSnapshot(reset);
  }

  /**
   * Return the current query statistics.
   */
  public CQueryPlanStats getQueryStats() {
    return stats;
  }

  /**
   * Return the time this query plan was last used.
   */
  public long getLastQueryTime() {
    return stats.getLastQueryTime();
  }

  BeanProperty getSingleProperty() {
    return sqlTree.getRootNode().getSingleProperty();
  }
}

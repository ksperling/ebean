package com.avaje.ebeaninternal.server.core;

import com.avaje.ebean.QueryIterator;
import com.avaje.ebean.Version;
import com.avaje.ebean.bean.BeanCollection;

import java.util.List;

/**
 * The Object Relational query execution API.
 */
public interface OrmQueryEngine {

  /**
   * Execute the 'find by id' query returning a single bean.
   */
  <T> T findId(OrmQueryRequest<T> request);

  /**
   * Execute the findList, findSet, findMap query returning an appropriate BeanCollection.
   */
  <T> BeanCollection<T> findMany(OrmQueryRequest<T> request);

  /**
   * Execute the findSingleAttributeList query.
   */
  <A> List<A> findSingleAttributeList(OrmQueryRequest<?> request);

  /**
   * Execute the findVersions query.
   */
  <T> List<Version<T>> findVersions(OrmQueryRequest<T> request);

  /**
   * Execute the query using a QueryIterator.
   */
  <T> QueryIterator<T> findIterate(OrmQueryRequest<T> request);

  /**
   * Execute the row count query.
   */
  <T> int findRowCount(OrmQueryRequest<T> request);

  /**
   * Execute the find id's query.
   */
  <A> List<A> findIds(OrmQueryRequest<?> request);

  /**
   * Execute the query as a delete statement.
   */
  <T> int delete(OrmQueryRequest<T> request);

  /**
   * Execute the query as a update statement.
   */
  <T> int update(OrmQueryRequest<T> request);
}

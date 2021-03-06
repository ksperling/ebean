package com.avaje.tests.history;

import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.SqlQuery;
import com.avaje.ebean.SqlRow;
import com.avaje.ebean.Version;
import com.avaje.tests.model.converstation.User;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestHistoryInsert extends BaseTestCase {

  private final Logger logger = LoggerFactory.getLogger(TestHistoryInsert.class);

  @Test
  public void test() throws InterruptedException {

    if (!isH2() && !isPostgres()) {
      return;
    }

    User user = new User();
    user.setName("Jim");
    user.setEmail("one@email.com");
    user.setPasswordHash("someHash");

    Ebean.save(user);
    logger.info("-- initial save");

    Timestamp afterInsert = new Timestamp(System.currentTimeMillis());

    List<SqlRow> history = fetchHistory(user);
    assertThat(history).isEmpty();

    List<Version<User>> versions = Ebean.find(User.class).setId(user.getId()).findVersions();
    assertThat(versions).hasSize(1);

    user.setName("Jim v2");
    user.setPasswordHash("anotherHash");
    Thread.sleep(50); // wait, to ensure that whenModified differs
    logger.info("-- update v2");
    Ebean.save(user);

    history = fetchHistory(user);
    assertThat(history).hasSize(1);
    assertThat(history.get(0).getString("name")).isEqualTo("Jim");

    versions = Ebean.find(User.class).setId(user.getId()).findVersions();
    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).getDiff()).containsKeys("name", "version", "whenModified");

    user.setName("Jim v3");
    user.setEmail("three@email.com");
    Thread.sleep(50); // otherwise the timestamp of "whenModified" may not change

    logger.info("-- update v3");
    Ebean.save(user);

    history = fetchHistory(user);
    assertThat(history).hasSize(2);
    assertThat(history.get(1).getString("name")).isEqualTo("Jim v2");
    assertThat(history.get(1).getString("email")).isEqualTo("one@email.com");

    versions = Ebean.find(User.class).setId(user.getId()).findVersions();
    assertThat(versions).hasSize(3);
    assertThat(versions.get(0).getDiff()).containsKeys("name", "email", "version", "whenModified");

    logger.info("-- delete");
    Ebean.delete(user);

    User earlyVersion = Ebean.find(User.class).setId(user.getId()).asOf(afterInsert).findUnique();
    assertThat(earlyVersion.getName()).isEqualTo("Jim");
    assertThat(earlyVersion.getEmail()).isEqualTo("one@email.com");

    Ebean.find(User.class).setId(user.getId()).asOf(afterInsert).findUnique();

    logger.info("-- last fetchHistory");

    history = fetchHistory(user);
    assertThat(history).hasSize(3);
    assertThat(history.get(2).getString("name")).isEqualTo("Jim v3");
    assertThat(history.get(2).getString("email")).isEqualTo("three@email.com");

    versions = Ebean.find(User.class).setId(user.getId()).findVersions();
    assertThat(versions).hasSize(3);
  }

  /**
   * Use SqlQuery to query the history table directly.
   */
  private List<SqlRow> fetchHistory(User user) {
    SqlQuery sqlQuery = Ebean.createSqlQuery("select * from c_user_history where id = :id order by when_modified");
    sqlQuery.setParameter("id", user.getId());
    return sqlQuery.findList();
  }
}

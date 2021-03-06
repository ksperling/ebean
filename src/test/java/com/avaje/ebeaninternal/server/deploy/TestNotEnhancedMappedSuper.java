package com.avaje.ebeaninternal.server.deploy;


import com.avaje.ebean.BaseTestCase;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.bean.EntityBean;
import com.avaje.tests.model.mappedsuper.ASimpleBean;
import com.avaje.tests.model.mappedsuper.NotEnhancedMappedSuper;
import org.junit.Assert;
import org.junit.Test;

public class TestNotEnhancedMappedSuper extends BaseTestCase {

  @Test
  public void simpleBean_mappedSuperNotEnhanced_ok() {

    ASimpleBean bean = new ASimpleBean();
    bean.setName("junk");

    Ebean.save(bean);
    Assert.assertNotNull(bean.getId());
    
    bean.setName("junk mod");
    Ebean.save(bean);
    
    ASimpleBean fetched = Ebean.find(ASimpleBean.class, bean.getId());
    Assert.assertEquals("junk mod", fetched.getName());
    Assert.assertEquals(bean.getName(), fetched.getName());
    
    Ebean.delete(bean);
    ASimpleBean fetched2 = Ebean.find(ASimpleBean.class, bean.getId());
    Assert.assertNull(fetched2);
    
    NotEnhancedMappedSuper mappedSuper = new NotEnhancedMappedSuper();
    boolean enhanced = (mappedSuper instanceof EntityBean);
    Assert.assertFalse(enhanced);
     
  }
  
}

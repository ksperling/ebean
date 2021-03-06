package com.avaje.ebeaninternal.server.persist.dmlbind;

import java.util.List;

import com.avaje.ebeaninternal.server.deploy.BeanDescriptor;
import com.avaje.ebeaninternal.server.deploy.BeanPropertyAssocOne;
import com.avaje.ebeaninternal.server.persist.dml.DmlMode;

/**
 * A factory that builds Bindable for BeanPropertyAssocOne properties.
 */
public class FactoryAssocOnes {

  public FactoryAssocOnes() {
  }

  /**
   * Add foreign key columns from associated one beans.
   */
  public void create(List<Bindable> list, BeanDescriptor<?> desc, DmlMode mode) {

    BeanPropertyAssocOne<?>[] ones = desc.propertiesOneImported();

    for (BeanPropertyAssocOne<?> one : ones) {
      if (!one.isImportedPrimaryKey()) {
        switch (mode) {
          case INSERT:
            if (!one.isInsertable()) {
              continue;
            }
            break;
          case UPDATE:
            if (!one.isUpdateable()) {
              continue;
            }
            break;
        }
        list.add(new BindableAssocOne(one));
      }
    }
  }
}

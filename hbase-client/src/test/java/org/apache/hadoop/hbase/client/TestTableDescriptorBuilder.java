/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.client;

import org.apache.hadoop.hbase.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.exceptions.DeserializationException;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.apache.hadoop.hbase.util.BuilderStyleTest;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;

/**
 * Test setting values in the descriptor
 */
@Category({MiscTests.class, SmallTests.class})
public class TestTableDescriptorBuilder {
  private static final Log LOG = LogFactory.getLog(TestTableDescriptorBuilder.class);

  @Rule
  public TestName name = new TestName();

  @Test (expected=IOException.class)
  public void testAddCoprocessorTwice() throws IOException {
    String cpName = "a.b.c.d";
    TableDescriptor htd
      = TableDescriptorBuilder.newBuilder(TableName.META_TABLE_NAME)
            .addCoprocessor(cpName)
            .addCoprocessor(cpName)
            .build();
  }

  @Test
  public void testAddCoprocessorWithSpecStr() throws IOException {
    String cpName = "a.b.c.d";
    TableDescriptorBuilder builder
      = TableDescriptorBuilder.newBuilder(TableName.META_TABLE_NAME);

    try {
      builder.addCoprocessorWithSpec(cpName);
      fail();
    } catch (IllegalArgumentException iae) {
      // Expected as cpName is invalid
    }

    // Try minimal spec.
    try {
      builder.addCoprocessorWithSpec("file:///some/path" + "|" + cpName);
      fail();
    } catch (IllegalArgumentException iae) {
      // Expected to be invalid
    }

    // Try more spec.
    String spec = "hdfs:///foo.jar|com.foo.FooRegionObserver|1001|arg1=1,arg2=2";
    try {
      builder.addCoprocessorWithSpec(spec);
    } catch (IllegalArgumentException iae) {
      fail();
    }

    // Try double add of same coprocessor
    try {
      builder.addCoprocessorWithSpec(spec);
      fail();
    } catch (IOException ioe) {
      // Expect that the coprocessor already exists
    }
  }

  @Test
  public void testPb() throws DeserializationException, IOException {
    final int v = 123;
    TableDescriptor htd
      = TableDescriptorBuilder.newBuilder(TableName.META_TABLE_NAME)
          .setMaxFileSize(v)
          .setDurability(Durability.ASYNC_WAL)
          .setReadOnly(true)
          .setRegionReplication(2)
          .build();

    byte [] bytes = TableDescriptorBuilder.toByteArray(htd);
    TableDescriptor deserializedHtd = TableDescriptorBuilder.parseFrom(bytes);
    assertEquals(htd, deserializedHtd);
    assertEquals(v, deserializedHtd.getMaxFileSize());
    assertTrue(deserializedHtd.isReadOnly());
    assertEquals(Durability.ASYNC_WAL, deserializedHtd.getDurability());
    assertEquals(2, deserializedHtd.getRegionReplication());
  }

  /**
   * Test cps in the table description
   * @throws Exception
   */
  @Test
  public void testGetSetRemoveCP() throws Exception {
    // simple CP
    String className = "org.apache.hadoop.hbase.coprocessor.SimpleRegionObserver";
    TableDescriptor desc
      = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
         .addCoprocessor(className) // add and check that it is present
        .build();
    assertTrue(desc.hasCoprocessor(className));
    desc = TableDescriptorBuilder.newBuilder(desc)
         .removeCoprocessor(className) // remove it and check that it is gone
        .build();
    assertFalse(desc.hasCoprocessor(className));
  }

  /**
   * Test cps in the table description
   * @throws Exception
   */
  @Test
  public void testSetListRemoveCP() throws Exception {
    TableDescriptor desc
      = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName())).build();
    // Check that any coprocessor is present.
    assertTrue(desc.getCoprocessors().isEmpty());

    // simple CP
    String className1 = "org.apache.hadoop.hbase.coprocessor.SimpleRegionObserver";
    String className2 = "org.apache.hadoop.hbase.coprocessor.SampleRegionWALObserver";
    desc = TableDescriptorBuilder.newBuilder(desc)
            .addCoprocessor(className1) // Add the 1 coprocessor and check if present.
            .build();
    assertTrue(desc.getCoprocessors().size() == 1);
    assertTrue(desc.getCoprocessors().contains(className1));

    desc = TableDescriptorBuilder.newBuilder(desc)
            // Add the 2nd coprocessor and check if present.
            // remove it and check that it is gone
            .addCoprocessor(className2)
            .build();
    assertTrue(desc.getCoprocessors().size() == 2);
    assertTrue(desc.getCoprocessors().contains(className2));

    desc = TableDescriptorBuilder.newBuilder(desc)
            // Remove one and check
            .removeCoprocessor(className1)
            .build();
    assertTrue(desc.getCoprocessors().size() == 1);
    assertFalse(desc.getCoprocessors().contains(className1));
    assertTrue(desc.getCoprocessors().contains(className2));

    desc = TableDescriptorBuilder.newBuilder(desc)
            // Remove the last and check
            .removeCoprocessor(className2)
            .build();
    assertTrue(desc.getCoprocessors().isEmpty());
    assertFalse(desc.getCoprocessors().contains(className1));
    assertFalse(desc.getCoprocessors().contains(className2));
  }

  /**
   * Test that we add and remove strings from settings properly.
   * @throws Exception
   */
  @Test
  public void testRemoveString() throws Exception {
    byte[] key = Bytes.toBytes("Some");
    byte[] value = Bytes.toBytes("value");
    TableDescriptor desc
      = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
            .setValue(key, value)
            .build();
    assertTrue(Bytes.equals(value, desc.getValue(key)));
    desc = TableDescriptorBuilder.newBuilder(desc)
            .removeValue(key)
            .build();
    assertTrue(desc.getValue(key) == null);
  }

  String legalTableNames[] = { "foo", "with-dash_under.dot", "_under_start_ok",
      "with-dash.with_underscore", "02-01-2012.my_table_01-02", "xyz._mytable_", "9_9_0.table_02"
      , "dot1.dot2.table", "new.-mytable", "with-dash.with.dot", "legal..t2", "legal..legal.t2",
      "trailingdots..", "trailing.dots...", "ns:mytable", "ns:_mytable_", "ns:my_table_01-02"};
  String illegalTableNames[] = { ".dot_start_illegal", "-dash_start_illegal", "spaces not ok",
      "-dash-.start_illegal", "new.table with space", "01 .table", "ns:-illegaldash",
      "new:.illegaldot", "new:illegalcolon1:", "new:illegalcolon1:2"};

  @Test
  public void testLegalTableNames() {
    for (String tn : legalTableNames) {
      TableName.isLegalFullyQualifiedTableName(Bytes.toBytes(tn));
    }
  }

  @Test
  public void testIllegalTableNames() {
    for (String tn : illegalTableNames) {
      try {
        TableName.isLegalFullyQualifiedTableName(Bytes.toBytes(tn));
        fail("invalid tablename " + tn + " should have failed");
      } catch (Exception e) {
        // expected
      }
    }
  }

  @Test
  public void testLegalTableNamesRegex() {
    for (String tn : legalTableNames) {
      TableName tName = TableName.valueOf(tn);
      assertTrue("Testing: '" + tn + "'", Pattern.matches(TableName.VALID_USER_TABLE_REGEX,
          tName.getNameAsString()));
    }
  }

  @Test
  public void testIllegalTableNamesRegex() {
    for (String tn : illegalTableNames) {
      LOG.info("Testing: '" + tn + "'");
      assertFalse(Pattern.matches(TableName.VALID_USER_TABLE_REGEX, tn));
    }
  }

    /**
   * Test default value handling for maxFileSize
   */
  @Test
  public void testGetMaxFileSize() {
    TableDescriptor desc = TableDescriptorBuilder
            .newBuilder(TableName.valueOf(name.getMethodName())).build();
    assertEquals(-1, desc.getMaxFileSize());
    desc = TableDescriptorBuilder
            .newBuilder(TableName.valueOf(name.getMethodName()))
            .setMaxFileSize(1111L).build();
    assertEquals(1111L, desc.getMaxFileSize());
  }

  /**
   * Test default value handling for memStoreFlushSize
   */
  @Test
  public void testGetMemStoreFlushSize() {
    TableDescriptor desc = TableDescriptorBuilder
            .newBuilder(TableName.valueOf(name.getMethodName())).build();
    assertEquals(-1, desc.getMemStoreFlushSize());
    desc = TableDescriptorBuilder
            .newBuilder(TableName.valueOf(name.getMethodName()))
            .setMemStoreFlushSize(1111L).build();
    assertEquals(1111L, desc.getMemStoreFlushSize());
  }

  @Test
  public void testClassMethodsAreBuilderStyle() {
    BuilderStyleTest.assertClassesAreBuilderStyle(TableDescriptorBuilder.class);
  }

  @Test
  public void testModifyFamily() {
    byte[] familyName = Bytes.toBytes("cf");
    ColumnFamilyDescriptor hcd = ColumnFamilyDescriptorBuilder.newBuilder(familyName)
            .setBlocksize(1000)
            .setDFSReplication((short) 3)
            .build();
    TableDescriptor htd
      = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
              .addColumnFamily(hcd)
              .build();

    assertEquals(1000, htd.getColumnFamily(familyName).getBlocksize());
    assertEquals(3, htd.getColumnFamily(familyName).getDFSReplication());
    hcd = ColumnFamilyDescriptorBuilder.newBuilder(familyName)
            .setBlocksize(2000)
            .setDFSReplication((short) 1)
            .build();
    htd = TableDescriptorBuilder.newBuilder(htd)
              .modifyColumnFamily(hcd)
              .build();
    assertEquals(2000, htd.getColumnFamily(familyName).getBlocksize());
    assertEquals(1, htd.getColumnFamily(familyName).getDFSReplication());
  }

  @Test(expected=IllegalArgumentException.class)
  public void testModifyInexistentFamily() {
    byte[] familyName = Bytes.toBytes("cf");
    HColumnDescriptor hcd = new HColumnDescriptor(familyName);
    TableDescriptor htd = TableDescriptorBuilder
            .newBuilder(TableName.valueOf(name.getMethodName()))
            .modifyColumnFamily(hcd)
            .build();
  }

  @Test(expected=IllegalArgumentException.class)
  public void testAddDuplicateFamilies() {
    byte[] familyName = Bytes.toBytes("cf");
    ColumnFamilyDescriptor hcd = ColumnFamilyDescriptorBuilder.newBuilder(familyName)
            .setBlocksize(1000)
            .build();
    TableDescriptor htd = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
            .addColumnFamily(hcd)
            .build();
    assertEquals(1000, htd.getColumnFamily(familyName).getBlocksize());
    hcd = ColumnFamilyDescriptorBuilder.newBuilder(familyName)
            .setBlocksize(2000)
            .build();
    // add duplicate column
    TableDescriptorBuilder.newBuilder(htd).addColumnFamily(hcd).build();
  }

  @Test
  public void testPriority() {
    TableDescriptor htd = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
            .setPriority(42)
            .build();
    assertEquals(42, htd.getPriority());
  }

  @Test
  public void testSerialReplicationScope() {
    HColumnDescriptor hcdWithScope = new HColumnDescriptor(Bytes.toBytes("cf0"));
    hcdWithScope.setScope(HConstants.REPLICATION_SCOPE_SERIAL);
    HColumnDescriptor hcdWithoutScope = new HColumnDescriptor(Bytes.toBytes("cf1"));
    TableDescriptor htd = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
            .addColumnFamily(hcdWithoutScope)
            .build();
    assertFalse(htd.hasSerialReplicationScope());

    htd = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
            .addColumnFamily(hcdWithScope)
            .build();
    assertTrue(htd.hasSerialReplicationScope());

    htd = TableDescriptorBuilder.newBuilder(TableName.valueOf(name.getMethodName()))
            .addColumnFamily(hcdWithScope)
            .addColumnFamily(hcdWithoutScope)
            .build();
    assertTrue(htd.hasSerialReplicationScope());
  }
}
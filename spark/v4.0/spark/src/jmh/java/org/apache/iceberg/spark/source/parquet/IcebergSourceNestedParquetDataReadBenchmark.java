/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.spark.source.parquet;

import static org.apache.iceberg.TableProperties.SPLIT_OPEN_FILE_COST;
import static org.apache.spark.sql.functions.expr;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.struct;

import java.io.IOException;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.source.IcebergSourceNestedDataBenchmark;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.internal.SQLConf;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

/**
 * A benchmark that evaluates the performance of reading nested Parquet data using Iceberg and the
 * built-in file source in Spark.
 *
 * <p>To run this benchmark for spark-4.0: <code>
 *   ./gradlew -DsparkVersions=4.0 :iceberg-spark:iceberg-spark-4.0_2.13:jmh
 *       -PjmhIncludeRegex=IcebergSourceNestedParquetDataReadBenchmark
 *       -PjmhOutputPath=benchmark/iceberg-source-nested-parquet-data-read-benchmark-result.txt
 * </code>
 */
public class IcebergSourceNestedParquetDataReadBenchmark extends IcebergSourceNestedDataBenchmark {

  private static final int NUM_FILES = 10;
  private static final int NUM_ROWS = 1000000;

  @Setup
  public void setupBenchmark() {
    setupSpark();
    appendData();
  }

  @TearDown
  public void tearDownBenchmark() throws IOException {
    tearDownSpark();
    cleanupFiles();
  }

  @Benchmark
  @Threads(1)
  public void readIceberg() {
    Map<String, String> tableProperties = Maps.newHashMap();
    tableProperties.put(SPLIT_OPEN_FILE_COST, Integer.toString(128 * 1024 * 1024));
    withTableProperties(
        tableProperties,
        () -> {
          String tableLocation = table().location();
          Dataset<Row> df = spark().read().format("iceberg").load(tableLocation);
          materialize(df);
        });
  }

  @Benchmark
  @Threads(1)
  public void readFileSourceVectorized() {
    Map<String, String> conf = Maps.newHashMap();
    conf.put(SQLConf.PARQUET_VECTORIZED_READER_ENABLED().key(), "true");
    conf.put(SQLConf.FILES_OPEN_COST_IN_BYTES().key(), Integer.toString(128 * 1024 * 1024));
    withSQLConf(
        conf,
        () -> {
          Dataset<Row> df = spark().read().parquet(dataLocation());
          materialize(df);
        });
  }

  @Benchmark
  @Threads(1)
  public void readFileSourceNonVectorized() {
    Map<String, String> conf = Maps.newHashMap();
    conf.put(SQLConf.PARQUET_VECTORIZED_READER_ENABLED().key(), "false");
    conf.put(SQLConf.FILES_OPEN_COST_IN_BYTES().key(), Integer.toString(128 * 1024 * 1024));
    withSQLConf(
        conf,
        () -> {
          Dataset<Row> df = spark().read().parquet(dataLocation());
          materialize(df);
        });
  }

  @Benchmark
  @Threads(1)
  public void readWithProjectionIceberg() {
    Map<String, String> tableProperties = Maps.newHashMap();
    tableProperties.put(SPLIT_OPEN_FILE_COST, Integer.toString(128 * 1024 * 1024));
    withTableProperties(
        tableProperties,
        () -> {
          String tableLocation = table().location();
          Dataset<Row> df =
              spark().read().format("iceberg").load(tableLocation).selectExpr("nested.col3");
          materialize(df);
        });
  }

  @Benchmark
  @Threads(1)
  public void readWithProjectionFileSourceVectorized() {
    Map<String, String> conf = Maps.newHashMap();
    conf.put(SQLConf.PARQUET_VECTORIZED_READER_ENABLED().key(), "true");
    conf.put(SQLConf.FILES_OPEN_COST_IN_BYTES().key(), Integer.toString(128 * 1024 * 1024));
    conf.put(SQLConf.NESTED_SCHEMA_PRUNING_ENABLED().key(), "true");
    withSQLConf(
        conf,
        () -> {
          Dataset<Row> df = spark().read().parquet(dataLocation()).selectExpr("nested.col3");
          materialize(df);
        });
  }

  @Benchmark
  @Threads(1)
  public void readWithProjectionFileSourceNonVectorized() {
    Map<String, String> conf = Maps.newHashMap();
    conf.put(SQLConf.PARQUET_VECTORIZED_READER_ENABLED().key(), "false");
    conf.put(SQLConf.FILES_OPEN_COST_IN_BYTES().key(), Integer.toString(128 * 1024 * 1024));
    conf.put(SQLConf.NESTED_SCHEMA_PRUNING_ENABLED().key(), "true");
    withSQLConf(
        conf,
        () -> {
          Dataset<Row> df = spark().read().parquet(dataLocation()).selectExpr("nested.col3");
          materialize(df);
        });
  }

  private void appendData() {
    for (int fileNum = 0; fileNum < NUM_FILES; fileNum++) {
      Dataset<Row> df =
          spark()
              .range(NUM_ROWS)
              .withColumn(
                  "nested",
                  struct(
                      expr("CAST(id AS string) AS col1"),
                      expr("CAST(id AS double) AS col2"),
                      lit(fileNum).cast("long").as("col3")));
      appendAsFile(df);
    }
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kyuubi.service

import org.apache.hive.service.rpc.thrift._

import org.apache.kyuubi.operation.{OperationHandle, OperationStatus}
import org.apache.kyuubi.operation.FetchOrientation.FetchOrientation
import org.apache.kyuubi.session.SessionHandle

trait BackendService {

  def openSession(
      protocol: TProtocolVersion,
      user: String,
      password: String,
      ipAddr: String,
      configs: java.util.Map[String, String]): SessionHandle
  def closeSession(sessionHandle: SessionHandle): Unit

  def getInfo(sessionHandle: SessionHandle, infoType: TGetInfoType): TGetInfoValue

  def executeStatement(
      sessionHandle: SessionHandle,
      statement: String,
      confOverlay: java.util.Map[String, String]): OperationHandle
  def executeStatement(
      sessionHandle: SessionHandle,
      statement: String,
      confOverlay: java.util.Map[String, String],
      queryTimeout: Long): OperationHandle
  def executeStatementAsync(
      sessionHandle: SessionHandle,
      statement: String,
      confOverlay: java.util.Map[String, String]): OperationHandle
  def executeStatementAsync(
      sessionHandle: SessionHandle,
      statement: String,
      confOverlay: java.util.Map[String, String],
      queryTimeout: Long): OperationHandle

  def getTypeInfo(sessionHandle: SessionHandle): OperationHandle
  def getCatalogs(sessionHandle: SessionHandle): OperationHandle
  def getSchemas(
      sessionHandle: SessionHandle,
      catalogName: String,
      schemaName: String): OperationHandle
  def getTables(
      sessionHandle: SessionHandle,
      catalogName: String,
      schemaName: String,
      tableName: String,
      tableTypes: java.util.List[String]): OperationHandle
  def getTableTypes(sessionHandle: SessionHandle): OperationHandle
  def getColumns(
      sessionHandle: SessionHandle,
      catalogName: String,
      schemaName: String,
      tableName: String,
      columnName: String): OperationHandle
  def getFunctions(
      sessionHandle: SessionHandle,
      catalogName: String,
      schemaName: String,
      functionName: String): OperationHandle
  def getPrimaryKeys(
      sessionHandle: SessionHandle,
      catalogName: String,
      schemaName: String,
      tableName: String): OperationHandle
  def getCrossReference(
      sessionHandle: SessionHandle,
      primaryCatalog: String,
      primarySchema: String,
      primaryTable: String,
      foreignCatalog: String,
      foreignSchema: String,
      foreignTable: String): OperationHandle

  def getOperationStatus(operationHandle: OperationHandle): OperationStatus
  def cancelOperation(operationHandle: OperationHandle): Unit
  def closeOperation(operationHandle: OperationHandle): Unit
  def getResultSetMetadata(operationHandle: OperationHandle): TTableSchema
  def fetchResults(
      operationHandle: OperationHandle,
      orientation: FetchOrientation,
      maxRows: Long,
      fetchLog: Boolean): TRowSet
}

object BackendService {
  final val SERVER_VERSION = TProtocolVersion.HIVE_CLI_SERVICE_PROTOCOL_V10
}

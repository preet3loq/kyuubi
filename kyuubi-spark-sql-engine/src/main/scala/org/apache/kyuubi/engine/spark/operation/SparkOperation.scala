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

package org.apache.kyuubi.engine.spark.operation

import org.apache.commons.lang3.StringUtils
import org.apache.hive.service.rpc.thrift.{TRowSet, TTableSchema}
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.types.StructType

import org.apache.kyuubi.KyuubiSQLException
import org.apache.kyuubi.operation.{AbstractOperation, OperationState}
import org.apache.kyuubi.operation.FetchOrientation.FetchOrientation
import org.apache.kyuubi.operation.OperationState.OperationState
import org.apache.kyuubi.operation.OperationType.OperationType
import org.apache.kyuubi.schema.{RowSet, SchemaHelper}
import org.apache.kyuubi.session.Session

abstract class SparkOperation(spark: SparkSession, opType: OperationType, session: Session)
  extends AbstractOperation(opType, session) {

  protected var iter: Iterator[Row] = _

  protected def statement: String = opType.toString

  protected def resultSchema: StructType

  protected def isTerminalState(operationState: OperationState): Boolean = {
    OperationState.isTerminal(operationState)
  }

  protected def cleanup(targetState: OperationState): Unit = synchronized {
    if (!isTerminalState(state)) {
      setState(targetState)
      spark.sparkContext.cancelJobGroup(statementId)
    }
  }

  private def convertPattern(pattern: String, datanucleusFormat: Boolean): String = {
    val wStr = if (datanucleusFormat) "*" else ".*"
    pattern
      .replaceAll("([^\\\\])%", "$1" + wStr)
      .replaceAll("\\\\%", "%")
      .replaceAll("^%", wStr)
      .replaceAll("([^\\\\])_", "$1.")
      .replaceAll("\\\\_", "_")
      .replaceAll("^_", ".")
  }

  /**
   * Convert wildcards and escape sequence of schema pattern from JDBC format to datanucleous/regex
   * The schema pattern treats empty string also as wildcard
   */
  protected def convertSchemaPattern(pattern: String): String = {
    if (StringUtils.isEmpty(pattern)) {
      convertPattern("%", datanucleusFormat = true)
    } else {
      convertPattern(pattern, datanucleusFormat = true)
    }
  }

  /**
   * Convert wildcards and escape sequence from JDBC format to datanucleous/regex
   */
  protected def convertIdentifierPattern(pattern: String, datanucleusFormat: Boolean): String = {
    if (pattern == null) {
      convertPattern("%", datanucleusFormat = true)
    } else {
      convertPattern(pattern, datanucleusFormat)
    }
  }

  protected def onError(cancel: Boolean = false): PartialFunction[Throwable, Unit] = {
    case e: Exception =>
      if (cancel) spark.sparkContext.cancelJobGroup(statementId)
      state.synchronized {
        if (isTerminalState(state)) {
          warn(s"Ignore exception in terminal state with $statementId: $e")
        } else {
          setState(OperationState.ERROR)
          e match {
            case k: KyuubiSQLException => throw k
            case _ => throw KyuubiSQLException(s"Error operating $opType: ${e.getMessage}", e)
          }
        }
      }
  }

  override def setState(newState: OperationState): Unit = {
    info(s"Processing ${session.user}'s query with queryId: $statementId, currentState:" +
      s" ${state.name}, newState: ${newState.name}, statement: $statement")
    super.setState(newState)
  }

  override protected def beforeRun(): Unit = {
    setHasResultSet(true)
    setState(OperationState.RUNNING)
  }

  override protected def afterRun(): Unit = {
    state.synchronized {
      if (!isTerminalState(state)) {
        setState(OperationState.FINISHED)
      }
    }
  }

  override def cancel(): Unit = {
    cleanup(OperationState.CANCELED)
  }

  override def close(): Unit = {
    cleanup(OperationState.CLOSED)
  }

  override def getResultSetSchema: TTableSchema = SchemaHelper.toTTableSchema(resultSchema)

  override def getNextRowSet(order: FetchOrientation, rowSetSize: Int): TRowSet = {
    validateDefaultFetchOrientation(order)
    assertState(OperationState.FINISHED)
    setHasResultSet(true)
    val taken = iter.take(rowSetSize)
    RowSet.toTRowSet(taken.toList, resultSchema, getProtocolVersion)
  }

  override def shouldRunAsync: Boolean = false
}

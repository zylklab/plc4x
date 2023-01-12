/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.plc4x.nifi.service.api;

import java.util.Map;

import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
// import org.apache.plc4x.java.api.PlcDriver;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;

public interface Plc4xControllerService extends ControllerService{
    /**
     * @return a PlcConnection
     * @throws ProcessException if an error occurs while getting a connection
     */
    PlcConnection getConnection(FlowFile flowFile) throws ProcessException;

    /**
     * @return timeout time in millisecods
     * @throws ProcessException if an error occurs while getting the timeout time
     */
    Long getTimeout() throws ProcessException;

    /**
     * @return PlcReadResponse time in millisecods
     * @throws PlcConnectionException if an error occurs while creating a connection or reading data
     */
    PlcReadResponse getReadResponse(FlowFile flowfile, Map<String, String> tags) throws PlcConnectionException;

    /**
     * @return timeout time in millisecods
     * @throws PlcConnectionException if an error occurs while creating a connectino or writing data
     */
    PlcWriteResponse getWriteResponse(FlowFile flowfile, Map<String, String> tags, Map<String, Object> values) throws PlcConnectionException;
}
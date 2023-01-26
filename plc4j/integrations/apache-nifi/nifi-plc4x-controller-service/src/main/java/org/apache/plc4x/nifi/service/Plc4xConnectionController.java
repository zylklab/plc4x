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

package org.apache.plc4x.nifi.service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.nifi.annotation.behavior.SupportsSensitiveDynamicProperties;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.messages.PlcReadRequest;

@Tags({ "plc-connector", "plc-read", "plc-write"})
@CapabilityDescription("Manages connections to PLCs, allows reading and writing data into them. Connection string can be set constant or use Expression Language"
        + "to access incoming flowfile attributes, by defaults uses 'plc4x.connection_string' attribute.")
@SupportsSensitiveDynamicProperties()
public class Plc4xConnectionController extends AbstractPlc4xConnectionController{

    @Override
    public PlcReadResponse getReadResponse(FlowFile flowfile, Map<String, String> tags) throws PlcConnectionException{

        final PlcConnection connection = getConnection(flowfile); 
        if (!connection.isConnected())
            connection.connect();
        if (!connection.getMetadata().canRead())
            throw new PlcConnectionException("Reading not supported by connection"); 

        PlcReadRequest.Builder builder = connection.readRequestBuilder();
        tags.entrySet().forEach(tag -> {
            String address = tag.getValue();
            if (address != null) {
                builder.addTagAddress(tag.getKey(), address);
            }
        });
        PlcReadRequest readRequest = builder.build();

        PlcReadResponse readResponse;
        try {
            readResponse = readRequest.execute().get(getTimeout(), TimeUnit.MILLISECONDS);
            connection.close();
        } catch (final Exception e) {
            throw new PlcConnectionException(e);
        }
        return readResponse;
    }

    @Override
    public PlcWriteResponse getWriteResponse(FlowFile flowfile, Map<String, String> tags, Map<String, Object> values) throws PlcConnectionException{

        final PlcConnection connection = getConnection(flowfile); 
        if (!connection.isConnected())
            connection.connect();
        if (!connection.getMetadata().canWrite())
            throw new PlcConnectionException("Writing not supported by connection"); 
        
        PlcWriteRequest.Builder builder = connection.writeRequestBuilder();
        tags.entrySet().forEach(tag -> {
            String address = tag.getValue();
            if (address != null) {
                builder.addTagAddress(tag.getKey(), address, values.get(tag.getKey()));
            }
        });
        PlcWriteRequest writeRequest = builder.build();

        PlcWriteResponse writeResponse;
        try {
            writeResponse = writeRequest.execute().get(getTimeout(), TimeUnit.MILLISECONDS);
            connection.close();
        } catch (final Exception e) {
            throw new PlcConnectionException(e);
        }
        return writeResponse;
    }
}
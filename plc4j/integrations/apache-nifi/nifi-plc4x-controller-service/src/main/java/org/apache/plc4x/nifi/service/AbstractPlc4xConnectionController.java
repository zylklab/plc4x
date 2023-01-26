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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.ConfigVerificationResult;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.controller.VerifiableControllerService;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.reporting.InitializationException;

import static org.apache.nifi.components.ConfigVerificationResult.Outcome.FAILED;
import static org.apache.nifi.components.ConfigVerificationResult.Outcome.SUCCESSFUL;

import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.authentication.PlcAuthentication;
import org.apache.plc4x.java.api.authentication.PlcUsernamePasswordAuthentication;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.utils.cache.CachedPlcConnectionManager;
import org.apache.plc4x.nifi.service.api.Plc4xControllerService;

import static org.apache.plc4x.nifi.service.util.ConnectionControllerProperties.*;


public abstract class AbstractPlc4xConnectionController extends AbstractControllerService
        implements VerifiableControllerService, Plc4xControllerService {

    protected volatile PlcConnectionManager connectionManager = null;
    protected volatile long timeout;
    protected volatile ConfigurationContext configurationContext;

    protected static final String PLC_CONNECTION_STRING_REGEX = "^[\\w-]+:(?:tcp|udp|raw|serial|socketcan|pcap|)//[\\w.:]*\\?(?:[\\S]*)";

    // Connection String access strategies
    private final List<AllowableValue> connectionStringAccessStrategy = Collections.unmodifiableList(Arrays.asList(
            CONSTANT_STRING_CONNECTION,
            ATTRIBUTE_STRING_CONNECTION));

    private AllowableValue getDefaultConnectionStringAccessStrategy() {
        return CONSTANT_STRING_CONNECTION;
    }

    protected List<AllowableValue> getConnectionStringAccessStrategyValues() {
        return connectionStringAccessStrategy;
    }

    private PropertyDescriptor buildStrategyProperty(AllowableValue[] values) {
        return new PropertyDescriptor.Builder()
                .fromPropertyDescriptor(CONNECTION_STRING_STRATEGY)
                .allowableValues(values)
                .defaultValue(getDefaultConnectionStringAccessStrategy().getValue())
                .build();
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        final List<PropertyDescriptor> properties = new ArrayList<>(2);

        final AllowableValue[] strategies = getConnectionStringAccessStrategyValues().toArray(new AllowableValue[0]);
        properties.add(buildStrategyProperty(strategies));

        properties.add(CONSTANT_STRING_CONNECTION_PROPERTY);
        properties.add(ATTRIBUTE_STRING_CONNECTION_PROPERTY);
        properties.add(PLC_FUTURE_TIMEOUT_MILISECONDS);
        properties.add(PLC_AUTHENTICATION_USERNAME_CONSTANT);
        properties.add(PLC_AUTHENTICATION_USERNAME_ATTRIBUTE);
        properties.add(PLC_AUTHENTICATION_PASSWORD_CONSTANT);
        properties.add(PLC_AUTHENTICATION_PASSWORD_ATTRIBUTE);

        return properties;
    }

    @Override
    public List<ConfigVerificationResult> verify(ConfigurationContext context, ComponentLog verificationLogger,
            Map<String, String> variables) {

        configurePlcConnectionManager();
        List<ConfigVerificationResult> results = new ArrayList<>();

        try {
            String plcConnectionString = null;
            String value = configurationContext.getProperty(CONNECTION_STRING_STRATEGY).getValue();
            if (CONSTANT_STRING_CONNECTION.getValue().equalsIgnoreCase(value)) {
                plcConnectionString = configurationContext.getProperty(CONSTANT_STRING_CONNECTION_PROPERTY).getValue();
                if (!plcConnectionString.matches(PLC_CONNECTION_STRING_REGEX))
                    throw new Exception(
                            "Plc connection string does not match '{driver code}:{transport code}://{transport config}?{options}'");
            }

            // Else assume correct
            results.add(new ConfigVerificationResult.Builder()
                    .verificationStepName("create connection to PLC")
                    .outcome(SUCCESSFUL)
                    .explanation("Successfully created connection to PLC")
                    .build());

        } catch (final Exception e) {
            verificationLogger.error("Failed to create connection to PLC", e);
            results.add(new ConfigVerificationResult.Builder()
                    .verificationStepName("create connection to PLC")
                    .outcome(FAILED)
                    .explanation("Failed to create connection to PLC: " + e.getMessage())
                    .build());
        }
        return results;
    }

    private void configurePlcConnectionManager() {
        if (connectionManager == null)
            connectionManager = CachedPlcConnectionManager.getBuilder().build();
    }

    @OnEnabled
    public void onConfigured(final ConfigurationContext context) throws InitializationException {
        configurePlcConnectionManager();

        this.timeout = Long.valueOf(context.getProperty(PLC_FUTURE_TIMEOUT_MILISECONDS).getValue());
        this.configurationContext = context;
    }

    @Override
    public PlcConnection getConnection(FlowFile flowFile) throws ProcessException {
        String plcConnectionString = null;
        PlcAuthentication plcAuthentication = null;
        String value = configurationContext.getProperty(CONNECTION_STRING_STRATEGY).getValue();
        if (CONSTANT_STRING_CONNECTION.getValue().equalsIgnoreCase(value)) {
            plcConnectionString = configurationContext.getProperty(CONSTANT_STRING_CONNECTION_PROPERTY).getValue();
            try {
                plcAuthentication = new PlcUsernamePasswordAuthentication(
                    configurationContext.getProperty(PLC_AUTHENTICATION_USERNAME_CONSTANT).getValue(),
                    configurationContext.getProperty(PLC_AUTHENTICATION_PASSWORD_CONSTANT).getValue());
            } catch (Exception e){}

        } else if (ATTRIBUTE_STRING_CONNECTION.getValue().equalsIgnoreCase(value)) {
            plcConnectionString = configurationContext.getProperty(ATTRIBUTE_STRING_CONNECTION_PROPERTY)
                    .evaluateAttributeExpressions(flowFile).getValue();
            try {
                plcAuthentication = new PlcUsernamePasswordAuthentication(
                    configurationContext.getProperty(PLC_AUTHENTICATION_USERNAME_ATTRIBUTE).evaluateAttributeExpressions(flowFile).getValue(),
                    configurationContext.getProperty(PLC_AUTHENTICATION_PASSWORD_ATTRIBUTE).evaluateAttributeExpressions(flowFile).getValue());
            } catch (Exception e){}
        }

        try {
            try {
                return connectionManager.getConnection(plcConnectionString, plcAuthentication);
            } catch (PlcConnectionException e) {}
            return connectionManager.getConnection(plcConnectionString);
        } catch (PlcConnectionException e) {
            throw new ProcessException(e);
        }
    }

    @Override
    public Long getTimeout() throws ProcessException {
        return timeout;
    }
}
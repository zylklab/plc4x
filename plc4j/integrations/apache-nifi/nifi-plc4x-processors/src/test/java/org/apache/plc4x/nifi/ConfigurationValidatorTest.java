package org.apache.plc4x.nifi;

import java.util.HashMap;
import java.util.Map;

import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigurationValidatorTest extends BasePlc4xProcessor {

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {

    }

    private TestRunner testRunner;
    private static final int NUMBER_OF_CALLS = 1;


    @Test
    public void testConfiguration() throws JsonProcessingException {
        testRunner = TestRunners.newTestRunner(this);
        testRunner.setIncomingConnection(false);
        testRunner.setValidateExpressionUsage(true);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> options = new HashMap<>();
        options.put("username", "admin");
        options.put("password", "password");

        testRunner.setProperty(PLC_CONNECTION_STRING, "opcua:tcp://127.0.0.1:12686?discovery=true");
        testRunner.setProperty(PLC_DRIVER_CONFIGURATION, mapper.writeValueAsString(options));
        testRunner.setProperty(PLC_FUTURE_TIMEOUT_MILISECONDS, "1000");

        testRunner.addConnection(REL_SUCCESS);
        testRunner.addConnection(REL_FAILURE);

        testRunner.assertValid();
        testRunner.run(NUMBER_OF_CALLS);
    }


    @Test
    public void testInvalidConfiguration() throws JsonProcessingException {
        testRunner = TestRunners.newTestRunner(this);
        testRunner.setIncomingConnection(false);
        testRunner.setValidateExpressionUsage(true);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> options = new HashMap<>();
        options.put("random parameter", "random value");

        testRunner.setProperty(PLC_CONNECTION_STRING, "opcua:tcp://127.0.0.1:12686");
        testRunner.setProperty(PLC_DRIVER_CONFIGURATION, mapper.writeValueAsString(options));
        testRunner.setProperty(PLC_FUTURE_TIMEOUT_MILISECONDS, "1000");

        testRunner.addConnection(REL_SUCCESS);
        testRunner.addConnection(REL_FAILURE);

        testRunner.assertNotValid();
    }


    
}

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

package org.apache.plc4x.nifi.service.util;

import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.processor.util.StandardValidators;

public class ConnectionControllerProperties {

    public static final PropertyDescriptor CONNECTION_STRING_STRATEGY = new PropertyDescriptor.Builder()
        .name("string-connection-strategy")
        .displayName("Connection String Access Strategy")
        .description("Strategy used to obtain the Connection String")
        .required(true)
        .build();

    public static final PropertyDescriptor PLC_FUTURE_TIMEOUT_MILISECONDS = new PropertyDescriptor.Builder()
            .name("plc4x-record-timeout")
            .displayName("Read/Write timeout (miliseconds)")
            .description("Read/Write timeout in miliseconds")
            .defaultValue("10000")
            .required(true)
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .build();


    public static final AllowableValue CONSTANT_STRING_CONNECTION = new AllowableValue(
        "constant-string-connection", 
        "Use 'Constant Connection String' Property");

    public static final PropertyDescriptor CONSTANT_STRING_CONNECTION_PROPERTY = new PropertyDescriptor.Builder()
        .name("constant-string-connection-property")
        .displayName("Constant Connection String")
        .expressionLanguageSupported(ExpressionLanguageScope.NONE)
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .dependsOn(CONNECTION_STRING_STRATEGY, CONSTANT_STRING_CONNECTION)
        .required(true)
        .build();


    public static final AllowableValue ATTRIBUTE_STRING_CONNECTION = new AllowableValue(
        "attribute-string-connection", 
        "Use 'Connection String' Property");

    public static final PropertyDescriptor ATTRIBUTE_STRING_CONNECTION_PROPERTY = new PropertyDescriptor.Builder()
        .name("attribute-string-connection-property")
        .displayName("Connection String")
        .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
        .addValidator(StandardValidators.NON_EMPTY_EL_VALIDATOR)
        .defaultValue("${plc4x.connection_string}")
        .dependsOn(CONNECTION_STRING_STRATEGY, ATTRIBUTE_STRING_CONNECTION)
        .required(true)
        .build();

    public static final PropertyDescriptor PLC_AUTHENTICATION_USERNAME_CONSTANT = new PropertyDescriptor.Builder()
        .name("plc-authentication-username-constant")
        .displayName("Plc Authentication Username")
        .description("Username used for plc authentication")
        .expressionLanguageSupported(ExpressionLanguageScope.NONE)
        .dependsOn(CONNECTION_STRING_STRATEGY, CONSTANT_STRING_CONNECTION)
        .addValidator(new Validator() {
            @Override
            public ValidationResult validate(String subject, String input, ValidationContext context) {
                return new ValidationResult.Builder().valid(true).build();
            }
        })
        .required(false)
        .defaultValue("")
        .build();

    public static final PropertyDescriptor PLC_AUTHENTICATION_PASSWORD_CONSTANT = new PropertyDescriptor.Builder()
        .name("plc-authentication-password-constant")
        .displayName("Plc Authentication Password")
        .description("Password used for plc authentication")
        .expressionLanguageSupported(ExpressionLanguageScope.NONE)
        .dependsOn(CONNECTION_STRING_STRATEGY, CONSTANT_STRING_CONNECTION)
        .addValidator(new Validator() {
            @Override
            public ValidationResult validate(String subject, String input, ValidationContext context) {
                return new ValidationResult.Builder().valid(true).build();
            }
        })
        .sensitive(true)
        .required(false)
        .defaultValue("")
        .build();

    public static final PropertyDescriptor PLC_AUTHENTICATION_USERNAME_ATTRIBUTE = new PropertyDescriptor.Builder()
        .name("plc-authentication-username-attribute")
        .displayName("Plc Authentication Username")
        .description("Username used for plc authentication")
        .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
        .dependsOn(CONNECTION_STRING_STRATEGY, ATTRIBUTE_STRING_CONNECTION)
        .addValidator(new Validator() {
            @Override
            public ValidationResult validate(String subject, String input, ValidationContext context) {
                return new ValidationResult.Builder().valid(true).build();
            }
        })
        .required(false)
        .defaultValue("${plc4x.username}")
        .build();

    public static final PropertyDescriptor PLC_AUTHENTICATION_PASSWORD_ATTRIBUTE = new PropertyDescriptor.Builder()
        .name("plc-authentication-password-attribute")
        .displayName("Plc Authentication Password")
        .description("Password used for plc authentication")
        .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
        .dependsOn(CONNECTION_STRING_STRATEGY, ATTRIBUTE_STRING_CONNECTION)
        .addValidator(new Validator() {
            @Override
            public ValidationResult validate(String subject, String input, ValidationContext context) {
                return new ValidationResult.Builder().valid(true).build();
            }
        })
        .required(false)
        .defaultValue("${plc4x.password}")
        .build();

    
}
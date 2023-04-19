/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.java.opcua.context;

import org.apache.plc4x.java.api.exceptions.PlcRuntimeException;
import org.apache.plc4x.java.opcua.protocol.OpcuaProtocolLogic;
import org.apache.plc4x.java.opcua.readwrite.MessagePDU;
import org.apache.plc4x.java.opcua.readwrite.OpcuaAPU;
import org.apache.plc4x.java.opcua.readwrite.OpcuaMessageResponse;
import org.apache.plc4x.java.opcua.readwrite.OpcuaOpenResponse;
import org.apache.plc4x.java.spi.generation.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;


public class EncryptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpcuaProtocolLogic.class);

    static {
        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
        Security.addProvider(new BouncyCastleProvider());
    }

    private X509Certificate serverCertificate;
    private X509Certificate clientCertificate;
    private PrivateKey clientPrivateKey;
    private PublicKey clientPublicKey;
    private String securitypolicy;
    private SecretKey symmetricLocalSigningKey;
    private SecretKey symmetricLocalEncryptingKey;
    private IvParameterSpec symmetricLocalinitializationVector;
    private SecretKey symmetricRemoteSigningKey;
    private SecretKey symmetricRemoteEncryptingKey;
    private IvParameterSpec symmetricRemoteinitializationVector;

    public EncryptionHandler(CertificateKeyPair ckp, byte[] senderCertificate, String securityPolicy) {
        if (ckp != null) {
            this.clientPrivateKey = ckp.getKeyPair().getPrivate();
            this.clientPublicKey = ckp.getKeyPair().getPublic();
            this.clientCertificate = ckp.getCertificate();
        }
        if (senderCertificate != null) {
            this.serverCertificate = getCertificateX509(senderCertificate);
        }
        this.securitypolicy = securityPolicy;
    }

    public void setServerCertificate(X509Certificate serverCertificate) {
        this.serverCertificate = serverCertificate;
    }

    public ReadBuffer encodeMessage(MessagePDU pdu, byte[] message, boolean asymmetric) {
        int PREENCRYPTED_BLOCK_LENGTH = asymmetric? 190: 16;
        int ENCRYPTED_BLOCK_LENGTH = asymmetric? 256: 16;
        int signatureSize = asymmetric? 256: 32;
        int unencryptedLength = pdu.getLengthInBytes();
        int encryptedMessageLength = message.length + 8;
        int positionFirstBlock = unencryptedLength - encryptedMessageLength;
        int paddingSize = PREENCRYPTED_BLOCK_LENGTH - ((encryptedMessageLength + signatureSize + 1) % PREENCRYPTED_BLOCK_LENGTH);
        int preEncryptedLength = encryptedMessageLength + signatureSize + 1 + paddingSize;
        if (preEncryptedLength % PREENCRYPTED_BLOCK_LENGTH != 0) {
            throw new PlcRuntimeException("Pre encrypted block length " + preEncryptedLength + " isn't a multiple of the block size");
        }
        int numberOfBlocks = preEncryptedLength / PREENCRYPTED_BLOCK_LENGTH;
        int encryptedLength = numberOfBlocks * ENCRYPTED_BLOCK_LENGTH + positionFirstBlock;
        WriteBufferByteBased buf = new WriteBufferByteBased(encryptedLength, ByteOrder.LITTLE_ENDIAN);
        try {
            new OpcuaAPU(pdu).serialize(buf);
            byte paddingByte = (byte) paddingSize;
            buf.writeByte(paddingByte);
            for (int i = 0; i < paddingSize; i++) {
                buf.writeByte(paddingByte);
            }
            //Writing Message Length
            int tempPos = buf.getPos();
            buf.setPos(4);
            buf.writeInt(32, encryptedLength);
            buf.setPos(tempPos);
            byte[] signature = sign(getBytes(buf.getBytes(), 0, unencryptedLength + paddingSize + 1), asymmetric);
            //Write the signature to the end of the buffer
            for (byte b : signature) {
                buf.writeByte(b);
            }

            buf.setPos(positionFirstBlock + preEncryptedLength);
            byte[] data = getBytes(buf.getBytes(), positionFirstBlock, positionFirstBlock + preEncryptedLength);
            
            buf.setPos(positionFirstBlock);
            encryptBlock(buf, data, asymmetric);


            return new ReadBufferByteBased(buf.getBytes(), ByteOrder.LITTLE_ENDIAN);
        } catch (SerializationException e) {
            throw new PlcRuntimeException("Unable to parse apu prior to encrypting");
        }
    }

    public OpcuaAPU decodeMessage(OpcuaAPU pdu, boolean asymmetric) {
        LOGGER.info("Decoding Message with Security policy {}", securitypolicy);
        switch (securitypolicy) {
            case "None":
                return pdu;
            case "Basic256Sha256":
                byte[] message;
                if (pdu.getMessage() instanceof OpcuaOpenResponse) {
                    message = ((OpcuaOpenResponse) pdu.getMessage()).getMessage();
                } else if (pdu.getMessage() instanceof OpcuaMessageResponse) {
                    message = ((OpcuaMessageResponse) pdu.getMessage()).getMessage();
                } else {
                    return pdu;
                }
                try {
                    int ENCRYPTED_BLOCK_LENGTH = asymmetric? 256: 16;
                    int encryptedLength = pdu.getLengthInBytes();
                    int encryptedMessageLength = message.length + 8;
                    int headerLength = encryptedLength - encryptedMessageLength;
                    int numberOfBlocks = encryptedMessageLength / ENCRYPTED_BLOCK_LENGTH;
                    WriteBufferByteBased buf = new WriteBufferByteBased(headerLength + numberOfBlocks * ENCRYPTED_BLOCK_LENGTH, ByteOrder.LITTLE_ENDIAN);
                    pdu.serialize(buf);
                    byte[] data = getBytes(buf.getBytes(), headerLength, encryptedLength);
                    buf.setPos(headerLength);
                    decryptBlock(buf, data, asymmetric);
                    int tempPos = buf.getPos();

                    if (!checkSignature(getBytes(buf.getBytes(), 0, tempPos), asymmetric)) {
                        LOGGER.info("Signature verification failed: - {}", getBytes(buf.getBytes(), 0, tempPos - ENCRYPTED_BLOCK_LENGTH));
                    }
                    buf.setPos(4);
                    buf.writeInt(32, tempPos - ENCRYPTED_BLOCK_LENGTH);
                    buf.setPos(tempPos);
                    ReadBuffer readBuffer = new ReadBufferByteBased(getBytes(buf.getBytes(), 0, tempPos - ENCRYPTED_BLOCK_LENGTH), ByteOrder.LITTLE_ENDIAN);
                    return OpcuaAPU.staticParse(readBuffer, true);
                } catch (SerializationException | ParseException e) {
                    LOGGER.error("Unable to Parse encrypted message");
                }
        }
        return pdu;
    }

    public void encryptBlock(WriteBuffer buf, byte[] data, boolean asymmetric) {
        try {
            Cipher cipher;
            if (asymmetric) {
                cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, this.serverCertificate.getPublicKey());

                int block_size = asymmetric? 190: 16;
                int encrypted_size = asymmetric? 256: 16;
                for (int i = 0; i < data.length; i += block_size) {
                    LOGGER.info("Iterate:- {}, Data Length:- {}", i, data.length);
                    byte[] encrypted = cipher.doFinal(data, i, block_size);
                    for (int j = 0; j < encrypted_size; j++) {
                        buf.writeByte(encrypted[j]);
                    }

                }

            } else {
                cipher = Cipher.getInstance("AES/CBC/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, this.getSecretLocalEnryptingKey(), getSecretLocalInitializationVector());
                buf.writeByteArray(cipher.doFinal(data));
            }
            
        } catch (Exception e) {
            LOGGER.error("Unable to encrypt Data");
            e.printStackTrace();
        }
    }

    public void decryptBlock(WriteBuffer buf, byte[] data, boolean asymmetric) {
        try {
            Cipher cipher;
            if (asymmetric) {
                cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
                cipher.init(Cipher.DECRYPT_MODE, this.clientPrivateKey);

                int block_size = asymmetric? 214: 16;
                int encrypted_size = asymmetric? 256: 16;
                for (int i = 0; i < data.length; i += encrypted_size) {
                    byte[] decrypted = cipher.doFinal(data, i, encrypted_size);

                    for (int j = 0; j < block_size; j++) {
                        buf.writeByte(decrypted[j]);
                    }
                }
            } else {
                cipher = Cipher.getInstance("AES/CBC/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, this.getSecretRemoteEnryptingKey(), getSecretRemoteInitializationVector());

                buf.writeByteArray(cipher.doFinal(data));
            }

            
        } catch (Exception e) {
            LOGGER.error("Unable to decrypt Data", e);
        }
    }

    public boolean checkSignature(byte[] data, boolean asymmetric) {
        try {
            if (asymmetric) {
                Signature signature =  Signature.getInstance("SHA256withRSA");
                signature.initVerify(serverCertificate.getPublicKey());
                signature.update(getBytes(data, 0, data.length - 256));
                return signature.verify(getBytes(data, data.length - 256, data.length));
            } else {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(getSecretRemoteSigningKey());
                return Arrays.equals(
                    getBytes(data, data.length - 32, data.length),
                    mac.doFinal(getBytes(data, 0, data.length - 32))
                    );
            }
           
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Unable to sign Data");
            return false;
        }
    }

    public boolean checkSignatureLocal(byte[] data, boolean asymmetric) {
        try {
            if (asymmetric) {
                Signature signature =  Signature.getInstance("SHA256withRSA");
                signature.initVerify(clientCertificate.getPublicKey());
                signature.update(getBytes(data, 0, data.length - 256));
                return signature.verify(getBytes(data, data.length - 256, data.length));
            } else {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(getSecretLocalSigningKey());
                return Arrays.equals(
                    getBytes(data, data.length - 32, data.length),
                    mac.doFinal(getBytes(data, 0, data.length-32))
                    );
            }
           
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Unable to sign Data");
            return false;
        }
    }

    public byte[] encryptPassword(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, this.serverCertificate.getPublicKey());
            return cipher.doFinal(data);
        } catch (Exception e) {
            LOGGER.error("Unable to encrypt Data", e);
            return null;
        }
    }

    public SecretKey getSecretLocalSigningKey() {
        return symmetricLocalSigningKey;
    }

    public SecretKey getSecretLocalEnryptingKey() {
        return symmetricLocalEncryptingKey;
    }
    public IvParameterSpec getSecretLocalInitializationVector() {
        return symmetricLocalinitializationVector;
    }

    public SecretKey getSecretRemoteSigningKey() {
        return symmetricRemoteSigningKey;
    }

    public SecretKey getSecretRemoteEnryptingKey() {
        return symmetricRemoteEncryptingKey;
    }

    public IvParameterSpec getSecretRemoteInitializationVector() {
        return symmetricRemoteinitializationVector;
    }

    public void setSecretSymmetricKeys(byte[] localNonce, byte[] remoteNonce) {
        try {
            byte[] result = pHash(remoteNonce, localNonce);
            symmetricLocalSigningKey = new SecretKeySpec(result, 0, 32, "HmacSHA256");
            symmetricLocalEncryptingKey = new SecretKeySpec(result, 32, 32, "AES/CBC/NoPadding");
            symmetricLocalinitializationVector = new IvParameterSpec(getBytes(result, 64, 80));

            result = pHash(localNonce, remoteNonce);
            symmetricRemoteSigningKey = new SecretKeySpec(result, 0, 32, "HmacSHA256");
            symmetricRemoteEncryptingKey = new SecretKeySpec(result, 32, 32, "AES/CBC/NoPadding");
            symmetricRemoteinitializationVector = new IvParameterSpec(getBytes(result, 64, 80));

        } catch (Exception e) {
            LOGGER.error("Unable to create secret symmetric keys");
            e.printStackTrace();
        }
    }

    private byte[] pHash(byte[] secret, byte[] seed){
        try{
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
            
            int required = seed.length + secret.length + 16;
            byte[] result = new byte[required];
            byte[] accum;
            byte[] feed;
            
            int offset = 0;
            int newOffset;
            
            mac.init(key);
            accum = seed;
            while (required > 0) {
                mac.update(accum);
                accum = mac.doFinal();
                mac.reset();
                mac.init(key);
                mac.update(accum);
                mac.update(seed);
                feed = mac.doFinal();
                newOffset = Math.min(required, feed.length);
                System.arraycopy(feed, 0, result, offset, newOffset);
                offset += newOffset;
                required -= newOffset;
            }
            return result;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public static X509Certificate getCertificateX509(byte[] senderCertificate) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            LOGGER.info("Public Key Length {}", senderCertificate.length);
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(senderCertificate));
        } catch (Exception e) {
            LOGGER.error("Unable to get certificate from String {}", senderCertificate);
            return null;
        }
    }

    public byte[] sign(byte[] data, boolean asymmetric) {
        try {
            byte[] ss;
            
            if (asymmetric) {
                Signature signature = Signature.getInstance("SHA256withRSA");
                signature.initSign(this.clientPrivateKey);
                signature.update(data);
                ss = signature.sign();
            } else {
                Mac signature = Mac.getInstance("HmacSHA256");
                signature.init(getSecretLocalSigningKey());
                ss = signature.doFinal(data);
            }
            
            LOGGER.info("----------------Signature Length{}", ss.length);
            return ss;
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Unable to sign Data");
            return null;
        }
    }

    private byte[] getBytes(byte[] bytes, int startPos, int endPos) {
        int numBytes = endPos - startPos;
        byte[] data = new byte[numBytes];
        System.arraycopy(bytes, startPos, data, 0, numBytes);
        return data;
    }
}

package com.codehouse.ciciassistant.wecom.service;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

@Service
public class WecomKfCryptoService {

    private static final int BLOCK_SIZE = 32;
    private final SecureRandom random = new SecureRandom();

    public String signature(String token, String timestamp, String nonce, String encrypted) {
        String[] items = new String[] { blank(token), blank(timestamp), blank(nonce), blank(encrypted) };
        Arrays.sort(items);
        String joined = String.join("", items);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("WeCom signature failed", ex);
        }
    }

    public boolean matches(String token, String timestamp, String nonce, String encrypted, String expected) {
        return expected != null && signature(token, timestamp, nonce, encrypted).equalsIgnoreCase(expected.trim());
    }

    public String decrypt(String encodingAesKey, String encrypted) {
        try {
            byte[] key = decodeKey(encodingAesKey);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(blank(encrypted)));
            byte[] unpadded = stripPkcs7(decrypted);
            ByteBuffer buffer = ByteBuffer.wrap(unpadded);
            byte[] randomBytes = new byte[16];
            buffer.get(randomBytes);
            int xmlLength = buffer.getInt();
            if (xmlLength < 0 || xmlLength > buffer.remaining()) {
                throw new IllegalArgumentException("Invalid WeCom callback payload length");
            }
            byte[] xmlBytes = new byte[xmlLength];
            buffer.get(xmlBytes);
            return new String(xmlBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("WeCom decrypt failed", ex);
        }
    }

    public String encrypt(String encodingAesKey, String corpId, String plaintextXml) {
        try {
            byte[] key = decodeKey(encodingAesKey);
            byte[] xmlBytes = blank(plaintextXml).getBytes(StandardCharsets.UTF_8);
            byte[] corpBytes = blank(corpId).getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + xmlBytes.length + corpBytes.length + BLOCK_SIZE);
            byte[] randomBytes = new byte[16];
            random.nextBytes(randomBytes);
            buffer.put(randomBytes);
            buffer.putInt(xmlBytes.length);
            buffer.put(xmlBytes);
            buffer.put(corpBytes);
            byte[] raw = Arrays.copyOf(buffer.array(), buffer.position());
            byte[] padded = addPkcs7(raw);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
            return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
        } catch (Exception ex) {
            throw new IllegalArgumentException("WeCom encrypt failed", ex);
        }
    }

    public String extractEncrypt(String xml) {
        return text(xml, "Encrypt");
    }

    public String text(String xml, String tagName) {
        if (xml == null || xml.isBlank() || tagName == null || tagName.isBlank()) {
            return "";
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var nodes = doc.getElementsByTagName(tagName);
            if (nodes.getLength() == 0 || nodes.item(0) == null) {
                return "";
            }
            return blank(nodes.item(0).getTextContent());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid WeCom XML", ex);
        }
    }

    private byte[] decodeKey(String encodingAesKey) {
        String value = blank(encodingAesKey);
        if (value.length() != 43) {
            throw new IllegalArgumentException("EncodingAESKey must be 43 characters");
        }
        return Base64.getDecoder().decode(value + "=");
    }

    private byte[] addPkcs7(byte[] raw) {
        int amount = BLOCK_SIZE - raw.length % BLOCK_SIZE;
        if (amount == 0) {
            amount = BLOCK_SIZE;
        }
        byte[] out = Arrays.copyOf(raw, raw.length + amount);
        Arrays.fill(out, raw.length, out.length, (byte) amount);
        return out;
    }

    private byte[] stripPkcs7(byte[] raw) {
        if (raw.length == 0) {
            throw new IllegalArgumentException("Empty payload");
        }
        int amount = raw[raw.length - 1] & 0xff;
        if (amount < 1 || amount > BLOCK_SIZE || amount > raw.length) {
            throw new IllegalArgumentException("Invalid padding");
        }
        return Arrays.copyOf(raw, raw.length - amount);
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }
}

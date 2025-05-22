package com.igot.cb.authentication.model;

import lombok.Getter;
import lombok.Setter;

import java.security.PublicKey;

/**
 * @author Mahesh RV
 */
@Setter
@Getter
public class KeyData {
    private String keyId;
    private PublicKey publicKey;

    public KeyData(String keyId, PublicKey publicKey) {
        this.keyId = keyId;
        this.publicKey = publicKey;
    }
}
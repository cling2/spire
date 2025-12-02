package com.example.spiredemo.dto;

import java.time.Instant;

/**
 * /svid API에서 내려줄 응답용 DTO.
 */
public class SvidInfo {

    private String spiffeId;
    private Instant notAfter;

    public SvidInfo(String spiffeId, Instant notAfter) {
        this.spiffeId = spiffeId;
        this.notAfter = notAfter;
    }

    public String getSpiffeId() {
        return spiffeId;
    }

    public void setSpiffeId(String spiffeId) {
        this.spiffeId = spiffeId;
    }

    public Instant getNotAfter() {
        return notAfter;
    }

    public void setNotAfter(Instant notAfter) {
        this.notAfter = notAfter;
    }
}

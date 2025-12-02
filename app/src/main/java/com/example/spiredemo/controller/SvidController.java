package com.example.spiredemo.controller;

import com.example.spiredemo.dto.SvidInfo;
import com.example.spiredemo.service.SvidService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 단순 테스트용: GET /svid 호출 시 현재 프로세스의 SPIFFE ID를 반환.
 */
@RestController
public class SvidController {

    private final SvidService svidService;

    public SvidController(SvidService svidService) {
        this.svidService = svidService;
    }

    @GetMapping("/svid")
    public ResponseEntity<SvidInfo> getSvid() {
        SvidInfo info = svidService.fetchCurrentSvid();
        return ResponseEntity.ok(info);
    }
}

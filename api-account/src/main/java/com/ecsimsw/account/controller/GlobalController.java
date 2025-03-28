package com.ecsimsw.account.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class GlobalController {

    @GetMapping("/api/user/health")
    public String health() {
        return "OK";
    }
}

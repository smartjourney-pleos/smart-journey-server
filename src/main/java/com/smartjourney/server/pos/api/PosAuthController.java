package com.smartjourney.server.pos.api;

import com.smartjourney.server.pos.api.PosDtos.PosLoginRequest;
import com.smartjourney.server.pos.api.PosDtos.PosLoginResponse;
import com.smartjourney.server.pos.api.PosDtos.PosStoreOption;
import com.smartjourney.server.pos.application.PosAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "7. POS 로그인", description = "P-01 매장 선택 + PIN")
@RestController
@RequestMapping("/api/v1/pos")
@RequiredArgsConstructor
public class PosAuthController {

    private final PosAuthService posAuthService;

    @Operation(summary = "로그인 화면 매장 목록", description = "인증 불필요")
    @GetMapping("/stores")
    public List<PosStoreOption> stores() {
        return posAuthService.stores();
    }

    @Operation(summary = "PIN 로그인 → JWT", description = "인증 불필요. 데모 매장 PIN 1234")
    @PostMapping("/login")
    public PosLoginResponse login(@Valid @RequestBody PosLoginRequest request) {
        return posAuthService.login(request.storeId(), request.pin());
    }
}

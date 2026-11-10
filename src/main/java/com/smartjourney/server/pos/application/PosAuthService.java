package com.smartjourney.server.pos.application;

import com.smartjourney.server.common.error.BusinessException;
import com.smartjourney.server.common.error.ErrorCode;
import com.smartjourney.server.common.security.PosTokenProvider;
import com.smartjourney.server.pos.api.PosDtos.PosLoginResponse;
import com.smartjourney.server.pos.api.PosDtos.PosStoreOption;
import com.smartjourney.server.store.domain.Store;
import com.smartjourney.server.store.domain.StoreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POS 로그인 (P-01): 매장 선택 + PIN → JWT.
 * TODO(Phase 4): PIN 연속 실패 잠금 (5회 / 5분)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PosAuthService {

    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final PosTokenProvider tokenProvider;

    public List<PosStoreOption> stores() {
        return storeRepository.findAll().stream()
                .map(s -> new PosStoreOption(s.getId(), s.getName()))
                .toList();
    }

    public PosLoginResponse login(long storeId, String pin) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POS_LOGIN_FAILED));
        if (!passwordEncoder.matches(pin, store.getPosPinHash())) {
            throw new BusinessException(ErrorCode.POS_LOGIN_FAILED);
        }
        PosTokenProvider.IssuedToken token = tokenProvider.issue(store.getId());
        return new PosLoginResponse(token.accessToken(), token.expiresAt(), store.getId(), store.getName());
    }
}

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
 * PIN 연속 실패 잠금: 5분 안에 5번 틀리면 5분 동안 429 POS_LOGIN_LOCKED ({@link PinAttemptLimiter}).
 * 없는 매장도 같은 401 로 답해 매장 id 존재 여부를 흘리지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PosAuthService {

    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final PosTokenProvider tokenProvider;
    private final PinAttemptLimiter limiter;

    public List<PosStoreOption> stores() {
        return storeRepository.findAll().stream()
                .map(s -> new PosStoreOption(s.getId(), s.getName()))
                .toList();
    }

    public PosLoginResponse login(long storeId, String pin) {
        if (limiter.isLocked(storeId)) {
            throw new BusinessException(ErrorCode.POS_LOGIN_LOCKED);
        }
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POS_LOGIN_FAILED));
        if (!passwordEncoder.matches(pin, store.getPosPinHash())) {
            limiter.recordFailure(storeId);
            throw new BusinessException(ErrorCode.POS_LOGIN_FAILED);
        }
        limiter.recordSuccess(storeId);
        PosTokenProvider.IssuedToken token = tokenProvider.issue(store.getId());
        return new PosLoginResponse(token.accessToken(), token.expiresAt(), store.getId(), store.getName());
    }
}

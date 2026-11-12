package dev.settle.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
class SessionMaintenance {

    private static final Logger log = LoggerFactory.getLogger(SessionMaintenance.class);

    private final RefreshTokenService refreshTokens;

    SessionMaintenance(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(cron = "0 17 3 * * *")
    void purgeExpiredRefreshTokens() {
        int removed = refreshTokens.purgeExpired();
        log.atInfo().addKeyValue("refresh_tokens.removed", removed).log("Expired refresh tokens purged");
    }
}

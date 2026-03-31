package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures the system-owned publisher account used by remote mirror ingestion exists.
 */
@Component
public class RemoteMirrorBotInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RemoteMirrorBotInitializer.class);
    private static final String USER_ID = "remote-mirror-bot";
    private static final String DISPLAY_NAME = "Remote Mirror Bot";
    private static final String EMAIL = "remote-mirror-bot@skillhub.local";

    private final UserAccountRepository userAccountRepository;

    public RemoteMirrorBotInitializer(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userAccountRepository.findById(USER_ID)
                .orElseGet(() -> {
                    log.info("Creating remote mirror bot account: {}", USER_ID);
                    return userAccountRepository.save(new UserAccount(USER_ID, DISPLAY_NAME, EMAIL, null));
                });
    }
}

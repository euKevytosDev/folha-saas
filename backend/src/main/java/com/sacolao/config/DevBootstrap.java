package com.sacolao.config;

import com.sacolao.common.util.EmailNormalizer;
import com.sacolao.user.entity.UserRole;
import com.sacolao.user.repository.UserRepository;
import com.sacolao.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("dev")
public class DevBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DevBootstrap.class);

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final UserService userService;

    public DevBootstrap(AppProperties properties, UserRepository userRepository, UserService userService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapSuperAdmin() {
        String email = EmailNormalizer.normalize(properties.bootstrap().superadminEmail());
        String password = properties.bootstrap().superadminPassword();
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            return;
        }
        if (userRepository.existsByRole(UserRole.SUPER_ADMIN) || userRepository.existsByEmail(email)) {
            return;
        }
        userService.createSuperAdmin("Super Admin", email, password);
        log.info("SUPER_ADMIN inicial criado");
    }
}

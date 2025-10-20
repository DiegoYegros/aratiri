package com.aratiri.auth.application;

import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.auth.application.port.in.RegistrationCommand;
import com.aratiri.auth.application.port.in.RegistrationPort;
import com.aratiri.auth.application.port.in.VerificationCommand;
import com.aratiri.auth.application.port.out.AccessTokenPort;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.auth.application.port.out.PasswordEncoderPort;
import com.aratiri.auth.application.port.out.RefreshTokenPort;
import com.aratiri.auth.application.port.out.RegistrationDraftPort;
import com.aratiri.auth.application.port.out.UserCommandPort;
import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.RegistrationDraft;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.accounts.application.dto.CreateAccountRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Random;

@Service
public class RegistrationAdapter implements RegistrationPort {

    private final RegistrationDraftPort registrationDraftPort;
    private final EmailNotificationPort emailNotificationPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final UserCommandPort userCommandPort;
    private final AccountsPort accountsPort;
    private final AccessTokenPort accessTokenPort;
    private final RefreshTokenPort refreshTokenPort;
    private final LoadUserPort loadUserPort;
    private final Clock clock;
    private final Random random = new Random();

    public RegistrationAdapter(
            RegistrationDraftPort registrationDraftPort,
            EmailNotificationPort emailNotificationPort,
            PasswordEncoderPort passwordEncoderPort,
            UserCommandPort userCommandPort,
            AccountsPort accountsPort,
            AccessTokenPort accessTokenPort,
            RefreshTokenPort refreshTokenPort,
            LoadUserPort loadUserPort,
            Clock clock
    ) {
        this.registrationDraftPort = registrationDraftPort;
        this.emailNotificationPort = emailNotificationPort;
        this.passwordEncoderPort = passwordEncoderPort;
        this.userCommandPort = userCommandPort;
        this.accountsPort = accountsPort;
        this.accessTokenPort = accessTokenPort;
        this.refreshTokenPort = refreshTokenPort;
        this.loadUserPort = loadUserPort;
        this.clock = clock;
    }

    @Override
    public void initiateRegistration(RegistrationCommand command) {
        loadUserPort.findByEmail(command.email()).ifPresent(user -> {
            throw new AratiriException("Email is already in use", HttpStatus.BAD_REQUEST.value());
        });
        if (command.alias() != null && !command.alias().isBlank() && accountsPort.existsByAlias(command.alias())) {
            throw new AratiriException("Alias is already in use", HttpStatus.BAD_REQUEST.value());
        }
        String code = generateVerificationCode();
        RegistrationDraft draft = new RegistrationDraft(
                command.email(),
                command.name(),
                passwordEncoderPort.encode(command.password()),
                command.alias(),
                code,
                LocalDateTime.now(clock).plusMinutes(15)
        );
        registrationDraftPort.save(draft);
        emailNotificationPort.sendVerificationEmail(command.email(), code);
    }

    @Override
    public AuthTokens completeRegistration(VerificationCommand command) {
        RegistrationDraft draft = registrationDraftPort.findByEmail(command.email())
                .orElseThrow(() -> new AratiriException("Invalid verification request", HttpStatus.BAD_REQUEST.value()));
        if (draft.isExpired(clock)) {
            registrationDraftPort.deleteByEmail(command.email());
            throw new AratiriException("Verification code has expired", HttpStatus.BAD_REQUEST.value());
        }
        if (!draft.code().equals(command.code())) {
            throw new AratiriException("Invalid verification code", HttpStatus.BAD_REQUEST.value());
        }
        AuthUser user = userCommandPort.registerLocalUser(draft.name(), draft.email(), draft.encodedPassword());
        CreateAccountRequestDTO createAccountRequest = new CreateAccountRequestDTO();
        createAccountRequest.setUserId(user.id());
        createAccountRequest.setAlias(draft.alias());
        accountsPort.createAccount(createAccountRequest, user.id());
        registrationDraftPort.deleteByEmail(command.email());
        String accessToken = accessTokenPort.generateAccessToken(user.email());
        String refreshToken = refreshTokenPort.createRefreshToken(user.id()).token();
        return new AuthTokens(accessToken, refreshToken);
    }

    private String generateVerificationCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}

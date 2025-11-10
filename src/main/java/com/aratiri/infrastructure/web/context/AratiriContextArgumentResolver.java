package com.aratiri.infrastructure.web.context;

import com.aratiri.auth.application.dto.UserDTO;
import com.aratiri.auth.application.port.in.AuthPort;
import com.aratiri.auth.domain.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AratiriContextArgumentResolver implements HandlerMethodArgumentResolver {

    private static final Logger logger = LoggerFactory.getLogger(AratiriContextArgumentResolver.class);

    private final AuthPort authPort;

    @Autowired
    public AratiriContextArgumentResolver(AuthPort authPort) {
        this.authPort = authPort;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(AratiriCtx.class) != null &&
                parameter.getParameterType().equals(AratiriContext.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        UserDTO currentUser;
        try {
            AuthUser authUser = authPort.getCurrentUser();
            currentUser = new UserDTO(authUser.id(), authUser.name(), authUser.email(), authUser.role());
            logger.info("Current User: [{}]", currentUser);
        } catch (Exception e) {
            logger.warn("Could not retrieve current user for AratiriContext: {}", e.getMessage());
            return null;
        }
        return new AratiriContext(currentUser);
    }
}

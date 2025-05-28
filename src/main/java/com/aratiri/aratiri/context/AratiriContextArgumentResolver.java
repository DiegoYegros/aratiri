package com.aratiri.aratiri.context;

import com.aratiri.aratiri.dto.users.UserDTO;
import com.aratiri.aratiri.service.AuthService;
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

    private final AuthService authService;

    @Autowired
    public AratiriContextArgumentResolver(AuthService authService) {
        this.authService = authService;
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
        logger.info("I'm resolving the argument.");
        try {
            currentUser = authService.getCurrentUser(); // TODO: this has to be a call to a database, cache or something
            logger.info("Current User: [{}]", currentUser);
        } catch (Exception e) {
            logger.warn("Could not retrieve current user for AratiriContext: {}", e.getMessage());
            return null;
        }

        if (currentUser == null) {
            return null;
        }
        return new AratiriContext(currentUser);
    }
}
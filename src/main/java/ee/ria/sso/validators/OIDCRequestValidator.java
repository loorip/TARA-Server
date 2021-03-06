package ee.ria.sso.validators;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.support.oauth.OAuth20GrantTypes;
import org.pac4j.core.context.J2EContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.sso.authentication.LevelOfAssurance;
import ee.ria.sso.flow.JSONFlowExecutionException;

/**
 * Created by Janar Rahumeel (CGI Estonia)
 */

public class OIDCRequestValidator {

    private static final Logger log = LoggerFactory.getLogger(OIDCRequestValidator.class);

    public static void checkGrantType(HttpServletRequest request) {
        Optional<String> grantType = Optional.ofNullable(request.getParameter("grant_type"));
        if (grantType.isPresent()) {
            if (!OAuth20GrantTypes.AUTHORIZATION_CODE.getType().equals(grantType.get())) {
                throw JSONFlowExecutionException.ofBadRequest(Collections.singletonMap("error", "unsupported_grant_type"),
                    new RuntimeException("Unsupported grant type"));
            }
        } else {
            throw JSONFlowExecutionException.ofBadRequest(Collections.singletonMap("error", "invalid_request"),
                new RuntimeException("No grant type found"));
        }
    }

    public static Optional<Integer> validateAll(final J2EContext context, final List<RequestParameter> parameters) {
        if (CollectionUtils.isNotEmpty(parameters)) {
            for (RequestParameter parameter : parameters) {
                Optional<Integer> r = validate(context, parameter);
                if (r.isPresent()) {
                    return r;
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<Integer> validate(final J2EContext context, final RequestParameter parameter) {
        try {
            String[] values = context.getRequest().getParameterValues(parameter.getParameterKey());
            if (values != null && values.length > 1) {
                return resultOfBadRequest(ErrorResponse.of(context, "invalid_request",
                    String.format("Multiple values found in the request for <%s> parameter", parameter.getParameterKey())));
            }
            String parameterValue = context.getRequestParameter(parameter.getParameterKey());
            boolean isValueMandatory = parameter.isMandatory() || context.getRequestParameters().containsKey(parameter.getParameterKey());
            if (StringUtils.isBlank(parameterValue) && isValueMandatory) {
                return resultOfBadRequest(ErrorResponse.of(context, parameter.getError(),
                    String.format("No value found in the request for <%s> parameter", parameter.getParameterKey())));
            }
            Optional<Integer> code;
            switch (parameter) {
                case SCOPE:
                    code = validateScopeValue(context);
                    break;
                case RESPONSE_TYPE:
                    code = validateResponseType(context);
                    break;
                case ACR_VALUES:
                    code = validateAcrValues(context);
                    break;
                default:
                    code = Optional.empty();
            }
            return code;
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.error("Error while validating OIDC request", e);
            } else {
                log.error("Error while validating OIDC request: {}", e.getMessage());
            }
            return resultOfInternalServerError(ErrorResponse.of(context, "server_error"));
        }
    }

    /*
     * RESTRICTED METHODS
     */

    private static Optional<Integer> validateScopeValue(final J2EContext context) throws Exception {
        String scope = context.getRequestParameter(RequestParameter.SCOPE.name().toLowerCase());
        List scopes = Arrays.stream(scope.split(" ")).collect(Collectors.toList());

        if (scopes.isEmpty() || !scopes.contains(TaraScope.OPENID.getFormalName())) {
            return resultOfBadRequest(ErrorResponse.of(context, "invalid_scope", String.format(
                    "Required scope <%s> not provided. TARA do not allow this request to be processed",
                    TaraScope.OPENID.getFormalName()
            )));
        }

        List<String> allowedScopes = Stream.of(TaraScope.values()).map(TaraScope::getFormalName).collect(Collectors.toList());
        if (!ListUtils.subtract(scopes, allowedScopes).isEmpty()) {
            return resultOfBadRequest(ErrorResponse.of(context, "invalid_scope", String.format(
                    "One or some of the provided scopes are not allowed by TARA, only <%s> are permitted. TARA do not allow this request to be processed",
                    allowedScopes.stream().collect(Collectors.joining(", "))
            )));
        }

        return Optional.empty();
    }

    private static Optional<Integer> validateResponseType(final J2EContext context) {
        String responseType = context.getRequestParameter(RequestParameter.RESPONSE_TYPE.name().toLowerCase());
        if (!"code".equals(responseType)) {
            return resultOfBadRequest(ErrorResponse.of(context, "unsupported_response_type", String.format(
                    "Provided response type is not allowed by TARA, only <%s> is permitted. TARA do not allow this request to be processed", "code"
            )));
        }
        return Optional.empty();
    }

    private static Optional<Integer> validateAcrValues(final J2EContext context) {
        String acrValues = context.getRequestParameter(RequestParameter.ACR_VALUES.name().toLowerCase());

        if (acrValues != null) {
            final List<String> allowedValues = Stream.of(LevelOfAssurance.values())
                    .map(LevelOfAssurance::getAcrName).collect(Collectors.toList());

            if (!allowedValues.contains(acrValues)) {
                return resultOfBadRequest(ErrorResponse.of(context, "unsupported_acr_values", String.format(
                        "Provided acr_values is not allowed by TARA, only <%s> are permitted. TARA do not allow this request to be processed",
                        allowedValues.stream().collect(Collectors.joining(", ")
                ))));
            }
        }

        return Optional.empty();
    }

    private static Optional<Integer> resultOfInternalServerError(final ErrorResponse response) {
        return resultOf(response, Optional.of(500));
    }

    private static Optional<Integer> resultOfBadRequest(final ErrorResponse response) {
        return resultOf(response, Optional.of(400));
    }

    private static Optional<Integer> resultOf(final ErrorResponse response, Optional<Integer> optional) {
        if (response.hasErrorDescription()) {
            log.error(response.getErrorDescription());
        }
        if (response.isValidRedirectURI()) {
            if (!response.sendRedirect()) {
                return Optional.of(500);
            }
        }
        return optional;
    }

}

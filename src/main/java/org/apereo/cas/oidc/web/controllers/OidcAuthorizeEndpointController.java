//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.apereo.cas.oidc.web.controllers;

import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.message.BasicNameValuePair;
import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.principal.PrincipalFactory;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.authentication.principal.WebApplicationService;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.oidc.token.OidcIdTokenGeneratorService;
import org.apereo.cas.services.OidcRegisteredService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.services.UnauthorizedServiceException;
import org.apereo.cas.support.oauth.OAuth20ResponseTypes;
import org.apereo.cas.support.oauth.authenticator.OAuth20CasAuthenticationBuilder;
import org.apereo.cas.support.oauth.profile.OAuth20ProfileScopeToAttributesFilter;
import org.apereo.cas.support.oauth.util.OAuth20Utils;
import org.apereo.cas.support.oauth.validator.OAuth20Validator;
import org.apereo.cas.support.oauth.web.endpoints.OAuth20AuthorizeEndpointController;
import org.apereo.cas.support.oauth.web.response.accesstoken.ext.AccessTokenRequestDataHolder;
import org.apereo.cas.support.oauth.web.views.ConsentApprovalViewResolver;
import org.apereo.cas.ticket.TicketGrantingTicket;
import org.apereo.cas.ticket.accesstoken.AccessToken;
import org.apereo.cas.ticket.accesstoken.AccessTokenFactory;
import org.apereo.cas.ticket.code.OAuthCodeFactory;
import org.apereo.cas.ticket.registry.TicketRegistry;
import org.apereo.cas.web.support.CookieRetrievingCookieGenerator;
import org.apereo.cas.web.support.CookieUtils;
import org.pac4j.core.context.J2EContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import com.google.common.base.Throwables;

public class OidcAuthorizeEndpointController extends OAuth20AuthorizeEndpointController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcAuthorizeEndpointController.class);
    private final OidcIdTokenGeneratorService idTokenGenerator;

    public OidcAuthorizeEndpointController(ServicesManager servicesManager, TicketRegistry ticketRegistry, OAuth20Validator validator, AccessTokenFactory accessTokenFactory, PrincipalFactory principalFactory, ServiceFactory<WebApplicationService> webApplicationServiceServiceFactory, OAuthCodeFactory oAuthCodeFactory, ConsentApprovalViewResolver consentApprovalViewResolver, OidcIdTokenGeneratorService idTokenGenerator, OAuth20ProfileScopeToAttributesFilter scopeToAttributesFilter, CasConfigurationProperties casProperties, CookieRetrievingCookieGenerator ticketGrantingTicketCookieGenerator, OAuth20CasAuthenticationBuilder authenticationBuilder) {
        super(servicesManager, ticketRegistry, validator, accessTokenFactory, principalFactory, webApplicationServiceServiceFactory, oAuthCodeFactory, consentApprovalViewResolver, scopeToAttributesFilter, casProperties, ticketGrantingTicketCookieGenerator, authenticationBuilder);
        this.idTokenGenerator = idTokenGenerator;
    }

    @GetMapping({"/oidc/authorize"})
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Collection scopes = OAuth20Utils.getRequestedScopes(request);
        if (scopes.isEmpty() || !scopes.contains("openid")) {
            String message = String.format("Provided scopes [%s] are undefined by OpenID Connect, which requires that scope [%s] MUST be specified. CAS DO NOT allow this request to be processed for now", scopes, "openid");
            LOGGER.warn(message);
            return OAuth20Utils.produceErrorView(new UnauthorizedServiceException("screen.service.error.message", message));
        }
        return super.handleRequest(request, response);
    }

    /*
     * RESTRICTED METHODS
     */

    protected String buildCallbackUrlForTokenResponseType(J2EContext context, Authentication authentication, Service service, String redirectUri, String responseType, String clientId) {
        if (!OAuth20Utils.isResponseType(responseType, OAuth20ResponseTypes.IDTOKEN_TOKEN)) {
            return super.buildCallbackUrlForTokenResponseType(context, authentication, service, redirectUri, responseType, clientId);
        } else {
            LOGGER.debug("Handling callback for response type [{}]", responseType);
            TicketGrantingTicket ticketGrantingTicket = CookieUtils.getTicketGrantingTicketFromRequest(this.ticketGrantingTicketCookieGenerator, this.ticketRegistry, context.getRequest());
            return this.buildCallbackUrlForImplicitTokenResponseType(context, authentication, service, redirectUri, clientId, OAuth20ResponseTypes.IDTOKEN_TOKEN, ticketGrantingTicket);
        }
    }

    private String buildCallbackUrlForImplicitTokenResponseType(J2EContext context, Authentication authentication, Service service, String redirectUri, String clientId, OAuth20ResponseTypes responseType, TicketGrantingTicket ticketGrantingTicket) {
        try {
            OidcRegisteredService e = (OidcRegisteredService) OAuth20Utils.getRegisteredOAuthService(this.servicesManager, clientId);
            AccessTokenRequestDataHolder holder = new AccessTokenRequestDataHolder(service, authentication, e, ticketGrantingTicket);
            AccessToken accessToken = this.generateAccessToken(holder);
            LOGGER.debug("Generated OAuth access token: [{}]", accessToken);
            long timeout = (long) this.casProperties.getTicket().getTgt().getTimeToKillInSeconds();
            String idToken = this.idTokenGenerator.generate(context.getRequest(), context.getResponse(), accessToken, timeout, responseType, e);
            LOGGER.debug("Generated id token [{}]", idToken);
            ArrayList params = new ArrayList();
            params.add(new BasicNameValuePair("id_token", idToken));
            return this.buildCallbackUrlResponseType(authentication, service, redirectUri, accessToken, params);
        } catch (Exception var15) {
            throw Throwables.propagate(var15);
        }
    }

}

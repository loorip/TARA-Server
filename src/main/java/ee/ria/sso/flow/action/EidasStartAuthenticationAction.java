package ee.ria.sso.flow.action;

import ee.ria.sso.service.AuthenticationService;
import org.springframework.stereotype.Component;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

@Component("EIDASStartAuthenticationAction")
public class EidasStartAuthenticationAction extends AbstractAuthenticationAction {

    private final AuthenticationService authenticationService;

    public EidasStartAuthenticationAction(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /*
     * RESTRICTED METHODS
     */

    @Override
    protected Event doAuthenticationExecute(RequestContext requestContext) {
        return this.authenticationService.startLoginByEidas(requestContext);
    }

}

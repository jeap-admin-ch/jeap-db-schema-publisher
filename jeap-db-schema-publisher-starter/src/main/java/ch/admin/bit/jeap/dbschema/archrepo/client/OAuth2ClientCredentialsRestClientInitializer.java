package ch.admin.bit.jeap.dbschema.archrepo.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

@Slf4j
public class OAuth2ClientCredentialsRestClientInitializer implements ClientHttpRequestInitializer {

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final ClientRegistration clientRegistration;

    public OAuth2ClientCredentialsRestClientInitializer(OAuth2AuthorizedClientManager authorizedClientManager, ClientRegistration clientRegistration) {
        this.authorizedClientManager = authorizedClientManager;
        this.clientRegistration = clientRegistration;
    }

    @Override
    public void initialize(ClientHttpRequest request) {
        final String clientRegistrationId = this.clientRegistration.getRegistrationId();
        final OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(clientRegistrationId)
                .principal(this.clientRegistration.getClientId())
                .build();
        final OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null) {
            throw new IllegalStateException("client credentials flow on " + clientRegistrationId + " failed, client is null");
        }
        request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
    }
}
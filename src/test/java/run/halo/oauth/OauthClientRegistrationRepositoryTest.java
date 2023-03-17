package run.halo.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.AuthProvider;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * @author guqing
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class OauthClientRegistrationRepositoryTest {

    @Mock
    private ReactiveExtensionClient client;

    @InjectMocks
    private OauthClientRegistrationRepository repository;

    @Test
    void findByRegistrationId_withValidId_returnsClientRegistration() {
        AuthProvider authProvider = new AuthProvider();
        authProvider.setMetadata(new Metadata());
        authProvider.getMetadata().setName("github");
        authProvider.setSpec(new AuthProvider.AuthProviderSpec());
        authProvider.getSpec().setDisplayName("GitHub");
        authProvider.getSpec().setEnabled(true);
        authProvider.getSpec().setAuthenticationUrl("/oauth2/authorization/github");
        authProvider.getSpec().setSettingRef(new AuthProvider.SettingRef());
        authProvider.getSpec().getSettingRef().setName("oauth-github-setting");
        authProvider.getSpec().getSettingRef().setGroup("github");
        authProvider.getSpec().setConfigMapRef(new AuthProvider.ConfigMapRef());
        authProvider.getSpec().getConfigMapRef().setName("oauth-github-config");

        authProvider.getSpec().setClientRegistration(new AuthProvider.ClientRegistration());
        authProvider.getSpec().getClientRegistration().setAuthorizationUri("fake-uri");
        authProvider.getSpec().getClientRegistration().setTokenUri("fake-token-uri");

        when(client.fetch(eq(AuthProvider.class), eq("github")))
            .thenReturn(Mono.just(authProvider));

        ConfigMap configMap = new ConfigMap();
        configMap.setData(Map.of("github",
            "{\"clientId\":\"my-client-id\",\"clientSecret\":\"my-client-secret\"}"));
        when(client.fetch(eq(ConfigMap.class), eq("oauth-github-config")))
            .thenReturn(Mono.just(configMap));

        StepVerifier.create(repository.findByRegistrationId("github"))
            .assertNext(clientRegistration -> {
                assertThat(clientRegistration.getRegistrationId()).isEqualTo("github");
                assertThat(clientRegistration.getClientId()).isEqualTo("my-client-id");
                assertThat(clientRegistration.getClientSecret()).isEqualTo("my-client-secret");
            })
            .expectComplete()
            .verify();
    }

    @Test
    void findByRegistrationId_withUnsupportedProvider_throwsProviderNotFoundException() {
        when(client.fetch(eq(AuthProvider.class), eq("unsupported-provider")))
            .thenReturn(Mono.empty());
        assertThatThrownBy(() -> repository.findByRegistrationId("unsupported-provider").block())
            .isInstanceOf(ProviderNotFoundException.class)
            .hasMessage("Unsupported OAuth2 provider: unsupported-provider");
    }
}

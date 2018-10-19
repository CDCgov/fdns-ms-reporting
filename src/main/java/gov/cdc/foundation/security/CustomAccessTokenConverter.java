package gov.cdc.foundation.security;

import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;

public class CustomAccessTokenConverter extends DefaultAccessTokenConverter {

	public CustomAccessTokenConverter() {
		setUserTokenConverter(new CustomUserAuthenticationConverter());
	}
}

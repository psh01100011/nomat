package com.dogdog.nomat.global.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        ClientIpProperties properties = new ClientIpProperties();
        properties.setTrustedProxyCidrs(List.of("10.0.0.0/8", "2001:db8:abcd::/48"));
        resolver = new ClientIpResolver(properties);
    }

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        MockHttpServletRequest request = request("203.0.113.10", "198.51.100.20");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void resolvesClientAppendedByAlbAfterSpoofedValue() {
        MockHttpServletRequest request = request(
                "10.0.1.25",
                "192.0.2.99, 198.51.100.20"
        );

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void skipsMultipleTrustedProxyHopsFromRightToLeft() {
        MockHttpServletRequest request = request(
                "10.0.1.25",
                "198.51.100.20, 2001:db8:abcd::10, 10.2.3.4"
        );

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void fallsBackToPeerWhenTrustedProxySendsMalformedHeader() {
        MockHttpServletRequest request = request("10.0.1.25", "198.51.100.20:invalid-port");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.1.25");
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader(ClientIpResolver.FORWARDED_FOR_HEADER, forwardedFor);
        return request;
    }
}

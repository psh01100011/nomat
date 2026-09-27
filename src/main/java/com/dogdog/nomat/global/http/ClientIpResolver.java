package com.dogdog.nomat.global.http;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String UNKNOWN_IP = "unknown";

    private final List<CidrBlock> trustedProxyCidrs;

    public ClientIpResolver(ClientIpProperties properties) {
        this.trustedProxyCidrs = properties.getTrustedProxyCidrs().stream()
                .map(CidrBlock::parse)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        InetAddress peerAddress = parseIpLiteral(request.getRemoteAddr());
        if (peerAddress == null) {
            return UNKNOWN_IP;
        }

        String peerIp = peerAddress.getHostAddress();
        if (!isTrustedProxy(peerAddress)) {
            return peerIp;
        }

        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (!StringUtils.hasText(forwardedFor)) {
            return peerIp;
        }

        List<InetAddress> chain = parseForwardedFor(forwardedFor);
        if (chain == null || chain.isEmpty()) {
            return peerIp;
        }

        for (int index = chain.size() - 1; index >= 0; index--) {
            InetAddress candidate = chain.get(index);
            if (!isTrustedProxy(candidate)) {
                return candidate.getHostAddress();
            }
        }
        return chain.getFirst().getHostAddress();
    }

    private List<InetAddress> parseForwardedFor(String headerValue) {
        List<InetAddress> addresses = new ArrayList<>();
        for (String token : headerValue.split(",")) {
            InetAddress address = parseIpLiteral(token);
            if (address == null) {
                return null;
            }
            addresses.add(address);
        }
        return addresses;
    }

    private boolean isTrustedProxy(InetAddress address) {
        return trustedProxyCidrs.stream().anyMatch(cidr -> cidr.contains(address));
    }

    private static InetAddress parseIpLiteral(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }

        String value = stripPort(rawValue.trim());
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.indexOf(':') < 0 && !isValidIpv4(value)) {
            return null;
        }
        if (value.indexOf(':') >= 0 && !value.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }

        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static String stripPort(String value) {
        if (value.startsWith("[")) {
            int closingBracket = value.indexOf(']');
            if (closingBracket <= 0 || !isValidPortSuffix(value.substring(closingBracket + 1))) {
                return null;
            }
            return value.substring(1, closingBracket);
        }
        int firstColon = value.indexOf(':');
        if (firstColon > 0 && firstColon == value.lastIndexOf(':') && value.indexOf('.') > 0) {
            if (!isValidPortSuffix(value.substring(firstColon))) {
                return null;
            }
            return value.substring(0, firstColon);
        }
        return value;
    }

    private static boolean isValidPortSuffix(String suffix) {
        if (suffix.isEmpty()) {
            return true;
        }
        if (!suffix.matches(":\\d{1,5}")) {
            return false;
        }
        return Integer.parseInt(suffix.substring(1)) <= 65535;
    }

    private static boolean isValidIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            try {
                int numericOctet = Integer.parseInt(octet);
                if (octet.isEmpty() || numericOctet < 0 || numericOctet > 255) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    private record CidrBlock(byte[] networkAddress, int prefixLength) {

        private static CidrBlock parse(String value) {
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("Trusted proxy CIDR must not be blank");
            }

            String[] parts = value.trim().split("/", -1);
            InetAddress address = parseIpLiteral(parts[0]);
            if (address == null || parts.length > 2) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + value);
            }

            int maxPrefixLength = address.getAddress().length * Byte.SIZE;
            int prefixLength = maxPrefixLength;
            if (parts.length == 2) {
                try {
                    prefixLength = Integer.parseInt(parts[1]);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + value, exception);
                }
            }
            if (prefixLength < 0 || prefixLength > maxPrefixLength) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + value);
            }

            byte[] networkAddress = address.getAddress().clone();
            maskHostBits(networkAddress, prefixLength);
            return new CidrBlock(networkAddress, prefixLength);
        }

        private boolean contains(InetAddress candidate) {
            byte[] candidateAddress = candidate.getAddress();
            if (candidateAddress.length != networkAddress.length) {
                return false;
            }
            byte[] maskedCandidate = candidateAddress.clone();
            maskHostBits(maskedCandidate, prefixLength);
            return Arrays.equals(networkAddress, maskedCandidate);
        }

        private static void maskHostBits(byte[] address, int prefixLength) {
            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            if (remainingBits > 0) {
                int mask = 0xFF << (Byte.SIZE - remainingBits);
                address[fullBytes] = (byte) (address[fullBytes] & mask);
                fullBytes++;
            }
            Arrays.fill(address, fullBytes, address.length, (byte) 0);
        }
    }
}

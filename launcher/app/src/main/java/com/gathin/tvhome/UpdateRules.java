package com.gathin.tvhome;

/** Only known HTTPS download endpoints and complete SHA-256 digests are accepted. */
final class UpdateRules {
    private UpdateRules() { }
    static boolean valid(String origin,String url,String sha,long version) {
        return ((origin+"/tv.apk").equals(url) || (origin+"/tv-fireos6.apk").equals(url))
                && sha!=null && sha.matches("[a-fA-F0-9]{64}") && version>0 && version<=Integer.MAX_VALUE;
    }
    static boolean newer(long available,long installed){return available>installed;}
}

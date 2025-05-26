package com.aratiri.aratiri.service;

public interface LnurlService {
    Object getLnurlMetadata(String alias);

    Object lnurlCallback(String alias, long amount, String comment);
}

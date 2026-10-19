package com.chanakanlabs.theparlor.account.internal.domain;

import java.io.Serializable;

public record AccountIdentityId(String issuer, String subject) implements Serializable {}

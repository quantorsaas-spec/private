package com.quantor.domain.trading;

import com.quantor.domain.DomainException;

public class SessionAlreadyRunningException extends DomainException {
    public SessionAlreadyRunningException(String message) { super(message); }
}

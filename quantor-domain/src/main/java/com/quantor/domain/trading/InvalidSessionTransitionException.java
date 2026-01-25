package com.quantor.domain.trading;

import com.quantor.domain.DomainException;

public class InvalidSessionTransitionException extends DomainException {
    public InvalidSessionTransitionException(String message) { super(message); }
}

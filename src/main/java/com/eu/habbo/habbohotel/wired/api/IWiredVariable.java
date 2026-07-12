package com.eu.habbo.habbohotel.wired.api;

import com.eu.habbo.habbohotel.wired.WiredVariablePersistence;
import com.eu.habbo.habbohotel.wired.WiredVariableType;

public interface IWiredVariable {
    WiredVariableType getType();

    String getVariableName();

    WiredVariablePersistence getPersistence();

    long getValue();

    void setValue(long value);
}

package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.wired.WiredVariableType;

/** Versioned saved operand for one stable array field ID in Modify Array. */
public final class WiredArrayFieldInput {
    public static final int SET_VALUE = 0;
    public static final int FROM_VARIABLE = 1;

    public int mode = SET_VALUE;
    public String value = "0";
    public int variableType = WiredVariableType.GLOBAL.code;
    public String variable = "";
    public int variableSource = WiredVariableType.GLOBAL.code;

    public WiredArrayFieldInput() {
    }

    public boolean hasValidMode() {
        return this.mode == SET_VALUE || this.mode == FROM_VARIABLE;
    }
}

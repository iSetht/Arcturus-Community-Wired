package com.eu.habbo.habbohotel.wired.api;

import com.eu.habbo.habbohotel.wired.core.WiredContext;

public interface WiredTextPlaceholderProvider {
    String getPlaceholderName();

    String resolvePlaceholder(WiredContext ctx);
}

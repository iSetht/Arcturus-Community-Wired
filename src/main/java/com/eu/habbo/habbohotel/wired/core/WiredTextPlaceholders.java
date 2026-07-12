package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.wired.api.WiredTextPlaceholderProvider;

public final class WiredTextPlaceholders {
    private WiredTextPlaceholders() {
    }

    public static String resolve(WiredContext ctx, String text) {
        if (ctx == null || ctx.stack() == null || text == null || text.isEmpty() || !text.contains("$(")) {
            return text;
        }

        String resolved = text;
        for (InteractionWiredExtra extra : ctx.stack().extras()) {
            if (!(extra instanceof WiredTextPlaceholderProvider)) {
                continue;
            }

            WiredTextPlaceholderProvider provider = (WiredTextPlaceholderProvider) extra;
            String placeholderName = provider.getPlaceholderName();
            if (placeholderName == null || placeholderName.isEmpty()) {
                continue;
            }

            String token = "$(" + placeholderName + ")";
            if (resolved.contains(token)) {
                resolved = resolved.replace(token, provider.resolvePlaceholder(ctx));
            }
        }

        return resolved;
    }
}

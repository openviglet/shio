package com.viglet.shio.plugin;

import com.viglet.shio.exchange.post.ShPostExchange;

public interface ShImporterPlugin {
	ShPostExchange process(ShPostExchange shPostExchange);
}

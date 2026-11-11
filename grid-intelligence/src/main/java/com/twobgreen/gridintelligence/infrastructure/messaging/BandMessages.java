package com.twobgreen.gridintelligence.infrastructure.messaging;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import com.twobgreen.gridintelligence.domain.BandChange;

/**
 * JSON of the {@code grid.band-changed} event (contract K1, section 7). Same conventions as the snapshot topics:
 * UTC {@code observedAt} with {@code Z}, {@code schemaVersion}, key = scope.
 */
public class BandMessages {

	static final int SCHEMA_VERSION = 1;

	private final JsonMapper mapper = JsonMapper.builder().build();

	public String bandChanged(BandChange change) {
		ObjectNode root = mapper.createObjectNode();
		root.put("schemaVersion", SCHEMA_VERSION);
		root.put("scope", change.scope());
		root.put("band", change.band().name());
		if (change.previousBand() == null) {
			root.putNull("previousBand");
		}
		else {
			root.put("previousBand", change.previousBand().name());
		}
		root.put("observedAt", change.observedAt().toString());
		root.put("co2Intensity", change.co2Intensity());
		return mapper.writeValueAsString(root);
	}
}

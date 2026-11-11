package com.twobgreen.platform.delivery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import com.twobgreen.platform.TestcontainersConfiguration;
import com.twobgreen.platform.delivery.InAppNotifications;
import com.twobgreen.platform.delivery.application.NotificationBox;
import com.twobgreen.platform.delivery.domain.Notification;
import com.twobgreen.platform.delivery.domain.NotificationRepository;
import com.twobgreen.platform.delivery.domain.NotificationType;

/** The notification box as the frontend sees it, through the real security chain and the real database. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTests {

	private static final String BOX = "/api/v1/notifications";

	private static final Instant T0 = Instant.parse("2026-11-10T12:00:00Z");

	private final String alice = UUID.randomUUID().toString();

	private final String bob = UUID.randomUUID().toString();

	@Autowired
	MockMvc mvc;

	@Autowired
	JwtAuthenticationConverter converter;

	@Autowired
	InAppNotifications channel;

	@Autowired
	NotificationRepository notifications;

	@Test
	void withoutTokenIsUnauthorized() throws Exception {
		mvc.perform(get(BOX)).andExpect(status().isUnauthorized());
		mvc.perform(post(BOX + "/read-all")).andExpect(status().isUnauthorized());
	}

	@Test
	void devopsHasNoBoxButCitizenAndOfficialDo() throws Exception {
		mvc.perform(get(BOX).with(token(alice, "devops"))).andExpect(status().isForbidden());
		mvc.perform(get(BOX).with(token(alice, "citizen"))).andExpect(status().isOk());
		mvc.perform(get(BOX).with(token(alice, "regional_official"))).andExpect(status().isOk());
	}

	@Test
	void boxShowsWhatTheChannelStoredNewestFirst() throws Exception {
		long older = store(alice, "Réseau propre : 28", T0);
		long newer = store(alice, "Réseau propre : 29", T0.plusSeconds(900));

		mvc.perform(get(BOX).with(citizen(alice)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(2))
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.items[0].id").value(newer))
				.andExpect(jsonPath("$.items[0].type").value("CLEAN_GRID"))
				.andExpect(jsonPath("$.items[0].message").value("Réseau propre : 29"))
				.andExpect(jsonPath("$.items[0].createdAt").value(T0.plusSeconds(900).toString()))
				.andExpect(jsonPath("$.items[0].readAt").value((Object) null))
				.andExpect(jsonPath("$.items[0].userId").doesNotExist())
				.andExpect(jsonPath("$.items[1].id").value(older));
	}

	@Test
	void whatTheEngineSendsThroughTheChannelArrivesInTheBox() throws Exception {
		channel.cleanGrid(alice, 7, "Réseau propre : 28 gCO₂eq/kWh le 10/11 à 13:30, sous votre seuil de 30.");

		mvc.perform(get(BOX).with(citizen(alice)))
				.andExpect(jsonPath("$.unreadCount").value(1))
				.andExpect(jsonPath("$.items[0].message")
						.value("Réseau propre : 28 gCO₂eq/kWh le 10/11 à 13:30, sous votre seuil de 30."));
	}

	@Test
	void markingAsReadLowersTheBadgeAndKeepsTheFirstDate() throws Exception {
		long first = store(alice, "un", T0);
		store(alice, "deux", T0.plusSeconds(900));

		mvc.perform(post(BOX + "/" + first + "/read").with(citizen(alice))).andExpect(status().isNoContent());
		Instant readAt = notifications.findById(first).orElseThrow().readAt();
		assertThat(readAt).isNotNull();

		// Twice is not an error and changes nothing
		mvc.perform(post(BOX + "/" + first + "/read").with(citizen(alice))).andExpect(status().isNoContent());
		assertThat(notifications.findById(first).orElseThrow().readAt()).isEqualTo(readAt);

		mvc.perform(get(BOX).with(citizen(alice)))
				.andExpect(jsonPath("$.unreadCount").value(1))
				.andExpect(jsonPath("$.items[1].readAt").value(readAt.toString()));
	}

	@Test
	void markingAllAsReadEmptiesTheBadgeOfThatUserOnly() throws Exception {
		store(alice, "un", T0);
		store(alice, "deux", T0.plusSeconds(900));
		long ofBob = store(bob, "à Bob", T0);

		mvc.perform(post(BOX + "/read-all").with(citizen(alice))).andExpect(status().isNoContent());

		mvc.perform(get(BOX).with(citizen(alice))).andExpect(jsonPath("$.unreadCount").value(0));
		assertThat(notifications.findById(ofBob).orElseThrow().readAt()).isNull();
	}

	@Test
	void userCanNeitherReadNorMarkTheNotificationOfAnother() throws Exception {
		long ofAlice = store(alice, "à Alice", T0);

		mvc.perform(get(BOX).with(citizen(bob)))
				.andExpect(jsonPath("$.unreadCount").value(0))
				.andExpect(jsonPath("$.items", hasSize(0)));
		mvc.perform(post(BOX + "/" + ofAlice + "/read").with(citizen(bob)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

		assertThat(notifications.findById(ofAlice).orElseThrow().readAt()).isNull();
	}

	@Test
	void boxShowsTheNewestOnlyButCountsEveryUnreadOne() throws Exception {
		for (int i = 0; i < NotificationBox.MAX_SHOWN + 5; i++) {
			store(alice, "n" + i, T0.plusSeconds(i));
		}

		mvc.perform(get(BOX).with(citizen(alice)))
				.andExpect(jsonPath("$.unreadCount").value(NotificationBox.MAX_SHOWN + 5))
				.andExpect(jsonPath("$.items", hasSize(NotificationBox.MAX_SHOWN)))
				.andExpect(jsonPath("$.items[0].message").value("n" + (NotificationBox.MAX_SHOWN + 4)));
	}

	private long store(String user, String message, Instant createdAt) {
		return notifications.save(new Notification(null, user, NotificationType.CLEAN_GRID, 1, message, createdAt,
				null)).id();
	}

	private JwtRequestPostProcessor citizen(String user) {
		return token(user, "citizen");
	}

	private JwtRequestPostProcessor token(String user, String role) {
		return jwt().jwt(j -> j.subject(user).claim("realm_access", Map.of("roles", List.of(role))))
				.authorities(token -> converter.convert(token).getAuthorities());
	}
}

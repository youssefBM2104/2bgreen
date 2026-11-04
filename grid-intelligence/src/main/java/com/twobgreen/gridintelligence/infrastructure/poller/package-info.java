/**
 * The poller (D6), only started with the {@code poller} profile: schedules {@code PollNationalReadings} and turns its
 * failures into logs, so a broken ODRÉ or Kafka never stops the application. Scheduling and wiring only.
 */
package com.twobgreen.gridintelligence.infrastructure.poller;

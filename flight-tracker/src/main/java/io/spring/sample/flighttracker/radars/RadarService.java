package io.spring.sample.flighttracker.radars;

import java.util.List;

import org.springframework.core.env.Environment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.MediaType;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Component;

@Component
public class RadarService {

	private final Mono<RSocketRequester> requesterMono;

	public RadarService(RSocketRequester.Builder builder, Environment env) {
		String host = env.getProperty("radar-service.host", "localhost");
		int port = env.getProperty("radar-service.port", Integer.class, 9898);
		this.requesterMono = builder
				.dataMimeType(MediaType.APPLICATION_CBOR)
				.connectTcp(host, port).retry(5).cache();
	}

	public Mono<AirportLocation> findRadar(String type, String code) {
		return this.requesterMono.flatMap(req ->
				req.route("find.radar.{type}.{code}", type, code)
						.retrieveMono(AirportLocation.class));
	}

	public Flux<AirportLocation> findRadars(ViewBox box, int maxCount) {
		return this.requesterMono
				.flatMapMany(req ->
						req.route("locate.radars.within")
								.data(box)
								.retrieveFlux(AirportLocation.class))
				.take(maxCount);
	}

	public Flux<AircraftSignal> streamAircraftSignals(List<Radar> radars) {
		return this.requesterMono.flatMapMany(req ->
				Flux.fromIterable(radars).flatMap(radar ->
						req.route("listen.radar.{type}.{code}", radar.getType(), radar.getCode())
								.retrieveFlux(AircraftSignal.class)));
	}
}

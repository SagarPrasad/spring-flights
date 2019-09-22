## Spring Flights Application

This is a demo application showcasing RSocket support in Spring.

This application is made of 3 modules:

* `radar-collector`, an app that provides information about radars and the aircraft signals they collect.
* `flight-tracker` and `flight-client`, an app that displays an interactive map with radars and aircrafts.

### Running the applications

First, run the collector application:

```
$ ./gradlew :radar-collector:build
$ java -jar radar-collector/build/libs/radar-collector-0.0.1-SNAPSHOT.jar
```

Then, run the tracker web application:
```
$ ./gradlew :flight-tracker:build
$ java -jar flight-tracker/build/libs/flight-tracker-0.0.1-SNAPSHOT.jar
```

The tracker application is available at `http://localhost:8080/index.html`

### Radar Collector

This application is providing information about radars (actually populated from list of airports):
their IATA code, location and aircraft signals recorded. The aircraft signals are randomly
generated and the list of radars is inserted in a MongoDB database.

The application starts an RSocket server with TCP transport, at `localhost:9898`.

You can get a list of airports located inside specific coordinates,
using the [rsocket-cli](https://github.com/rsocket/rsocket-cli):

```
rsocket-cli --stream --metadataFormat=message/x.rsocket.routing.v0 -m=locate.radars.within --dataFormat=json \
-i='{"first":{"lng": 3.878915, "lat": 46.409025}, "second": {"lng": 6.714843, "lat": 44.365644}}' tcp://localhost:9898/
```

You can also get a stream of aircraft locations for a given radar, with:

```
rsocket-cli --stream --metadataFormat=message/x.rsocket.routing.v0 -m=listen.radar.LYS --dataFormat=json -i "" tcp://localhost:9898/
```


### Flight Tracker

This application displays an interactive map showing radars - it is also concatenating
the streams of aircraft signals for the radars displayed on screen.

The application starts a WebFlux server at `localhost:8080`, with an RSocket over websocket endpoint on `/rsocket`.
The `flight-client` module builds the JavaScript client using [Leaflet](https://leafletjs.com/) and the the websocket client
from [rsocket-js](https://github.com/rsocket/rsocket-js/).

The browser will first locate all radars in the current view box; you can do the same on the CLI with:

```
rsocket-cli --stream --metadataFormat=message/x.rsocket.routing.v0 -m=locate.radars.within --dataFormat=json \
-i='{"viewBox": {"first":{"lng": 3.878915, "lat": 46.409025}, "second": {"lng": 6.714843, "lat": 44.365644}}, "maxRadars": 10}' ws://localhost:8080/rsocket
```

Once all the radars are retrieved, we can ask a merged stream of all aircrafts for those radars to the server.

```
rsocket-cli --stream --metadataFormat=message/x.rsocket.routing.v0 -m=locate.aircrafts.for --dataFormat=json \
-i='[{"iata":"LYS"}, {"iata":"CVF"}, {"iata":"NCY"}]' ws://localhost:8080/rsocket
```

The browser will perform such a request and update the aircrafts positions live.

The Leaflet map has a small number input (bottom left) which controls the reactive streams demand from the client.
Decreasing it significantly should make the server send less updates to the map. Increasing it back should
catch up with the updates.

Also, once the RSocket client is connected to the server, a bi-directionnal connection is established:
they're now both able to send requests (being a requester) and respond to those (being a responder).
Here, this demo shows how the JavaScript client can respond to requests sent by the server.

Sending the following request to the web server will make it send requests to all connected clients
to let them know that they should change their location to the selected radar:

```
curl -X POST localhost:8080/location/CDG
```

### Deploy to Cloud Foundry

Use the `cf` CLI to target your instance of Cloud Foundry.

Open the file called `manifest-vars.yml` and edit the domains as appropriate for your Cloud Foundry foundation. You can run the command `cf domains` to see the domains available to you.

Next, you need to provide flight-client with the correct URL to flight-tracker on Cloud Foundry (otherwise, the URL defaults to `ws://localhost:8080/rsocket`).
Run the following following script and enter the appropriate URL when prompted. 
You can copy/paste the sample value provided by the script - just change the domain as necessary.
```
$ ./config-client.sh
```

Build the radar-collector and flight-client apps.
Please note that even if you built the projects earlier in this exercise, you must re-build flight-client to incorporate the change in the last step.
```
./gradlew :radar-collector:build
./gradlew :flight-tracker:build
```

Push the apps to Cloud Foundry, providing `manifest-vars.yml` as input:
```
cf push --vars-file manifest-vars.yml
```

The tracker application is available at `http://flight-tracker.<YOUR-DOMAIN>/index.html`

### Run locally using Spring Cloud Gateway RSocket

First, run the gateway application:
```
$ ./gradlew :radar-gateway:build
$ java -jar radar-gateway/build/libs/radar-gateway-0.0.1-SNAPSHOT.jar
```

Then, run three instances of the collector application using different profiles: 
- To control which subset of [airports](radar-collector/src/main/resources/airports.json) each instance loads, we use profiles `civilian`, `military1`, and `military2`.
- To configure the connection of the collector application to the gateway application, we add the `gateway` profile.
This enables the collector to create a connection with the gateway, rather than waiting for flight-tracker to create direct connections to collector on a given port.

You can review the corresponding properties files in the radar-collector application for the profile-based configuration.
```
$ # Build radar-collector
$ ./gradlew :radar-collector:build

$ # In one terminal run:
$ java -jar radar-collector/build/libs/radar-collector-0.0.1-SNAPSHOT.jar --spring.profiles.active=civilian,gateway

$ # In a second terminal run:
$ java -jar radar-collector/build/libs/radar-collector-0.0.1-SNAPSHOT.jar --spring.profiles.active=military1,gateway

$ # In a third terminal run:
$ java -jar radar-collector/build/libs/radar-collector-0.0.1-SNAPSHOT.jar --spring.profiles.active=military1,gateway
```

TO-DO:
- Disable TCP listening port when collector is in gateway client mode
- Disable gateway client auto-configuration when collector is not in gateway client mode
- Update request code in collector to include the necessary metadata for gateway
- Update flight-tracker to be a client to gateway
- Deploy with gateway to Cloud Foundry
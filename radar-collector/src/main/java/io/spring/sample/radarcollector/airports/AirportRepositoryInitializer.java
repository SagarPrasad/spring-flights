package io.spring.sample.radarcollector.airports;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Box;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.query.Query.query;

@Component
public class AirportRepositoryInitializer {

	private final Logger logger = LoggerFactory.getLogger(AirportRepositoryInitializer.class);

	private final MongoTemplate template;

	private final ObjectMapper objectMapper;

	public AirportRepositoryInitializer(MongoTemplate template, ObjectMapper objectMapper) {
		this.template = template;
		this.objectMapper = objectMapper;
	}

	@Value("${airports.filter.id:}")
	String idFilter;

	@Value("${airports.filter.type:}")
	String typeFilter;

	@PostConstruct
	public void initializeAirportsDatabase() throws IOException {
		if (!this.template.collectionExists(Airport.class)) {
			this.template.createCollection(Airport.class);
			IndexOperations indexOps = this.template.indexOps(Airport.class);
			indexOps.ensureIndex(new GeospatialIndex("location").typed(GeoSpatialIndexType.GEO_2DSPHERE));
		}
		if (this.template.count(query(Criteria.where("code").exists(true)), Airport.class) == 0) {
			ClassPathResource airportsResource = new ClassPathResource("airports.json");
			AirportsFileEntry[] fileEntries = this.objectMapper
					.readValue(airportsResource.getInputStream(), AirportsFileEntry[].class);

			List<Airport> airports = Arrays.stream(fileEntries)
					.filter(entry -> idFilter.length() == 0 || entry.getId().equalsIgnoreCase(idFilter))
					.filter(entry -> typeFilter.length() == 0 || entry.getType().equalsIgnoreCase(typeFilter))
					.map(entry -> new Airport(
							entry.getId(), AirportType.valueOf(entry.getType()), entry.getCode(),
							entry.getName(), new GeoJsonPoint(entry.getLon(), entry.getLat())))
					.collect(Collectors.toList());

			Collection<Airport> inserted = this.template.insert(airports, Airport.class);
			logger.info("Added {} airports to the database [filters: id={}, type={}]", inserted.size(), idFilter, typeFilter);
		}
	}
}

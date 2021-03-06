package com.voidaspect.public24.service.p24;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * REST Client for Privat24 API
 *
 * @author mikhail.h
 */
@Service
@Slf4j
public final class Privat24Service implements Privat24 {

    private static final Pattern SPACES_PATTERN = Pattern.compile(" +");

    /**
     * Http client instance
     */
    private final RestTemplate restTemplate;

    /**
     * @see DateTimeFormatter
     */
    private final DateTimeFormatter dateTimeFormatter;

    /**
     * @see Privat24Properties
     */
    private final Privat24Properties privat24Properties;

    /**
     * Cache for archive requests
     */
    private final Map<LocalDate, ExchangeRateHistory> exchangeHistoryCache =
            new HashMap<>();

    @Autowired
    public Privat24Service(RestTemplateBuilder restTemplateBuilder,
                           DateTimeFormatter dateTimeFormatter,
                           Privat24Properties privat24Properties) {
        this.restTemplate = restTemplateBuilder.build();
        this.dateTimeFormatter = dateTimeFormatter;
        this.privat24Properties = privat24Properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExchangeRateHistory getExchangeRatesForDate(LocalDate date) {
        return exchangeHistoryCache.computeIfAbsent(date, d -> {
            val uri = getUriComponentsBuilder()
                    .path("/exchange_rates")
                    .queryParam("date", date.format(dateTimeFormatter))
                    .build().toUri();
            log.debug("GET Request to p24 api: {}", uri);
            return restTemplate.getForObject(uri, ExchangeRateHistory.class);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExchangeRateHistory getExchangeRatesForDate(LocalDate date, Currency currency) {
        ExchangeRateHistory exchangeRatesForDate = getExchangeRatesForDate(date);

        List<ExchangeRateHistoryCurrency> ccyHistory = exchangeRatesForDate.getExchangeRates().stream()
                .filter(exchangeRate -> exchangeRate.getCurrency().equals(currency.name()))
                .collect(Collectors.toList());

        return new ExchangeRateHistory(
                exchangeRatesForDate.getDate(),
                exchangeRatesForDate.getBank(),
                exchangeRatesForDate.getBaseCurrency(),
                exchangeRatesForDate.getBaseCurrencyLit(),
                ccyHistory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CurrentExchangeRate> getCurrentExchangeRates(ExchangeRateType exchangeRateType) {
        val uri = getUriComponentsBuilder()
                .path("/pubinfo")
                .queryParam("exchange")
                .queryParam("coursid", exchangeRateType.getId())
                .build().toUri();
        log.debug("GET Request to p24 api: {}", uri);
        CurrentExchangeRate[] rates = restTemplate.getForObject(uri, CurrentExchangeRate[].class);
        return Arrays.asList(rates);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CurrentExchangeRate> getCurrentExchangeRates(ExchangeRateType exchangeRateType, Currency currency) {
        return getCurrentExchangeRates(exchangeRateType).stream()
                .filter(exchangeRate -> exchangeRate.getCurrency().equals(currency.name()))
                .findAny();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Infrastructure getInfrastructureLocations(DeviceType deviceType, String cityName) {
        return getInfrastructureLocations(deviceType, cityName, "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Infrastructure getInfrastructureLocations(DeviceType deviceType, String cityName, String address) {
        val infrastructureRequestBuilder = getUriComponentsBuilder()
                .path("/infrastructure")
                .queryParam(deviceType.name().toLowerCase());
        val uri = populateLocationQueryParams(infrastructureRequestBuilder, cityName, address);
        log.debug("GET Request to p24 api: {}", uri);
        return restTemplate.getForObject(uri, Infrastructure.class);
    }

    private URI populateLocationQueryParams(UriComponentsBuilder infrastructureRequestBuilder, String cityName, String address) {
        return infrastructureRequestBuilder
                .queryParam("city", trimQueryParam(cityName))
                .queryParam("address", trimQueryParam(address))
                .build().encode().toUri();
    }

    private String trimQueryParam(String query) {
        return SPACES_PATTERN.matcher(query.trim()).replaceAll(" ");
    }

    /**
     * Preconfigures URI for Privat24 requests
     *
     * @return partially populated builder
     */
    private UriComponentsBuilder getUriComponentsBuilder() {
        return UriComponentsBuilder.fromHttpUrl(privat24Properties.getUrl())
                .queryParam(privat24Properties.getFormat());
    }
}

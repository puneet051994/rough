package com.rbs.cdna.configuration.rest;

import com.rbs.dws.transport.config.HttpConfig;
import com.rbs.dws.transport.config.RestConfig;
import com.rbs.dws.transport.rest.CorrelationIdClientHttpRequestFactory;
import com.rbs.dws.transport.rest.ResponseServerErrorHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestTemplate;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;


import java.time.Duration;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

@Configuration("ADRestTemplateFactory")
public class RestTemplateFactory {
	@Autowired
	private ApplicationContext appContext;
	@Autowired
	HttpConfig httpConfig;

	public RestTemplate getRestTemplateByActiveProfile(String qualifier) {
		RestTemplate restTemplate = (RestTemplate) appContext.getBean(qualifier);
		if (StringUtils.equalsIgnoreCase("restTemplate", qualifier)) {
			RequestConfig requestConfig = RequestConfig.custom()
					.setConnectionKeepAlive(Timeout.of(Duration.ofMillis(httpConfig.getConnectionTimeout())))
					.setConnectionRequestTimeout(Timeout.of(Duration.ofMillis(httpConfig.getExecutionTimeoutInMilliseconds())))
					.setResponseTimeout(Timeout.of(Duration.ofMillis(httpConfig.getSocketTimeout())))
					.build();
			CloseableHttpClient httpClient = HttpClientBuilder.create()
					//.setConnectionTimeToLive(httpConfig.getExecutionTimeoutInMilliseconds(), TimeUnit.MILLISECONDS)
					.setDefaultRequestConfig(requestConfig).build();
			CorrelationIdClientHttpRequestFactory correlationIdClientHttpRequestFactory = new CorrelationIdClientHttpRequestFactory(httpClient, true);
			restTemplate.setRequestFactory(correlationIdClientHttpRequestFactory);
		}
		return restTemplate;
	}

	@Bean("restTemplateWithoutTrustStore")
	@Lazy
	@Scope(SCOPE_PROTOTYPE)
	public RestTemplate restTemplateWithoutTrustStore(
			RestConfig restConfig,
			ResponseServerErrorHandler responseServerErrorHandler) {
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectionKeepAlive(Timeout.of(Duration.ofMillis(httpConfig.getConnectionTimeout())))
				.setConnectionRequestTimeout(Timeout.of(Duration.ofMillis(httpConfig.getConnectionTimeout())))
				.setResponseTimeout(Timeout.of(Duration.ofMillis(httpConfig.getSocketTimeout())))
				.build();
		CloseableHttpClient httpClientBuilder = HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
		CorrelationIdClientHttpRequestFactory correlationIdClientHttpRequestFactory = new CorrelationIdClientHttpRequestFactory(httpClientBuilder, true);
		RestTemplate restTemplate = new RestTemplate(correlationIdClientHttpRequestFactory);
		if (restConfig.isResponseServerErrorHandler()) {
			restTemplate.setErrorHandler(responseServerErrorHandler);
		}
		return restTemplate;
	}

	@Bean("restTemplateWithMTLS")
    @Lazy
    @Scope(SCOPE_PROTOTYPE)
    public RestTemplate restTemplateWithMTLS(
            RestConfig restConfig,
            ResponseServerErrorHandler responseServerErrorHandler) throws Exception {

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionKeepAlive(Timeout.of(Duration.ofMillis(httpConfig.getConnectionTimeout())))
                .setConnectionRequestTimeout(Timeout.of(Duration.ofMillis(httpConfig.getConnectionTimeout())))
                .setResponseTimeout(Timeout.of(Duration.ofMillis(httpConfig.getSocketTimeout())))
                .build();

        // Load keystore (client certificate) with proper resource management
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream keystoreStream = new FileInputStream(restConfig.getKeystorePath())) {
            keyStore.load(keystoreStream, restConfig.getKeystorePassword().toCharArray());
        }

        // Load truststore (trusted server certificates) with proper resource management
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream truststoreStream = new FileInputStream(restConfig.getTruststorePath())) {
            trustStore.load(truststoreStream, restConfig.getTruststorePassword().toCharArray());
        }

        // Create SSL context with mTLS
        SSLContext sslContext = SSLContexts.custom()
                .loadKeyMaterial(keyStore, restConfig.getKeyPassword().toCharArray())
                .loadTrustMaterial(trustStore, null)
                .build();

        // Create HTTP client with mTLS support
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE) // Use carefully in production
                .build();

        CorrelationIdClientHttpRequestFactory correlationIdClientHttpRequestFactory =
                new CorrelationIdClientHttpRequestFactory(httpClient, true);
        RestTemplate restTemplate = new RestTemplate(correlationIdClientHttpRequestFactory);

        if (restConfig.isResponseServerErrorHandler()) {
            restTemplate.setErrorHandler(responseServerErrorHandler);
        }
        return restTemplate;
    }
}

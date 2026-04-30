package com.igot.cb.elasticsearch.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.igot.cb.util.Constants;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Arrays;

@Configuration
public class EsClientConfig {

    @Value("${elasticsearch.hosts}")
    private String elasticsearchHosts;

    @Value("${elasticsearch.port}")
    private int elasticsearchPort;

    @Value("${elasticsearch.username}")
    private String elasticsearchUsername;

    @Value("${elasticsearch.password}")
    private String elasticsearchPassword;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(elasticsearchUsername, elasticsearchPassword));

        HttpHost[] httpHosts = Arrays.stream(elasticsearchHosts.split(","))
                .map(String::trim)
                .map(host -> new HttpHost(host, elasticsearchPort, Constants.ES_HTTP_SCHEME))
                .toArray(HttpHost[]::new);

        RestClientBuilder builder = RestClient.builder(httpHosts)
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider).addInterceptorLast((HttpResponseInterceptor) (response, context) ->
                        response.addHeader(Constants.ES_PRODUCT_HEADER, Constants.ES_PRODUCT_HEADER_VALUE)))
                .setDefaultHeaders(new org.apache.http.Header[]{
                        new org.apache.http.message.BasicHeader(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON),
                        new org.apache.http.message.BasicHeader(Constants.ES_PRODUCT_HEADER, Constants.ES_PRODUCT_HEADER_VALUE)});
        RestClient restClient = builder.build();
        ElasticsearchTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(elasticsearchTransport);
    }

}

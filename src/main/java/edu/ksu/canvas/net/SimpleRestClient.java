package edu.ksu.canvas.net;

import edu.ksu.canvas.exception.InvalidOauthTokenException;
import edu.ksu.canvas.exception.UnauthorizedException;
import edu.ksu.canvas.oauth.OauthToken;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import javax.validation.constraints.NotNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SimpleRestClient implements RestClient {
    private static final Logger LOG = Logger.getLogger(SimpleRestClient.class);

    @Override
    public Response sendApiGet(@NotNull OauthToken token, @NotNull String url,
                                      int connectTimeout, int readTimeout) throws IOException {
        LOG.debug("Sending GET request to URL: " + url);
        Long beginTime = System.currentTimeMillis();
        Response response = new Response();
        CloseableHttpClient httpClient = createPooledHttpClient(connectTimeout, readTimeout);
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Authorization", "Bearer" + " " + token.getAccessToken());
        CloseableHttpResponse httpResponse = httpClient.execute(httpGet);

        try {
            checkAuthenticationHeaders(httpResponse);

            //deal with the actual content
            BufferedReader in = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));
            String inputLine;
            StringBuffer content = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            response.setContent(content.toString());
            response.setResponseCode(httpResponse.getStatusLine().getStatusCode());
            Long endTime = System.currentTimeMillis();
            LOG.debug("GET call took: " + (endTime - beginTime) + "ms");

            //deal with pagination
            Header linkHeader = httpResponse.getFirstHeader("Link");
            String linkHeaderValue = linkHeader == null ? null : httpResponse.getFirstHeader("Link").getValue();
            if (linkHeaderValue == null) {
                return response;
            }
            List<String> links = Arrays.asList(linkHeaderValue.split(","));
            for (String link : links) {
                if (link.contains("rel=\"next\"")) {
                    LOG.debug("response has more pages");
                    String nextLink = link.substring(1, link.indexOf(';') - 1); //format is <http://.....>; rel="next"
                    response.setNextLink(nextLink);
                }
            }
        }
        finally {
            httpResponse.close();
            httpClient.close();
        }

        return response;
    }

    @Override
    public Response sendJsonPut(OauthToken token, String url, String json, int connectTimeout, int readTimeout) throws IOException {
        return sendJsonPostOrPut(token, url, json, connectTimeout, readTimeout, "PUT");
    }

    @Override
    public Response sendJsonPost(OauthToken token, String url, String json, int connectTimeout, int readTimeout) throws IOException {
        return sendJsonPostOrPut(token, url, json, connectTimeout, readTimeout, "POST");
    }

    // PUT and POST are identical calls except for the header specifying the method
    private Response sendJsonPostOrPut(OauthToken token, String url, String json,
                                        int connectTimeout, int readTimeout, String method) throws IOException {
        LOG.debug("Sending JSON " + method + " to URL: " + url);
        Response response = new Response();

        HttpClient httpClient = createHttpClient(connectTimeout, readTimeout);
        HttpEntityEnclosingRequestBase action;
        if("POST".equals(method)) {
            action = new HttpPost(url);
        } else if("PUT".equals(method)) {
            action = new HttpPut(url);
        } else {
            throw new IllegalArgumentException("Method must be either POST or PUT");
        }
        Long beginTime = System.currentTimeMillis();
        action.setHeader("Authorization", "Bearer" + " " + token.getAccessToken());

        StringEntity requestBody = new StringEntity(json, ContentType.APPLICATION_JSON);
        action.setEntity(requestBody);
        HttpResponse httpResponse = httpClient.execute(action);

        String content = handleResponse(httpResponse, action);

        response.setContent(content);
        response.setResponseCode(httpResponse.getStatusLine().getStatusCode());
        Long endTime = System.currentTimeMillis();
        LOG.debug("POST call took: " + (endTime - beginTime) + "ms");

        return response;
    }

    @Override
    public Response sendApiPost(OauthToken token, String url, Map<String, List<String>> postParameters,
                                       int connectTimeout, int readTimeout) throws InvalidOauthTokenException, IOException {
        LOG.debug("Sending API POST request to URL: " + url);
        Response response = new Response();
        HttpClient httpClient = createHttpClient(connectTimeout, readTimeout);
        Long beginTime = System.currentTimeMillis();
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Authorization", "Bearer" + " " + token.getAccessToken());
        List<NameValuePair> params = convertParameters(postParameters);

        httpPost.setEntity(new UrlEncodedFormEntity(params));
        HttpResponse httpResponse =  httpClient.execute(httpPost);
        String content = handleResponse(httpResponse, httpPost);

        response.setContent(content);
        response.setResponseCode(httpResponse.getStatusLine().getStatusCode());
        Long endTime = System.currentTimeMillis();
        LOG.debug("POST call took: " + (endTime - beginTime) + "ms");
        return response;
    }

    @Override
    public Response sendApiPut(OauthToken token, String url, Map<String, List<String>> putParameters,
                                int connectTimeout, int readTimeout) throws InvalidOauthTokenException, IOException {
        LOG.debug("Sending API PUT request to URL: " + url);
        Response response = new Response();
        HttpClient httpClient = createHttpClient(connectTimeout, readTimeout);
        Long beginTime = System.currentTimeMillis();
        HttpPut httpPut = new HttpPut(url);
        httpPut.setHeader("Authorization", "Bearer" + " " + token.getAccessToken());
        List<NameValuePair> params = convertParameters(putParameters);

        httpPut.setEntity(new UrlEncodedFormEntity(params));
        HttpResponse httpResponse =  httpClient.execute(httpPut);
        String content = handleResponse(httpResponse, httpPut);

        response.setContent(content);
        response.setResponseCode(httpResponse.getStatusLine().getStatusCode());
        Long endTime = System.currentTimeMillis();
        LOG.debug("PUT call took: " + (endTime - beginTime) + "ms");
        return response;
    }


    @Override
    public Response sendApiDelete(OauthToken token, String url, Map<String, List<String>> deleteParameters,
                                       int connectTimeout, int readTimeout) throws InvalidOauthTokenException, IOException {
        LOG.debug("Sending API DELETE request to URL: " + url);
        Response response = new Response();

        Long beginTime = System.currentTimeMillis();
        HttpClient httpClient = createHttpClient(connectTimeout, readTimeout);

        //This class is defined here because we need to be able to add form body elements to a delete request for a few api calls.
        class HttpDeleteWithBody extends HttpPost {
            @Override
            public String getMethod() {
                return "DELETE";
            }
        }

        HttpDeleteWithBody httpDelete = new HttpDeleteWithBody();

        httpDelete.setURI(URI.create(url));
        httpDelete.setHeader("Authorization", "Bearer" + " " + token.getAccessToken());
        List<NameValuePair> params = convertParameters(deleteParameters);

        httpDelete.setEntity(new UrlEncodedFormEntity(params));
        HttpResponse httpResponse = httpClient.execute(httpDelete);

        String content = handleResponse(httpResponse, httpDelete);
        response.setContent(content);
        response.setResponseCode(httpResponse.getStatusLine().getStatusCode());
        Long endTime = System.currentTimeMillis();
        LOG.debug("DELETE call took: " + (endTime - beginTime) + "ms");

        return response;
    }

    private void checkAuthenticationHeaders(HttpResponse httpResponse) {
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        if (statusCode == 401) {
            //If the WWW-Authenticate header is set, it is a token problem.
            //If the header is not present, it is a user permission error.
            //See https://canvas.instructure.com/doc/api/file.oauth.html#storing-access-tokens
            if(httpResponse.containsHeader(HttpHeaders.WWW_AUTHENTICATE)) {
                throw new InvalidOauthTokenException();
            }
            LOG.error("User is not authorized to perform this action");
            throw new UnauthorizedException();
        }
    }

    private String handleResponse(HttpResponse httpResponse, HttpRequestBase request) throws IOException {
        checkAuthenticationHeaders(httpResponse);
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        if(statusCode < 200 || statusCode > 299) {
            LOG.error("HTTP status " + statusCode + " returned from " + request.getURI());
            HttpEntity entity = httpResponse.getEntity();
            if(entity != null) {
                LOG.error("Response from Canvas: " + EntityUtils.toString(entity));
            }
        }
        return new BasicResponseHandler().handleResponse(httpResponse);
    }

    private HttpClient createHttpClient(int connectTimeout, int readTimeout) {
        HttpClient httpClient = new DefaultHttpClient();
        HttpParams params = httpClient.getParams();
        params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, connectTimeout);
        params.setParameter(CoreConnectionPNames.SO_TIMEOUT, readTimeout);
        return httpClient;
    }

    private CloseableHttpClient createPooledHttpClient(int connectTimeout, int readTimeout){
        HttpClientConnectionManager poolingConnManager
                = new PoolingHttpClientConnectionManager();
        CloseableHttpClient client
                = HttpClients.custom().setConnectionManager(poolingConnManager)
                .build();
        return client;
    }

    private static List<NameValuePair> convertParameters(final Map<String, List<String>> parameterMap) {
        final List<NameValuePair> params = new ArrayList<>();

        if (parameterMap == null) {
            return params;
        }

        for (final Map.Entry<String, List<String>> param : parameterMap.entrySet()) {
            final String key = param.getKey();
            for (final String value : param.getValue()) {
                params.add(new BasicNameValuePair(key, value));
                LOG.debug("key "+ key +"\t value : " + value);
            }
        }
        return params;
    }

}

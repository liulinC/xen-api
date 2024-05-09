/*
 * Copyright (c) Cloud Software Group, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   1) Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *
 *   2) Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials
 *      provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.xensource.xenapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xensource.xenapi.Types.BadServerResponse;
import com.xensource.xenapi.Types.XenAPIException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Represents a connection to a XenServer. Creating a new instance of this class initialises a new JsonRpcClient that is
 * then used by all method calls: each method call in xen-api takes a Connection as a parameter, composes a JSON-RPC
 * method call, and dispatches it on the Connection's client via the dispatch method.
 */
public class Connection {

    public final JsonRpcClient client;
    private APIVersion apiVersion;
    /**
     * The opaque reference to the session used by this connection
     */
    private String sessionReference;

    /**
     * Creates a connection to a particular server using a custom implementation of the JsonRpcClient.
     * <p>
     * Note this constructor does NOT call Session.loginWithPassword; the programmer is responsible for calling it,
     * passing the Connection as a parameter. No attempt to connect to the server is made until login is called.
     * <p>
     * When this constructor is used, a call to dispose() will do nothing. The programmer is responsible for manually
     * logging out the Session.
     *
     * @param jsonRpcClient The JsonRpcClient used to connect to the JSON-RPC backed.
     */
    public Connection(JsonRpcClient jsonRpcClient) {
        this.client = jsonRpcClient;
    }

    /**
     * Creates a connection to a particular server using a given url. This object can then be passed
     * in to any other API calls.
     * <p>
     * Note this constructor does NOT call Session.loginWithPassword; the programmer is responsible for calling it,
     * passing the Connection as a parameter. No attempt to connect to the server is made until login is called.
     * <p>
     * When this constructor is used, a call to dispose() will do nothing. The programmer is responsible for manually
     * logging out the Session.
     *
     * @param httpClient     The HttpClient used to make calls, this will be used by the underlying {@link #client} for handling requests
     * @param url            The URL of the server to connect to. Should be of the form http(s)://host-url/jsonrpc or http(s)://host-url.
     * @param requestTimeout The reply timeout for JSON-RPC calls in seconds
     * @deprecated           This constructor is deprecated. To set the {@code requestTimeout} please {@link #setRequestTimeout(int)}. You may also use the {@link com.xensource.xenapi.JsonRpcClient#setRequestTimeout(int)}
     *                       method of this object's {@link #client}. This option is only advisable if you are managing your own {@link com.xensource.xenapi.JsonRpcClient} as the underlying
     *                       {@link #client} for this object.
     */
    @Deprecated
    public Connection(CloseableHttpClient httpClient, URL url, int requestTimeout) {
        this.client = new JsonRpcClient(httpClient, url);
        this.client.setRequestTimeout(requestTimeout);
    }

    /**
     * Creates a connection to a particular server using a given url. This object can then be passed
     * in to any other API calls.
     * <p>
     * Note this constructor does NOT call Session.loginWithPassword; the programmer is responsible for calling it,
     * passing the Connection as a parameter. No attempt to connect to the server is made until login is called.
     * <p>
     * When this constructor is used, a call to dispose() will do nothing. The programmer is responsible for manually
     * logging out the Session.
     * <p>
     * This constructor uses the default values of the reply and connection timeouts for the JSON-RPC calls
     * (600 seconds and 5 seconds respectively).
     *
     * @param url The URL of the server to connect to. Should be of the form http(s)://host-url/jsonrpc or http(s)://host-url.
     */
    public Connection(URL url) {
        this.client = new JsonRpcClient(url);
    }

    /**
     * Creates a connection to a particular server using a given url. This object can then be passed
     * in to any other API calls.
     * <p>
     * Note this constructor does NOT call Session.loginWithPassword; the programmer is responsible for calling it,
     * passing the Connection as a parameter. No attempt to connect to the server is made until login is called.
     * <p>
     * When this constructor is used, a call to dispose() will do nothing. The programmer is responsible for manually
     * logging out the Session.
     *
     * @param url               The URL of the server to connect to. Should be of the form http(s)://host-url/jsonrpc or http(s)://host-url.
     * @param requestTimeout    The reply timeout for JSON-RPC calls in seconds
     * @param connectionTimeout The connection timeout for JSON-RPC calls in seconds
     * @deprecated              This constructor is deprecated. To set {@code requestTimeout} or {@code connectionTimeout} please use {@link #setRequestTimeout(int)} or {@link #setConnectionTimeout(int)} respectively.
     *                          You may also use the {@link com.xensource.xenapi.JsonRpcClient#setRequestTimeout(int)} method of this object's {@link #client}.
     *                          This option is only advisable if you are managing your own {@link com.xensource.xenapi.JsonRpcClient} as the underlying
     *                          {@link #client} for this object.
     */
    @Deprecated
    public Connection(URL url, int requestTimeout, int connectionTimeout) {
        this.client = new JsonRpcClient(url);
        this.client.setRequestTimeout(requestTimeout);
        this.client.setConnectionTimeout(connectionTimeout);
    }

    /**
     * Creates a connection to a particular server using a given url. This object can then be passed
     * in to any other API calls.
     * <p>
     * Note this constructor does NOT call Session.loginWithPassword; the programmer is responsible for calling it,
     * passing the Connection as a parameter. No attempt to connect to the server is made until login is called.
     * <p>
     * When this constructor is used, a call to dispose() will do nothing. The programmer is responsible for manually
     * logging out the Session.
     * <p>
     * This constructor uses the default values of the reply and connection timeouts for the JSON-RPC calls
     * (600 seconds and 5 seconds respectively).
     *
     * @param url              The URL of the server to connect to. Should be of the form http(s)://host-url/jsonrpc or http(s)://host-url.
     * @param sessionReference A reference to a logged-in Session. Any method calls on this
     *                         Connection will use it. This constructor does not call Session.loginWithPassword, and dispose() on the resulting
     *                         Connection object does not call Session.logout. The programmer is responsible for ensuring the Session is logged
     *                         in and out correctly.
     */
    public Connection(URL url, String sessionReference) {
        this.client = new JsonRpcClient(url);
        this.sessionReference = sessionReference;
    }

    /**
     * Creates a connection to a particular server using a given url. This object can then be passed
     * in to any other API calls.
     * <p>
     * Note this constructor does NOT call Session.loginWithPassword; the programmer is responsible for calling it,
     * passing the Connection as a parameter. No attempt to connect to the server is made until login is called.
     * <p>
     * When this constructor is used, a call to dispose() will do nothing. The programmer is responsible for manually
     * logging out the Session.
     *
     * @param url               The URL of the server to connect to. Should be of the form http(s)://host-url/jsonrpc or http(s)://host-url.
     * @param sessionReference  A reference to a logged-in Session. Any method calls on this Connection will use it.
     *                          This constructor does not call Session.loginWithPassword, and dispose() on the resulting
     *                          Connection object does not call Session.logout. The programmer is responsible for
     *                          ensuring the Session is logged in and out correctly.
     * @param requestTimeout    The reply timeout for JSON-RPC calls in seconds
     * @param connectionTimeout The connection timeout for JSON-RPC calls in seconds
     * @deprecated              This constructor is deprecated. To set {@code requestTimeout} or {@code connectionTimeout} please use {@link #setRequestTimeout(int)} or {@link #setConnectionTimeout(int)} respectively.
     *                          You may also use the {@link com.xensource.xenapi.JsonRpcClient#setRequestTimeout(int)} method of this object's {@link #client}.
     *                          This option is only advisable if you are managing your own {@link com.xensource.xenapi.JsonRpcClient} as the underlying
     *                          {@link #client} for this object.
     */
    @Deprecated
    public Connection(URL url, String sessionReference, int requestTimeout, int connectionTimeout) {
        this.client = new JsonRpcClient(url);
        this.client.setRequestTimeout(requestTimeout);
        this.client.setConnectionTimeout(connectionTimeout);
        this.sessionReference = sessionReference;
    }

    /**
     * Set the timeout in seconds for every request made by this object's {@link #client}.
     * If not set the value defaults to {@value JsonRpcClient#DEFAULT_REQUEST_TIMEOUT}.
     * You may also pass your own {@link JsonRpcClient} in the constructor for more control.
     *
     * @param requestTimeout        the timeout value in seconds
     * @throws NullPointerException if the {@link #client} is null
     * @see org.apache.hc.client5.http.config.RequestConfig.Builder#setConnectionRequestTimeout(long, TimeUnit)
     */
    public void setRequestTimeout(int requestTimeout) throws NullPointerException {
        this.client.setRequestTimeout(requestTimeout);
    }

    /**
     * Set the connection timeout in seconds for its {@link #client}'s {@link org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager}.
     * If not set the value defaults to {@value JsonRpcClient#DEFAULT_CONNECTION_TIMEOUT}.
     * You may also pass your own {@link JsonRpcClient} in the constructor for more control.
     *
     * @param connectionTimeout     the client's connection timeout in seconds.
     * @throws NullPointerException if the {@link #client} is null
     * @see org.apache.hc.client5.http.config.ConnectionConfig.Builder#setConnectTimeout(Timeout)
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.client.setConnectionTimeout(connectionTimeout);
    }

    /**
     * Updated when Session.login_with_password() is called.
     */
    public APIVersion getAPIVersion() {
        return apiVersion;
    }

    private void setAPIVersion() throws IOException {
        apiVersion = APIVersion.UNKNOWN;
        try {
            var pools = Pool.getAllRecords(this);
            var pool = pools.values().stream().findFirst();
            if (pool.isPresent()) {
                var host = pool.get().master.getRecord(this);
                apiVersion = APIVersion.fromMajorMinor(host.APIVersionMajor, host.APIVersionMinor);
            }
        } catch (BadServerResponse exn) {
            // ignore, we default to UNKNOWN
        }
    }

    /*
     * Because the binding calls are constructing their own parameter lists, they need to be able to get to
     * the session reference directly. This is all rather ugly and needs redone
     * CA-15447: Changed to public in order to allow easier integration with HTTP-level streaming interface,
     */
    public String getSessionReference() {
        return this.sessionReference;
    }

    /**
     * Send a method call to xapi's backend. You need to provide the type of the data returned by a successful response.
     *
     * @param methodCall            The JSON-RPC xapi method call. e.g.: session.login_with_password
     * @param methodParameters      The methodParameters of the method call
     * @param responseTypeReference The type of the response, wrapped with a TypeReference
     * @param <T>                   The type of the response's payload. For instance, a map of opaque references to VM objects is expected when calling VM.get_all_records
     * @return                      The result of the call with the type specified under T.
     * @throws XenAPIException      if the call failed.
     * @throws IOException          if an I/O error occurs when sending or receiving, includes cases when the request's payload or the response's payload cannot be written or read as valid JSON.
     */
    public <T> T dispatch(String methodCall, Object[] methodParameters, TypeReference<T> responseTypeReference) throws XenAPIException, IOException {
        var result = client.sendRequest(methodCall, methodParameters, responseTypeReference);
        if (result.error != null) {
            throw new XenAPIException(String.valueOf(result.error));
        }

        if (methodCall.equals("session.login_with_password")) {
            var session = ((Session) result.result);
            sessionReference = session.ref;
            setAPIVersion();
        } else if (methodCall.equals("session.slave_local_login_with_password")) {
            var session = ((Session) result.result);
            sessionReference = session.ref;
            apiVersion = APIVersion.latest();
        }

        return result.result;
    }

    /**
     * Send a method call to xapi's backend. To be used with methods without a return type
     *
     * @param methodCall       the JSON-RPC xapi method call. e.g.: session.login_with_password
     * @param methodParameters the methodParameters of the method call
     * @throws XenAPIException if the call failed.
     * @throws IOException     if an I/O error occurs when sending or receiving, includes cases when the request's payload or the response's payload cannot be written or read as valid JSON.
     */
    public <T> void dispatch(String methodCall, Object[] methodParameters) throws XenAPIException, IOException {
        var typeReference = new TypeReference<T>() {
        };
        this.dispatch(methodCall, methodParameters, typeReference);
    }
}

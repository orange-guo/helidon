/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.tests.integration.webserver.resourcelimit;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;

import io.helidon.common.http.Http;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.api.ClientResponseTyped;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class MaxTcpConnectionsTest {
    private final SocketHttpClient client;
    private final WebClient webClient;

    MaxTcpConnectionsTest(SocketHttpClient client, WebClient webClient) {
        this.client = client;
        this.webClient = webClient;
    }

    @SetUpServer
    static void serverSetup(WebServerConfig.Builder builder) {
        builder.maxTcpConnections(1);
    }

    @SetUpRoute
    static void routeSetup(HttpRules rules) {
        rules.get("/greet", (req, res) -> res.send("hello"));
    }

    @Test
    void testConcurrentRequests() throws Exception {
        String response = client.sendAndReceive(Http.Method.GET, "/greet", null, List.of("Connection: keep-alive"));
        assertThat(response, containsString("200 OK"));

        // we have a connection established with keep alive, we should not create a new one
        // this should timeout on read timeout (because network connect will be done, as the server socket is open,
        // the socket is just never accepted
        System.out.println("**** Attempt request that should timeout");
        assertThrows(UncheckedIOException.class,
                     () -> webClient.get("/greet")
                             .readTimeout(Duration.ofMillis(200))
                             .request(String.class));
        System.out.println("**** Closing SocketClient");
        client.close();
        Thread.sleep(100); // give it some time for server to release the semaphore
        System.out.println("**** Attempt request that should succeed");
        ClientResponseTyped<String> typedResponse = webClient.get("/greet")
                .readTimeout(Duration.ofMillis(200))
                .request(String.class);
        System.out.println("**** Validate response entity");
        assertThat(typedResponse.status().text(), typedResponse.entity(), is("hello"));
    }
}

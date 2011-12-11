/**
 * Copyright (c) 2011, ReXSL.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the ReXSL.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.rexsl.test;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.UriBuilder;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

/**
 * Test case for {@link RestTester}.
 * @author Yegor Bugayenko (yegor@rexsl.com)
 * @version $Id$
 */
public final class RestTesterTest {

    /**
     * RestTester can send HTTP request and process HTTP response.
     * @throws Exception If something goes wrong inside
     */
    @Test
    public void sendsHttpRequestAndProcessesHttpResponse() throws Exception {
        final ContainerMocker container = new ContainerMocker()
            .expectRequestUri(Matchers.containsString("foo"))
            .returnBody("works fine")
            .mock();
        RestTester
            .start(UriBuilder.fromUri(container.home()).path("/foo").build())
            .get()
            .assertBody(Matchers.containsString("fine"))
            .assertBody(Matchers.containsString("works"))
            .assertStatus(HttpURLConnection.HTTP_OK);
    }

    /**
     * RestTester can send HTTP headers.
     * @throws Exception If something goes wrong inside
     */
    @Test
    public void sendsHttpRequestWithHeaders() throws Exception {
        final ContainerMocker container = new ContainerMocker()
            .expectHeader(HttpHeaders.ACCEPT, Matchers.containsString("*"))
            .mock();
        RestTester
            .start(container.home())
            .header(HttpHeaders.ACCEPT, "*/*")
            .get()
            .assertStatus(HttpURLConnection.HTTP_OK);
    }

    /**
     * RestTester can send body with HTTP POST request.
     * @throws Exception If something goes wrong inside
     */
    @Test
    public void sendsTextWithPostRequest() throws Exception {
        final ContainerMocker container = new ContainerMocker()
            .expectBody(Matchers.containsString("bar"))
            .mock();
        RestTester
            .start(container.home())
            .post("bar bar bar")
            .assertStatus(HttpURLConnection.HTTP_OK);
    }

    /**
     * RestTester can assert HTTP status code value.
     * @throws Exception If something goes wrong inside.
     */
    @Test
    public void assertsHttpStatus() throws Exception {
        final ContainerMocker container = new ContainerMocker()
            .returnStatus(HttpURLConnection.HTTP_NOT_FOUND)
            .mock();
        RestTester
            .start(container.home())
            .get()
            .assertStatus(HttpURLConnection.HTTP_NOT_FOUND)
            .assertStatus(Matchers.equalTo(HttpURLConnection.HTTP_NOT_FOUND));
    }

    /**
     * RestTester can assert response body.
     * @throws Exception If something goes wrong inside.
     */
    @Test
    public void assertsHttpResponseBody() throws Exception {
        final ContainerMocker container = new ContainerMocker()
            .returnBody("some text")
            .mock();
        RestTester
            .start(container.home())
            .get()
            .assertBody(Matchers.containsString("some"))
            .assertStatus(HttpURLConnection.HTTP_OK);
    }

    /**
     * RestTester can assert response body content with XPath query.
     * @throws Exception If something goes wrong inside.
     */
    @Test
    public void assertsResponseBodyWithXpathQuery() throws Exception {
        final ContainerMocker container = new ContainerMocker()
            .returnBody("<root><a>works fine for me</a></root>")
            .mock();
        RestTester
            .start(container.home())
            .get()
            .assertXPath("/root/a[contains(.,'works')]")
            .assertStatus(HttpURLConnection.HTTP_OK);
    }

}

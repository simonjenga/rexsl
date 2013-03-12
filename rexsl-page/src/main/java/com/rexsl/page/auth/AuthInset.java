/**
 * Copyright (c) 2011-2013, ReXSL.com
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
package com.rexsl.page.auth;

import com.jcabi.aspects.Cacheable;
import com.jcabi.aspects.Loggable;
import com.jcabi.log.Logger;
import com.rexsl.page.BasePage;
import com.rexsl.page.CookieBuilder;
import com.rexsl.page.Inset;
import com.rexsl.page.JaxbBundle;
import com.rexsl.page.Link;
import com.rexsl.page.Resource;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Authentication inset.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 0.4.7
 */
@ToString
@EqualsAndHashCode(of = { "resource", "key", "salt" })
public final class AuthInset implements Inset {

    /**
     * Cookie name.
     */
    private static final String AUTH_COOKIE = "Rexsl-Auth";

    /**
     * Logout Query param.
     */
    private static final String LOGOUT_FLAG = "rexsl-logout";

    /**
     * The resource.
     */
    private final transient Resource resource;

    /**
     * Security key.
     */
    private final transient String key;

    /**
     * Security salt.
     */
    private final transient String salt;

    /**
     * Providers.
     */
    private final transient Set<Provider> providers = new HashSet<Provider>();

    /**
     * Public ctor.
     * @param res The resource
     * @param sec Security key
     * @param slt Security salt
     */
    public AuthInset(@NotNull final Resource res, @NotNull final String sec,
        @NotNull final String slt) {
        this.resource = res;
        this.key = sec;
        this.salt = slt;
    }

    /**
     * Encrypt identity into text.
     * @param identity The identity to encrypt
     * @param key Security key
     * @param salt Security salt
     * @return Encrypted text for cookie
     */
    @Loggable(Loggable.DEBUG)
    public static String encrypt(@NotNull final Identity identity,
        @NotNull final String key, @NotNull final String salt) {
        return new Encrypted(identity, key, salt).toString();
    }

    /**
     * With this authentication provider.
     * @param prov Additional authentication provider
     * @return This object
     */
    @Loggable(Loggable.DEBUG)
    public AuthInset with(@NotNull final Provider prov) {
        this.providers.add(prov);
        return this;
    }

    /**
     * Get user's identity (runtime exception if not authenticated).
     * @return Identity, if authenticated
     */
    @Loggable(Loggable.DEBUG)
    @Cacheable(lifetime = 1, unit = TimeUnit.SECONDS)
    public Identity identity() {
        Identity identity = Identity.ANONYMOUS;
        for (Provider prov : this.providers) {
            try {
                identity = prov.identity();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            if (!identity.equals(Identity.ANONYMOUS)) {
                throw new WebApplicationException(
                    Response.status(HttpURLConnection.HTTP_SEE_OTHER).location(
                        this.resource.uriInfo().getRequestUriBuilder()
                            .replaceQuery("")
                            .build()
                    ).cookie(this.cookie(identity)).build()
                );
            }
        }
        if (this.resource.httpHeaders().getCookies()
            .containsKey(AuthInset.AUTH_COOKIE)) {
            final String cookie = this.resource.httpHeaders().getCookies()
                .get(AuthInset.AUTH_COOKIE).getValue();
            try {
                identity = Encrypted.parse(cookie, this.key, this.salt);
            } catch (Encrypted.DecryptionException ex) {
                Logger.warn(
                    this,
                    "Failed to decrypt '%s' from %s to '%s': %[exception]s",
                    cookie,
                    this.resource.httpServletRequest().getRemoteAddr(),
                    this.resource.httpServletRequest().getRequestURI(),
                    ex
                );
            }
        }
        return identity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Loggable(Loggable.DEBUG)
    public void render(@NotNull final BasePage<?, ?> page,
        @NotNull final Response.ResponseBuilder builder) {
        final Identity identity = this.identity();
        if (identity.equals(Identity.ANONYMOUS)) {
            for (Provider prov : this.providers) {
                page.link(prov.link());
            }
        } else {
            page.append(
                new JaxbBundle("identity")
                    .add("urn", identity.urn())
                    .up()
                    .add("name", identity.name())
                    .up()
                    .add("photo", identity.photo())
                    .up()
            );
            page.link(
                new Link(
                    "auth-logout",
                    this.resource.uriInfo().getRequestUriBuilder()
                        .clone()
                        .queryParam(AuthInset.LOGOUT_FLAG, true)
                        .build()
                )
            );
            builder.cookie(this.cookie(identity));
            builder.header("X-Rexsl-Identity", identity.urn());
        }
        if (this.resource.uriInfo().getQueryParameters()
            .containsKey(AuthInset.LOGOUT_FLAG)) {
            throw new WebApplicationException(
                Response.status(HttpURLConnection.HTTP_SEE_OTHER).location(
                    this.resource.uriInfo().getRequestUriBuilder()
                        .replaceQuery("")
                        .build()
                ).cookie(
                    new CookieBuilder(this.resource.uriInfo().getBaseUri())
                        .name(AuthInset.AUTH_COOKIE)
                        .build()
                ).build()
            );
        }
    }

    /**
     * Authentication cookie.
     * @param identity The identity to wrap into the cookie
     * @return The cookie
     */
    private NewCookie cookie(final Identity identity) {
        return new CookieBuilder(this.resource.uriInfo().getBaseUri())
            .name(AuthInset.AUTH_COOKIE)
            .value(new Encrypted(identity, this.key, this.salt))
            .temporary()
            .build();
    }

}

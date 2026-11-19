package com.softm.sangsokhannun
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPolicyTest {
    @Test fun acceptsOnlyTheAppHttpsPath() {
        assertTrue(WebPolicy.isTrusted("https://softm.github.io/sangsokhannun/"))
        assertTrue(WebPolicy.isTrusted("https://softm.github.io/sangsokhannun/index.html#assets"))
        assertTrue(WebPolicy.isTrusted("https://softm.github.io:443/sangsokhannun/"))
    }
    @Test fun rejectsOriginAndPathConfusion() {
        listOf(
            "http://softm.github.io/sangsokhannun/",
            "https://softm.github.io.evil.example/sangsokhannun/",
            "https://softm.github.io@evil.example/sangsokhannun/",
            "https://evil.example@softm.github.io/sangsokhannun/",
            "https://softm.github.io/sangsokhannun-evil/",
            "https://softm.github.io/another-app/",
            "https://softm.github.io/sangsokhannun/../another-app/",
            "https://softm.github.io/sangsokhannun/%2e%2e/another-app/",
            "https://softm.github.io:8443/sangsokhannun/",
            "file:///sangsokhannun/index.html",
            "javascript:alert(1)"
        ).forEach { assertFalse(it, WebPolicy.isTrusted(it)) }
    }
    @Test fun blocksAdFitWithoutBlockingFirstPartyContent() {
        assertTrue(WebPolicy.isAdFit("https://t1.daumcdn.net/kas/static/ba.min.js"))
        assertTrue(WebPolicy.isAdFit("https://display.ad.daum.net/some-ad"))
        assertFalse(WebPolicy.isAdFit("https://softm.github.io/sangsokhannun/src/app.js"))
        assertFalse(WebPolicy.isAdFit("https://t1.daumcdn.net/other/asset.js"))
    }
}

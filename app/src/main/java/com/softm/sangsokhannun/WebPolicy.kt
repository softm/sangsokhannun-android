package com.softm.sangsokhannun

import java.net.URI

/** No suffix/substring origin matching: sibling GitHub Pages projects stay external. */
internal object WebPolicy {
    const val ORIGIN = "https://softm.github.io"
    private const val PATH_PREFIX = "/sangsokhannun/"
    fun isTrusted(raw: String?): Boolean = runCatching {
        val uri = URI(raw ?: return false)
        uri.scheme == "https" && uri.host == "softm.github.io" &&
            (uri.port == -1 || uri.port == 443) && uri.userInfo == null &&
            (uri.rawPath == "/sangsokhannun" ||
                (uri.rawPath?.startsWith(PATH_PREFIX) == true &&
                    !uri.rawPath.contains("%", ignoreCase = true) &&
                    !uri.rawPath.split("/").any { it == ".." || it == "." }))
    }.getOrDefault(false)
    fun isExternalHttps(raw: String?): Boolean = runCatching {
        val uri = URI(raw ?: return false)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
    fun isAdFit(raw: String?): Boolean = runCatching {
        val host = URI(raw ?: return false).host?.lowercase() ?: return false
        host == "t1.daumcdn.net" && raw.contains("/kas/static/") ||
            host == "ad.daum.net" || host.endsWith(".ad.daum.net") ||
            host == "adfit.kakao.com" || host.endsWith(".adfit.kakao.com") ||
            host == "ad.kakao.com" || host.endsWith(".ad.kakao.com")
    }.getOrDefault(false)
}

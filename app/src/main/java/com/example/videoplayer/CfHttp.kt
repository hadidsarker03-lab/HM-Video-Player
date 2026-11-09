package com.example.videoplayer

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

// সব ব্রাউজার-ডাউনলোড Cloudflare DNS (1.1.1.1) দিয়ে হয়: DNS-over-HTTPS, ফোনের নিজের DNS ব্যবহার হয় না
object CfHttp {
    val client: OkHttpClient by lazy {
        val bootstrap = OkHttpClient.Builder().build()
        val doh = DnsOverHttps.Builder()
            .client(bootstrap)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
                InetAddress.getByName("2606:4700:4700::1111"),
                InetAddress.getByName("2606:4700:4700::1001")
            )
            .includeIPv6(true)
            .build()
        OkHttpClient.Builder()
            .dns(doh)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

package com.spaceclipboard.app.pairing

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object NetworkAddressValidator {
    /**
     * O protocolo só aceita destinos da rede local, evitando que um QR Code malicioso
     * transforme o aplicativo em um cliente para a internet.
     */
    fun isLocalHost(host: String): Boolean {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        if (clean.isEmpty() || clean.length > 253) return false
        if (clean.any { it.isWhitespace() || it == '/' || it == '\\' || it == '@' || it == ':' }) {
            // ':' é permitido apenas em IPv6 literal, validado abaixo.
            if (!clean.contains(':')) return false
        }
        val addresses = runCatching { InetAddress.getAllByName(clean) }.getOrNull() ?: return false
        if (addresses.isEmpty()) return false
        return addresses.all { isPrivateOrLocal(it) }
    }

    fun isPrivateOrLocal(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress) {
            return true
        }
        if (address is Inet4Address) {
            val bytes = address.address.map { it.toInt() and 0xFF }
            return bytes[0] == 10 ||
                (bytes[0] == 172 && bytes[1] in 16..31) ||
                (bytes[0] == 192 && bytes[1] == 168) ||
                (bytes[0] == 169 && bytes[1] == 254)
        }
        if (address is Inet6Address) {
            val first = address.address[0].toInt() and 0xFE
            return first == 0xFC || address.isSiteLocalAddress || address.isLinkLocalAddress
        }
        return false
    }

    fun normalizeHost(host: String): String = host.trim().removePrefix("[").removeSuffix("]")

    fun formatUrlHost(host: String): String {
        val clean = normalizeHost(host)
        return if (clean.contains(':')) "[$clean]" else clean
    }
}

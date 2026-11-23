package vn.muoikip.pos.core

import java.text.Normalizer

data class BankAccount(val bin: String, val account: String, val holder: String)

data class Bank(val bin: String, val shortName: String)

/** NAPAS BINs (source: api.vietqr.io/v2/banks, 2026-11). Common banks first. */
val BANKS: List<Bank> = listOf(
    "970436" to "Vietcombank", "970415" to "VietinBank", "970418" to "BIDV", "970405" to "Agribank",
    "970422" to "MBBank", "970407" to "Techcombank", "970416" to "ACB", "970432" to "VPBank",
    "970403" to "Sacombank", "970423" to "TPBank", "970437" to "HDBank", "970441" to "VIB",
    "970443" to "SHB", "970449" to "LPBank", "970440" to "SeABank", "970448" to "OCB",
    "970426" to "MSB", "970431" to "Eximbank", "970428" to "NamABank", "970409" to "BacABank",
    "970452" to "KienLongBank", "970454" to "VietCapitalBank", "970412" to "PVcomBank",
    "546034" to "CAKE", "963388" to "Timo",
    // E-wallets that accept VietQR transfers from bank apps.
    "971025" to "MoMo", "971101" to "ZaloPay",
).map { (bin, name) -> Bank(bin, name) }

fun bankName(bin: String): String = BANKS.firstOrNull { it.bin == bin }?.shortName ?: bin

/** VietQR (EMVCo merchant-presented QR, NAPAS 247 account transfer) with a fixed amount. */
object VietQr {
    fun payload(bin: String, account: String, amount: Vnd, purpose: String): String {
        val beneficiary = tlv("00", bin) + tlv("01", account)
        val merchant = tlv("00", "A000000727") + tlv("01", beneficiary) + tlv("02", "QRIBFTTA")
        val note = ascii(purpose).take(50)
        val body = tlv("00", "01") + tlv("01", "12") + tlv("38", merchant) +
            tlv("53", "704") + tlv("54", amount.toString()) + tlv("58", "VN") +
            tlv("62", tlv("08", note)) + "6304"
        return body + crc16(body)
    }

    /** CRC-16/CCITT-FALSE, upper-case hex. */
    fun crc16(data: String): String {
        var crc = 0xFFFF
        for (b in data.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return "%04X".format(crc)
    }

    private fun tlv(id: String, value: String) = id + "%02d".format(value.length) + value

    /** Bank apps only accept plain ASCII in the note. */
    fun ascii(s: String): String =
        Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^A-Za-z0-9 ]"), " ")
            .replace(Regex(" +"), " ")
            .trim()
}

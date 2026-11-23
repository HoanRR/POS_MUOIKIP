package vn.muoikip.pos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VietQrTest {
    @Test fun crcMatchesStandardCheckValue() {
        // CRC-16/CCITT-FALSE check value from the CRC catalogue.
        assertEquals("29B1", VietQr.crc16("123456789"))
    }

    @Test fun payloadFollowsNapasLayout() {
        val body = "000201" + "010212" +
            "3857" + "0010A000000727" + "0127" + "0006970436" + "0113" + "0011001234567" + "0208QRIBFTTA" +
            "5303704" + "5406150000" + "5802VN" +
            "6228" + "0824MUOI KIP San truoc Ban 3" +
            "6304"
        val payload = VietQr.payload(bin = "970436", account = "0011001234567", amount = 150_000, purpose = "MUOI KIP Sân trước · Bàn 3")
        assertEquals(body, payload.dropLast(4))
        assertEquals(VietQr.crc16(body), payload.takeLast(4))
    }

    @Test fun walletsAreInTheBankListWithNapasBins() {
        // BINs from api.vietqr.io/v2/banks.
        assertEquals("MoMo", bankName("971025"))
        assertEquals("ZaloPay", bankName("971101"))
    }

    @Test fun accountMayContainLetters() {
        val p = VietQr.payload(bin = "971025", account = "99MM24000123", amount = 50_000, purpose = "MUOI KIP")
        assertTrue(p.contains("0006971025" + "0112" + "99MM24000123"))
    }

    @Test fun billCarriesQrOnlyWhenBankAccountIsSet() {
        val engine = PosEngine(InMemoryStore(), FakeClock(), FakePrinter(), DefaultData.SHOP)
        val t = engine.tables(engine.zones().first().id).first().table.id
        val tiger = engine.categories().flatMap { engine.menu(it.id) }.first { it.name == "Bia Tiger" }.id
        engine.addItem(t, tiger)
        val noBank = (engine.checkout(t, 20_000) as CheckoutResult.Paid).receipt
        assertNull(noBank.qrPayload)

        engine.setBankAccount(BankAccount(bin = "970436", account = "0011001234567", holder = "NGO VAN A"))
        engine.addItem(t, tiger)
        engine.addItem(t, tiger)
        val withBank = (engine.checkout(t, 40_000) as CheckoutResult.Paid).receipt
        assertTrue(withBank.qrPayload!!.contains("540540000"))
        assertEquals("Vietcombank · 0011001234567 · NGO VAN A", withBank.bankLine)
    }

    @Test fun bankAccountSurvivesRestartAndCanBeCleared() {
        val store = InMemoryStore()
        val clock = FakeClock()
        PosEngine(store, clock, FakePrinter(), DefaultData.SHOP)
            .setBankAccount(BankAccount("970415", "1010101010", "NGO VAN A"))
        val again = PosEngine(store, clock, FakePrinter(), DefaultData.SHOP)
        assertEquals(BankAccount("970415", "1010101010", "NGO VAN A"), again.bankAccount())
        again.setBankAccount(null)
        assertNull(PosEngine(store, clock, FakePrinter(), DefaultData.SHOP).bankAccount())
    }

    @Test fun qrIsPrintedOnReceiptLayout() {
        val engine = PosEngine(InMemoryStore(), FakeClock(), FakePrinter(), DefaultData.SHOP)
        engine.setBankAccount(BankAccount("970436", "0011001234567", "NGO VAN A"))
        val t = engine.tables(engine.zones().first().id).first().table.id
        engine.addItem(t, engine.categories().flatMap { engine.menu(it.id) }.first { it.name == "Rau xào" }.id)
        val receipt = (engine.checkout(t, 30_000) as CheckoutResult.Paid).receipt
        val rows = ReceiptLayout.rows(receipt)
        assertTrue(rows.any { it is PrintRow.Qr && it.payload == receipt.qrPayload })
        assertTrue(PrintRow.Center("Vietcombank · 0011001234567 · NGO VAN A") in rows)
    }
}

package vn.muoikip.pos.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import vn.muoikip.pos.core.PrintRow

/**
 * Draws receipt rows as a black-on-white bitmap for the 80mm head (576 dots wide).
 *
 * The printer's built-in font is small and thin; drawing ourselves lets us use large bold
 * Roboto (full Vietnamese diacritics) at sizes an older reader can manage.
 */
object ReceiptRenderer {
    const val WIDTH = 576

    /** Sizes in printer dots (203 dpi: 32 dots ≈ 4 mm tall text). */
    private const val TITLE = 52f
    private const val HEADING = 40f
    private const val BODY = 30f
    private const val TOTAL = 42f
    private const val GAP = 8

    private fun paint(size: Float, bold: Boolean, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        } to align

    private fun layout(text: String, width: Int, size: Float, bold: Boolean, align: Layout.Alignment): StaticLayout {
        val (p, a) = paint(size, bold, align)
        @Suppress("DEPRECATION") // StaticLayout.Builder needs API 23+, the constructor works everywhere.
        return StaticLayout(text, p, width, a, 1.05f, 0f, false)
    }

    /** One drawing step with its height, so the bitmap can be sized exactly. */
    private class Block(val height: Int, val draw: (Canvas, Int) -> Unit)

    private fun blocks(rows: List<PrintRow>): List<Block> = rows.mapNotNull { row ->
        when (row) {
            is PrintRow.Title -> {
                val size = if (row.text == row.text.uppercase() && row.text.length <= 12) TITLE else HEADING
                text(row.text, size, bold = true, Layout.Alignment.ALIGN_CENTER)
            }
            is PrintRow.Center -> text(row.text, BODY, bold = false, Layout.Alignment.ALIGN_CENTER)
            is PrintRow.Text -> text(row.text, BODY, bold = false, Layout.Alignment.ALIGN_NORMAL)
            is PrintRow.Item -> item(row)
            is PrintRow.Amount -> amount(row)
            PrintRow.Divider -> Block(20) { c, y ->
                val p = Paint().apply { color = Color.BLACK; strokeWidth = 3f }
                c.drawLine(0f, y + 10f, WIDTH.toFloat(), y + 10f, p)
            }
            is PrintRow.Qr -> null // printed natively by the printer for a crisp code
        }
    }

    private fun text(s: String, size: Float, bold: Boolean, align: Layout.Alignment): Block {
        val l = layout(s, WIDTH, size, bold, align)
        return Block(l.height + GAP) { c, y -> c.save(); c.translate(0f, y.toFloat()); l.draw(c); c.restore() }
    }

    /** Name (wraps) | quantity | amount. Header row "Món / SL / Thành tiền" is drawn the same way. */
    private fun item(r: PrintRow.Item): Block {
        val header = r.quantity == "SL"
        val qtyW = 64
        val amountW = 190
        val nameW = WIDTH - qtyW - amountW
        val name = layout(r.name, nameW - 8, BODY, bold = !header, Layout.Alignment.ALIGN_NORMAL)
        val qty = layout(r.quantity, qtyW, BODY, bold = !header, Layout.Alignment.ALIGN_CENTER)
        val amt = layout(r.amount, amountW, BODY, bold = !header, Layout.Alignment.ALIGN_OPPOSITE)
        val h = maxOf(name.height, qty.height, amt.height) + GAP
        return Block(h) { c, y ->
            c.save(); c.translate(0f, y.toFloat()); name.draw(c)
            c.translate(nameW.toFloat(), 0f); qty.draw(c)
            c.translate(qtyW.toFloat(), 0f); amt.draw(c); c.restore()
        }
    }

    private fun amount(r: PrintRow.Amount): Block {
        val size = if (r.emphasized) TOTAL else BODY
        val label = layout(r.label, WIDTH / 2, size, bold = true, Layout.Alignment.ALIGN_NORMAL)
        val value = layout(r.value, WIDTH / 2, size, bold = true, Layout.Alignment.ALIGN_OPPOSITE)
        return Block(maxOf(label.height, value.height) + GAP) { c, y ->
            c.save(); c.translate(0f, y.toFloat()); label.draw(c)
            c.translate(WIDTH / 2f, 0f); value.draw(c); c.restore()
        }
    }

    /** Renders rows into one bitmap, or null when there is nothing to draw. */
    fun render(rows: List<PrintRow>): Bitmap? {
        val bs = blocks(rows)
        if (bs.isEmpty()) return null
        val height = bs.sumOf { it.height }
        val bmp = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        var y = 0
        bs.forEach { it.draw(c, y); y += it.height }
        return bmp
    }

    /** Splits rows around QR codes: text segments become bitmaps, QR stays a native print command. */
    fun segments(rows: List<PrintRow>): List<Any> {
        val out = mutableListOf<Any>()
        var current = mutableListOf<PrintRow>()
        for (r in rows) {
            if (r is PrintRow.Qr) {
                render(current)?.let(out::add); current = mutableListOf()
                out += r
            } else current += r
        }
        render(current)?.let(out::add)
        return out
    }
}

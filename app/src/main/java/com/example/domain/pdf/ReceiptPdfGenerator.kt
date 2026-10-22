package com.example.domain.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.entity.BillWithItems
import com.example.data.entity.CustomerWithTransactions
import com.example.data.entity.ShopSettingsEntity
import com.example.domain.calculator.WeightPriceCalculator
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptPdfGenerator {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())

    fun generateBillPdf(
        context: Context,
        billWithItems: BillWithItems,
        shopSettings: ShopSettingsEntity
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(384, 600, 1).create() // 80mm thermal receipt width equivalent
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }
        var y = 35f

        // Header - Shop Name
        paint.color = Color.BLACK
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(shopSettings.shopName, 192f, y, paint)
        y += 18f

        // Tagline / Address
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (shopSettings.tagline.isNotBlank()) {
            canvas.drawText(shopSettings.tagline, 192f, y, paint)
            y += 14f
        }
        canvas.drawText(shopSettings.address, 192f, y, paint)
        y += 14f
        canvas.drawText("Phone: ${shopSettings.phone}", 192f, y, paint)
        y += 18f

        // Divider
        paint.strokeWidth = 1f
        canvas.drawLine(15f, y, 369f, y, paint)
        y += 16f

        // Bill details
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 10f
        canvas.drawText("Bill No: ${billWithItems.bill.billNumber}", 15f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(dateFormat.format(Date(billWithItems.bill.timestamp)), 369f, y, paint)
        y += 14f

        if (billWithItems.bill.customerName.isNotBlank() && billWithItems.bill.customerName != "Cash Customer") {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Customer: ${billWithItems.bill.customerName} (${billWithItems.bill.customerPhone})", 15f, y, paint)
            y += 14f
        }

        // Table Header
        paint.strokeWidth = 1f
        canvas.drawLine(15f, y, 369f, y, paint)
        y += 14f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Item", 15f, y, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Qty", 210f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Rate", 290f, y, paint)
        canvas.drawText("Amount", 369f, y, paint)
        y += 12f
        canvas.drawLine(15f, y, 369f, y, paint)
        y += 14f

        // Items
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        for (item in billWithItems.items) {
            paint.textAlign = Paint.Align.LEFT
            val itemName = if (item.productName.length > 20) item.productName.substring(0, 18) + ".." else item.productName
            canvas.drawText(itemName, 15f, y, paint)

            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantityEntered} ${item.unitEntered}", 210f, y, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("₹${item.ratePerBaseUnit}", 290f, y, paint)
            canvas.drawText("₹${String.format(Locale.getDefault(), "%.2f", item.itemTotal)}", 369f, y, paint)
            y += 16f
        }

        // Divider
        canvas.drawLine(15f, y, 369f, y, paint)
        y += 16f

        // Subtotal & Total
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Subtotal:", 190f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.getDefault(), "%.2f", billWithItems.bill.subtotal)}", 369f, y, paint)
        y += 14f

        if (billWithItems.bill.discount > 0) {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Discount:", 190f, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-₹${String.format(Locale.getDefault(), "%.2f", billWithItems.bill.discount)}", 369f, y, paint)
            y += 14f
        }

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Grand Total:", 190f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("₹${String.format(Locale.getDefault(), "%.2f", billWithItems.bill.grandTotal)}", 369f, y, paint)
        y += 18f

        // Payment info
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Payment Mode: ${billWithItems.bill.paymentMode}", 15f, y, paint)
        y += 14f
        if (billWithItems.bill.amountReceived > 0) {
            canvas.drawText("Amount Received: ₹${billWithItems.bill.amountReceived}", 15f, y, paint)
            if (billWithItems.bill.changeReturned > 0) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("Change Returned: ₹${billWithItems.bill.changeReturned}", 369f, y, paint)
            }
            y += 14f
        }
        if (billWithItems.bill.creditAmount > 0) {
            paint.color = Color.RED
            canvas.drawText("Added to Khata (Udhar): ₹${billWithItems.bill.creditAmount}", 15f, y, paint)
            paint.color = Color.BLACK
            y += 14f
        }

        // Footer
        y += 10f
        canvas.drawLine(15f, y, 369f, y, paint)
        y += 16f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(shopSettings.receiptFooter, 192f, y, paint)

        document.finishPage(page)

        val outputFile = File(context.cacheDir, "bill_${billWithItems.bill.billNumber}.pdf")
        val outputStream = FileOutputStream(outputFile)
        document.writeTo(outputStream)
        outputStream.close()
        document.close()

        return outputFile
    }

    fun generateKhataStatementPdf(
        context: Context,
        customerWithTransactions: CustomerWithTransactions,
        shopSettings: ShopSettingsEntity
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 page
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true }
        var y = 50f

        // Header
        paint.color = Color.BLACK
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(shopSettings.shopName, 297f, y, paint)
        y += 22f

        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Customer Khata Statement / उधारी बही खाता", 297f, y, paint)
        y += 16f
        canvas.drawText("Store: ${shopSettings.phone} | ${shopSettings.address}", 297f, y, paint)
        y += 24f

        canvas.drawLine(40f, y, 555f, y, paint)
        y += 20f

        // Customer info
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Customer: ${customerWithTransactions.customer.name}", 40f, y, paint)
        y += 18f
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Phone: ${customerWithTransactions.customer.phone}", 40f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = if (customerWithTransactions.customer.outstandingBalance > 0) Color.RED else Color.rgb(0, 128, 0)
        canvas.drawText("Outstanding Balance: ₹${customerWithTransactions.customer.outstandingBalance}", 555f, y, paint)
        paint.color = Color.BLACK
        y += 24f

        // Table
        canvas.drawLine(40f, y, 555f, y, paint)
        y += 16f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Date", 40f, y, paint)
        canvas.drawText("Type / Details", 160f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Debit (Udhar)", 380f, y, paint)
        canvas.drawText("Credit (Jama)", 460f, y, paint)
        canvas.drawText("Balance", 555f, y, paint)
        y += 14f
        canvas.drawLine(40f, y, 555f, y, paint)
        y += 18f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        for (tx in customerWithTransactions.transactions) {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(tx.timestamp)), 40f, y, paint)
            val details = if (tx.type == "UDHAR_PURCHASE") "Credit Purchase ${tx.billNumber ?: ""}" else "Payment (${tx.paymentMode})"
            canvas.drawText(details, 160f, y, paint)

            paint.textAlign = Paint.Align.RIGHT
            if (tx.type == "UDHAR_PURCHASE") {
                canvas.drawText("₹${tx.amount}", 380f, y, paint)
                canvas.drawText("-", 460f, y, paint)
            } else {
                canvas.drawText("-", 380f, y, paint)
                canvas.drawText("₹${tx.amount}", 460f, y, paint)
            }
            canvas.drawText("₹${tx.balanceAfter}", 555f, y, paint)
            y += 18f
        }

        document.finishPage(page)

        val outputFile = File(context.cacheDir, "khata_${customerWithTransactions.customer.phone}.pdf")
        val outputStream = FileOutputStream(outputFile)
        document.writeTo(outputStream)
        outputStream.close()
        document.close()

        return outputFile
    }
}

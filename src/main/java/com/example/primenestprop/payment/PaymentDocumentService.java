package com.example.primenestprop.payment;

import com.example.primenestprop.user.AppUser;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

/** Renders downloadable payment receipts and statements - plain HTML/CSV rather than a PDF
 * library, since the platform has no other PDF-generation dependency to justify pulling one in. */
@Service
public class PaymentDocumentService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final PaymentService payments;

    public PaymentDocumentService(PaymentService payments) {
        this.payments = payments;
    }

    public String receiptHtml(Long paymentId, AppUser currentUser) {
        Payment payment = payments.requireVisible(paymentId, currentUser);
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>Receipt %s</title>
                <style>
                  body { font-family: Arial, sans-serif; max-width: 560px; margin: 40px auto; color: #1f2937; }
                  h1 { font-size: 20px; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 16px; }
                  td { padding: 8px 0; border-bottom: 1px solid #e5e7eb; }
                  td:first-child { color: #6b7280; width: 40%%; }
                  .amount { font-size: 24px; font-weight: bold; margin-top: 16px; }
                  .status { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
                </style></head>
                <body>
                  <h1>PrimeNest Payment Receipt</h1>
                  <p>Reference: %s</p>
                  <table>
                    <tr><td>Status</td><td>%s</td></tr>
                    <tr><td>From</td><td>%s</td></tr>
                    <tr><td>To</td><td>%s</td></tr>
                    <tr><td>Purpose</td><td>%s</td></tr>
                    <tr><td>Provider</td><td>%s</td></tr>
                    <tr><td>Created</td><td>%s</td></tr>
                    <tr><td>Paid</td><td>%s</td></tr>
                  </table>
                  <p class="amount">%s %s</p>
                </body></html>
                """.formatted(
                payment.getReference(), payment.getReference(), payment.getStatus(),
                payment.getPayer().getFullName(), payment.getPayee().getFullName(),
                payment.getPurpose() == null ? "Rent payment" : payment.getPurpose(),
                payment.getProvider(),
                DATE_FORMAT.format(payment.getCreatedAt()),
                payment.getPaidAt() == null ? "—" : DATE_FORMAT.format(payment.getPaidAt()),
                payment.getAmount(), payment.getCurrency()
        );
    }

    public String statementCsv(Long userId, AppUser currentUser) {
        List<Payment> userPayments = payments.forUser(userId, currentUser);
        StringBuilder csv = new StringBuilder("Date,Reference,Direction,Counterparty,Purpose,Provider,Status,Amount,Currency\n");
        for (Payment payment : userPayments) {
            boolean isPayer = payment.getPayer().getId().equals(userId);
            String direction = isPayer ? "Paid" : "Received";
            String counterparty = isPayer ? payment.getPayee().getFullName() : payment.getPayer().getFullName();
            csv.append(csvRow(
                    payment.getCreatedAt() == null ? "" : DATE_FORMAT.format(payment.getCreatedAt()),
                    payment.getReference(), direction, counterparty,
                    payment.getPurpose() == null ? "" : payment.getPurpose(),
                    payment.getProvider(), payment.getStatus().name(),
                    payment.getAmount() == null ? "" : payment.getAmount().toString(), payment.getCurrency()
            ));
        }
        return csv.toString();
    }

    private String csvRow(String... fields) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                row.append(",");
            }
            row.append("\"").append(fields[i] == null ? "" : fields[i].replace("\"", "\"\"")).append("\"");
        }
        return row.append("\n").toString();
    }
}

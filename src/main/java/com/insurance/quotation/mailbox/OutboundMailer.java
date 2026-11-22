package com.insurance.quotation.mailbox;

/** Sends outgoing mail through whichever provider is configured. */
public interface OutboundMailer {

    void sendPlain(String to, String subject, String body);

    void sendWithAttachment(String to, String subject, String body, String pdfPath);

    default void sendHtmlWithAttachment(
            String to,
            String subject,
            String htmlBody,
            String pdfPath) {

        // Fallback for providers that do not support HTML.
        sendWithAttachment(to, subject, htmlBody, pdfPath);
    }
}
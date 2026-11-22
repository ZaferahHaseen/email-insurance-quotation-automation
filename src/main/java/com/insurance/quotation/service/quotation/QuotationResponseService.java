package com.insurance.quotation.service.quotation;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.mailbox.OutboundMailer;

@Service
public class QuotationResponseService {

    private static final Logger log =
            LoggerFactory.getLogger(QuotationResponseService.class);

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private final QuotationRepository quotationRepository;
    private final OutboundMailer outboundMailer;

    public QuotationResponseService(
            QuotationRepository quotationRepository,
            OutboundMailer outboundMailer) {

        this.quotationRepository = quotationRepository;
        this.outboundMailer = outboundMailer;
    }

    // ==========================================
    // RESPONSE RESULT
    // ==========================================

    public enum ResponseResult {
        ACCEPTED,
        REJECTED,
        ALREADY_RESPONDED,
        CLOSED,
        INVALID_TOKEN,
        INVALID_RESPONSE
    }

    // ==========================================
    // GENERATE SECURE RESPONSE TOKEN
    // ==========================================

    /**
     * Generates a secure token for a quotation.
     *
     * This token will be included in the Accept and Reject
     * links sent to the client.
     *
     * If a token already exists, the existing token is reused.
     */
    @Transactional
    public String ensureResponseToken(Quotation quotation) {

        if (quotation == null) {
            throw new IllegalArgumentException(
                    "Quotation cannot be null");
        }

        if (quotation.getResponseToken() != null
                && !quotation.getResponseToken().isBlank()) {

            return quotation.getResponseToken();
        }

        byte[] randomBytes = new byte[32];

        SECURE_RANDOM.nextBytes(randomBytes);

        String token = HexFormat.of().formatHex(randomBytes);

        quotation.setResponseToken(token);

        quotationRepository.save(quotation);

        log.info(
                "Response token generated for quotation {}",
                quotation.getQuotationNumber()
        );

        return token;
    }

    // ==========================================
    // PROCESS CLIENT RESPONSE
    // ==========================================

    @Transactional
    public ResponseResult respond(
            String token,
            String decision) {

        if (token == null || token.isBlank()) {

            return ResponseResult.INVALID_TOKEN;
        }

        if (decision == null || decision.isBlank()) {

            return ResponseResult.INVALID_RESPONSE;
        }

        String normalizedDecision =
                decision.trim().toUpperCase();

        if (!normalizedDecision.equals("ACCEPT")
                && !normalizedDecision.equals("REJECT")) {

            return ResponseResult.INVALID_RESPONSE;
        }

        Quotation quotation =
                quotationRepository
                        .findByResponseToken(token)
                        .orElse(null);

        if (quotation == null) {

            log.warn("Invalid quotation response token received");

            return ResponseResult.INVALID_TOKEN;
        }

        // --------------------------------------
        // CHECK EXISTING RESPONSE
        // --------------------------------------

        if (!"PENDING".equalsIgnoreCase(
                quotation.getResponseStatus())) {

            log.info(
                    "Quotation {} already has response status {}",
                    quotation.getQuotationNumber(),
                    quotation.getResponseStatus()
            );

            return ResponseResult.ALREADY_RESPONDED;
        }

        // --------------------------------------
        // CHECK IF QUOTATION IS CLOSED
        // --------------------------------------

        if (quotation.getClosedAt() != null) {

            log.info(
                    "Quotation {} is already closed",
                    quotation.getQuotationNumber()
            );

            return ResponseResult.CLOSED;
        }

        LocalDateTime now = LocalDateTime.now();

        quotation.setResponseAt(now);

        // --------------------------------------
        // ACCEPT
        // --------------------------------------

        if (normalizedDecision.equals("ACCEPT")) {

            quotation.setResponseStatus("ACCEPTED");

            quotationRepository.save(quotation);

            log.info(
                    "Quotation {} accepted by client",
                    quotation.getQuotationNumber()
            );

            sendAcceptanceConfirmation(quotation);

            return ResponseResult.ACCEPTED;
        }

        // --------------------------------------
        // REJECT
        // --------------------------------------

        quotation.setResponseStatus("REJECTED");

        quotation.setClosedAt(now);

        quotation.setClosureReason("REJECTED");

        quotationRepository.save(quotation);

        log.info(
                "Quotation {} rejected and closed",
                quotation.getQuotationNumber()
        );

        sendRejectionConfirmation(quotation);

        return ResponseResult.REJECTED;
    }

    // ==========================================
    // ACCEPTANCE CONFIRMATION EMAIL
    // ==========================================

    private void sendAcceptanceConfirmation(
            Quotation quotation) {

        String email = getClientEmail(quotation);

        if (email == null || email.isBlank()) {

            log.warn(
                    "Cannot send acceptance confirmation for quotation {}: "
                            + "client email is missing",
                    quotation.getQuotationNumber()
            );

            return;
        }

        String subject =
                "Quotation Acceptance Confirmation - "
                        + quotation.getQuotationNumber();

        String body =
                "Dear Customer,\n\n"
                        + "Thank you for accepting our quotation.\n\n"
                        + "Quotation Number: "
                        + quotation.getQuotationNumber()
                        + "\n"
                        + "Amount: "
                        + quotation.getCurrency()
                        + " "
                        + quotation.getTotalAmount()
                        + "\n\n"
                        + "Your acceptance has been recorded successfully. "
                        + "Our team will process your request manually "
                        + "and contact you regarding the next steps.\n\n"
                        + "Thank you for choosing us.\n\n"
                        + "Regards,\n"
                        + "Insurance Quotation Team";

        try {

            outboundMailer.sendPlain(
                    email,
                    subject,
                    body
            );

            log.info(
                    "Acceptance confirmation email sent for quotation {}",
                    quotation.getQuotationNumber()
            );

        } catch (RuntimeException e) {

            log.error(
                    "Failed to send acceptance confirmation email "
                            + "for quotation {}",
                    quotation.getQuotationNumber(),
                    e
            );
        }
    }

    // ==========================================
    // REJECTION CONFIRMATION EMAIL
    // ==========================================

    private void sendRejectionConfirmation(
            Quotation quotation) {

        String email = getClientEmail(quotation);

        if (email == null || email.isBlank()) {

            log.warn(
                    "Cannot send rejection confirmation for quotation {}: "
                            + "client email is missing",
                    quotation.getQuotationNumber()
            );

            return;
        }

        String subject =
                "Quotation Closure Confirmation - "
                        + quotation.getQuotationNumber();

        String body =
                "Dear Customer,\n\n"
                        + "We have received your decision to reject "
                        + "the quotation.\n\n"
                        + "Quotation Number: "
                        + quotation.getQuotationNumber()
                        + "\n\n"
                        + "Your quotation has been closed successfully.\n\n"
                        + "If you would like a new quotation in the future, "
                        + "please feel free to contact us.\n\n"
                        + "Thank you for your time.\n\n"
                        + "Regards,\n"
                        + "Insurance Quotation Team";

        try {

            outboundMailer.sendPlain(
                    email,
                    subject,
                    body
            );

            log.info(
                    "Rejection confirmation email sent for quotation {}",
                    quotation.getQuotationNumber()
            );

        } catch (RuntimeException e) {

            log.error(
                    "Failed to send rejection confirmation email "
                            + "for quotation {}",
                    quotation.getQuotationNumber(),
                    e
            );
        }
    }

    // ==========================================
    // GET CLIENT EMAIL
    // ==========================================

    private String getClientEmail(
            Quotation quotation) {

        if (quotation == null
                || quotation.getQuotationRequest() == null) {

            return null;
        }

        Client client =
                quotation.getQuotationRequest().getClient();

        if (client == null) {
            return null;
        }

        return client.getEmail();
    }
}
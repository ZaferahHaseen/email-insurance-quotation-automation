
package com.insurance.quotation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.insurance.quotation.service.quotation.QuotationResponseService;
import com.insurance.quotation.service.quotation.QuotationResponseService.ResponseResult;

@RestController
@RequestMapping("/api/public/quotation-response")
public class QuotationResponseController {

    private final QuotationResponseService responseService;

    public QuotationResponseController(
            QuotationResponseService responseService) {

        this.responseService = responseService;
    }

    @PostMapping("/{token}")
    public ResponseEntity<String> respond(
            @PathVariable String token,
            @RequestBody ResponseRequest request) {

        if (request == null || request.decision() == null) {
            return ResponseEntity
                    .badRequest()
                    .body("A quotation decision is required.");
        }

        ResponseResult result =
                responseService.respond(token, request.decision());

        return switch (result) {

            case ACCEPTED -> ResponseEntity.ok(
                    "Quotation accepted successfully. "
                    + "Our team will process your acceptance manually.");

            case REJECTED -> ResponseEntity.ok(
                    "Quotation rejected successfully. "
                    + "The quotation has been closed.");

            case ALREADY_RESPONDED -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("This quotation has already received a response.");

            case CLOSED -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("This quotation is already closed.");

            case INVALID_TOKEN -> ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Invalid quotation response link.");

            case INVALID_RESPONSE -> ResponseEntity
                    .badRequest()
                    .body("Invalid quotation decision.");
        };
    }

    public record ResponseRequest(String decision) {
    }
}

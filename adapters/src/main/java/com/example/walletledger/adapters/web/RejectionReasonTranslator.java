package com.example.walletledger.adapters.web;

import com.example.walletledger.adapters.web.dto.ErrorCode;
import com.example.walletledger.core.model.RejectionReason;
import org.springframework.http.HttpStatus;

final class RejectionReasonTranslator {

    private RejectionReasonTranslator() {
    }

    static RejectionTranslation translate(RejectionReason rejectionReason) {
        return switch (rejectionReason) {
            case SOURCE_ACCOUNT_NOT_FOUND, TARGET_ACCOUNT_NOT_FOUND ->
                    new RejectionTranslation(ErrorCode.ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND);
            case SAME_ACCOUNT_TRANSFER ->
                    new RejectionTranslation(ErrorCode.SAME_ACCOUNT_TRANSFER, HttpStatus.UNPROCESSABLE_ENTITY);
            case CURRENCY_MISMATCH ->
                    new RejectionTranslation(ErrorCode.CURRENCY_MISMATCH, HttpStatus.UNPROCESSABLE_ENTITY);
            case INSUFFICIENT_FUNDS ->
                    new RejectionTranslation(ErrorCode.INSUFFICIENT_FUNDS, HttpStatus.UNPROCESSABLE_ENTITY);
            case INVALID_MONETARY_AMOUNT, INVALID_IDENTIFIER, UNKNOWN_CURRENCY ->
                    new RejectionTranslation(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
        };
    }

    record RejectionTranslation(ErrorCode errorCode, HttpStatus status) {
    }
}

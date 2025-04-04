package com.ecsimsw.transaction.service

import com.ecsimsw.common.client.UserClient
import com.ecsimsw.transaction.domain.Transaction
import com.ecsimsw.transaction.domain.TransactionStatus
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class TransactionPaymentService(
    private val userClient: UserClient,
    private val paymentService: PaymentService,
    private val transactionService: TransactionService
) {
    fun create(username: String, amount: Long, successUrl: String, cancelUrl: String): String {
        val transaction = transactionService.create(username, amount)
        val payment = paymentService.create(transaction, successUrl, cancelUrl)
        transactionService.paymentRequested(transaction, payment.id)
        return payment.url
    }

    fun approve(paymentId: String, payerId: String) {
        val transaction = transactionService.findByPaymentId(paymentId)
        if (!transaction.isStatus(TransactionStatus.REQUESTED)) {
            throw IllegalArgumentException("Not a valid transaction")
        }
        addCredit(transaction)

        try {
            paymentService.approve(paymentId, payerId)
            transactionService.approved(transaction)
        } catch (e: Exception) {
            transactionService.failed(transaction, "Failed to payment")
            rollbackCredit(transaction)
            throw IllegalArgumentException("Failed to payment")
        }
    }

    private fun addCredit(transaction: Transaction) {
        val creditAddedResponse = userClient.addCredit(transaction.username, transaction.amount)
        if (creditAddedResponse.statusCode != HttpStatus.OK) {
            transactionService.failed(transaction, "Failed to add credit")
            throw IllegalArgumentException("Failed to add credit")
        }
    }

    private fun rollbackCredit(transaction: Transaction) {
        userClient.rollbackCreditAddition(transaction.username, transaction.amount)
    }
}
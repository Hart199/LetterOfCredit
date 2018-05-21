package eloc.contract

import eloc.state.LetterOfCreditApplicationState
import eloc.state.LetterOfCreditState
import eloc.state.LetterOfCreditStatus
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash

open class LetterOfCreditContract : Contract {
    companion object {
        @JvmStatic
        val CONTRACT_ID = "eloc.contract.LetterOfCreditContract"
    }

    interface Commands : CommandData {
        class Issuance : TypeOnlyCommandData(), Commands
        class ConfirmShipment : TypeOnlyCommandData(), Commands
        class AddPaymentToBeneficiary : TypeOnlyCommandData(), Commands
        class AddPaymentToAdvisory :TypeOnlyCommandData(), Commands
        class AddPaymentToIssuer :TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        // We should only ever receive one command at a time, else throw an exception
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Issuance -> {
                requireThat {
                    val output = tx.outputsOfType<LetterOfCreditState>().single()
                    // confirms the LetterOfCreditApplication is included in the transaction
                    tx.inputsOfType<LetterOfCreditApplicationState>().single()
                    requireThat {
                        //"the transaction is not signed by the advising bank" by (command.signers.contains(output.props.advisingBank.owningKey))
                        "The LOC must be Issued" using (output.status == LetterOfCreditStatus.ISSUED)
                        "The period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                    }
                }
            }

            is Commands.ConfirmShipment -> {
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                requireThat {
                    "The transaction is signed by the seller" using (command.signers.contains(output.props.beneficiary.owningKey))
                    "The LOC must be Issued" using (output.status == LetterOfCreditStatus.SHIPPED)
                }
            }

            is Commands.AddPaymentToBeneficiary -> {
                val input = tx.inputsOfType<LetterOfCreditState>().single()
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                requireThat {
                    "Cash is part of the output state." using (tx.outputsOfType<Cash.State>().any())
                    "Seller must not already have been paid." using (input.status == LetterOfCreditStatus.SHIPPED)
                    "Transaction must update ledger to mark seller as paid." using (output.status == LetterOfCreditStatus.BENEFICIARY_PAID)
                    "The period of presentation must be a positive number." using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                }
            }

            is Commands.AddPaymentToAdvisory -> {
                val input = tx.inputsOfType<LetterOfCreditState>().single()
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                requireThat {
                    "Cash is part of the output state." using (tx.outputsOfType<Cash.State>().any())
                    "Advising bank must not already have been paid." using (input.status == LetterOfCreditStatus.BENEFICIARY_PAID)
                    "Advising bank is marked as being paid in output state." using (output.status == LetterOfCreditStatus.ADVISORY_PAID)
                    "The period of presentation must be a positive number." using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                }
            }

            is Commands.AddPaymentToIssuer -> {
                val input = tx.inputsOfType<LetterOfCreditState>().single()
                val output = tx.outputsOfType<LetterOfCreditState>().single()
                requireThat {
                    "Cash is part of the output state" using (tx.outputsOfType<Cash.State>().any())
                    "Issuing bank must not already have been paid" using (input.status == LetterOfCreditStatus.ADVISORY_PAID)
                    "Issuing bank is marked as being paid in output state" using (output.status == LetterOfCreditStatus.ISSUER_PAID)
                    "The period of presentation must be a positive number" using (!output.props.periodPresentation.isNegative && !output.props.periodPresentation.isZero)
                }
            }
        }
    }
}
package code
package snippet

import java.util.Date
import scala.xml._

import net.liftweb._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.common._
import net.liftweb.http._

import model._

class Account extends Loggable {

	def user = User.currentUser.open_!

	/* helpers */
	def updateWallet(wallet: String) = {
		user.wallet(wallet).save
		logger.info("User %s - New Wallet: %s".format(user.id, wallet))
	}

	def updateEmail(email: String) = {
		user.email(email).save
		logger.info("User %s - New E-Mail: %s".format(user.id, email))
	}

	def updateDonation(donation: String) = {
		var donatePercent = donation.replaceFirst(",", ".")
		user.donatePercent.setFromString(donatePercent)
		user.save
		logger.info("User %s - New Donation: %s %%".format(user.id, donatePercent))
	}
	
	def updatePayoutlock(bool: Boolean) = {
		user.payoutlock(bool).save
		logger.info("User %s - Payout Lock: %s".format(user.id, bool))
	}

	def updateIdlewarning(bool: Boolean) = {
		user.idlewarning(bool).save
		logger.info("User %s - Idle Warning: %s".format(user.id, bool))
	}

	def sendcoinsToAccount(amountStr: String) = {
		val amount = amountStr.replaceFirst(",", ".")
		val balance = AccountBalance.create.user(user).sendAddress(user.wallet)
		balance.balance.setFromString(amount)
		balance.paid(true).timestamp(new Date).save
		logger.info("SENDING COINS: %s to %s".format(balance.balance, balance.sendAddress))
		js.jquery.JqJsCmds.Hide("sendcoin_amount") &
		js.jquery.JqJsCmds.FadeIn("sent_coins", 300, 600) &
		js.JsCmds.SetHtml("account_balance", <span>{"%.8f BTC".format(user.balance.toFloat - balance.balance.toFloat)}</span>)
	}


	/* snippets */
	def balance = <span id="account_balance">{"%.8f BTC".format(user.balance)}</span>

  def details(xhtml: NodeSeq) : NodeSeq = {
		bind("account", xhtml,
			"email" -> SHtml.ajaxText(user.email, updateEmail(_), "style" -> "width:250px;"),
			"donation" -> SHtml.ajaxText("%.2f".format(user.donatePercent.toFloat), updateDonation(_), "style" -> "width:30px;"),
			"payoutlock" -> SHtml.ajaxCheckbox(user.payoutlock, updatePayoutlock(_)),
			"idlewarning" -> SHtml.ajaxCheckbox(user.idlewarning, updateIdlewarning(_))
		)
	}

	def wallet(xhtml: NodeSeq) : NodeSeq = {
		SHtml.ajaxText(user.wallet, updateWallet(_), "style" -> "width:250px;")
	}

  def sendcoins(xhtml: NodeSeq) : NodeSeq = {
		SHtml.ajaxText("%.2f".format(user.balance), sendcoinsToAccount(_), "style" -> "width:40px;", "id" -> "sendcoin_amount")
	}

}


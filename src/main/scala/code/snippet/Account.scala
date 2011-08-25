/*
 *  Copyright (c) 2011, Franz Bettag <franz@bett.ag>
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * All advertising materials mentioning features or use of this software
 *       must display the following acknowledgement:
 *       This product includes software developed by the Bettag Systems UG
 *       and its contributors.
 *
 *  THIS SOFTWARE IS PROVIDED BY BETTAG SYSTEMS UG ''AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL BETTAG SYSTEMS UG BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

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


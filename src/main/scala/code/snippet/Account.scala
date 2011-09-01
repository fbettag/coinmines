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
import net.liftweb.mapper._
import net.liftweb.http._
import net.liftweb.http.js._
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.js.JsCmd

import model._
import lib._


class Account extends Loggable {

	def user = User.currentUser.open_!

	/* helpers */
	def updateBtcWallet(a: String) = { user.wallet_btc(a).saveWithJsFeedback(".user_wallet_btc") }
	def updateNmcWallet(a: String) = { user.wallet_nmc(a).saveWithJsFeedback(".user_wallet_nmc") }
	def updateSlcWallet(a: String) = { user.wallet_slc(a).saveWithJsFeedback(".user_wallet_slc") }
	def updateEmail(a: String) = { user.email(a).saveWithJsFeedback(".user_details_email") }
	def updateName(a: String) = { user.name(a).saveWithJsFeedback(".user_details_name") }

	def updateDonation(donation: String) = {
		var donatePercent = donation.replaceFirst(",", ".")
		user.donatePercent.setFromString(donatePercent)
		user.saveWithJsFeedback(".user_details_donation")
	}
	
	def updatePayoutlock(bool: Boolean) = {
		user.payoutlock(bool)
		logger.info("User %s - Payout Lock: %s".format(user.id, bool))
		user.saveWithJsFeedback("...")
	}

	def updateIdlewarning(bool: Boolean) = {
		user.idlewarning(bool)
		logger.info("User %s - Idle Warning: %s".format(user.id, bool))
		user.saveWithJsFeedback("...")
	}

	def sendcoinsHandler(network: String): JsCmd = {
		val handler = (SHtml.ajaxButton("sendcoins", () => this.sendcoins(network)) \\ "@onclick").toString.
			replaceAll("&quot;", "'").
			replaceAll("return false;+$", "")
		JsRaw(handler).cmd
	}

	def sendcoins(network: String): JsCmd = {
		var wallet = ""
		var balance = 0.0
		var cmd: CoindCommand = BtcCmd("")
		var cssSelector = ""
		var name = "BTC"
		val fee = 0.02
		var success = false
		val keepPercent: Double = 1.0 - (user.donatePercent / 100).toDouble

		def doIt {
			val res = Coind.call(cmd)
			success = false
			if (res._1) success = AccountBalance.create.
				user(user).network(network).sendAddress(wallet).paid(true).
				txid(res._2).timestamp(DateTimeHelpers.getDate.toDate).balance(balance * (-1)).save
		}

		network match {
			case "bitcoin" =>
				name = "BTC"
				cssSelector = ".user_balance_btc"
				wallet = user.wallet_btc.is
				balance = user.balanceBtcDB
				cmd = BtcCmd("sendtoaddress %s %.8f".format(wallet, (balance * keepPercent) - fee))
				doIt
				balance = user.balanceBtcDB
				user.balance_btc(balance).save
			case "namecoin" =>
				name = "NMC"
				cssSelector = ".user_balance_nmc"
				wallet = user.wallet_nmc.is
				balance = user.balanceNmcDB
				cmd = NmcCmd("sendtoaddress %s %.8f".format(wallet, (balance * keepPercent) - fee))
				doIt
				balance = user.balanceNmcDB
				user.balance_nmc(balance).save
			case "solidcoin" =>
				name = "SLC"
				cssSelector = ".user_balance_slc"
				wallet = user.wallet_slc.is
				balance = user.balanceSlcDB
				cmd = SlcCmd("sendtoaddress %s %.8f".format(wallet, (balance * keepPercent) - fee))
				doIt
				balance = user.balanceSlcDB
				user.balance_slc(balance).save
		}

		if (success) {
			logger.info("SENDING COINS %s: %.8f to %s".format(user.email.is, balance, wallet))
			JsRaw("$('%s').text('%.8f %s')".format(cssSelector, balance, name)).cmd &
			JsRaw("$('%s').effect('highlight', {times: 2}, 400)".format(cssSelector)).cmd
		}
		else	Alert("There was a problem sending your coins.\nPlease check if your wallet is a valid address")

	}


	/* snippets */
	def balance =
		".user_balance_btc [onclick]" #> ({
			if (user.wallet_btc.is == "")		Alert("Sorry, please set a wallet!").toJsCmd.toString
			else if (user.balance_btc.is < 0.1)	Alert("Sorry, not enough funds!").toJsCmd.toString
			else Confirm("Really withdraw all your Bitcoins?", sendcoinsHandler("bitcoin")).toJsCmd.toString } +
			"; return false;") &
		".user_balance_nmc [onclick]" #> ({
			if (user.wallet_nmc.is == "")		Alert("Sorry, please set a wallet!").toJsCmd.toString
			else if (user.balance_nmc.is < 0.1)	Alert("Sorry, not enough funds!").toJsCmd.toString
			else Confirm("Really withdraw all your Namecoins?", sendcoinsHandler("namecoin")).toJsCmd.toString } +
			"; return false;") &
		".user_balance_slc [onclick]" #> ({
			if (user.wallet_slc.is == "")		Alert("Sorry, please set a wallet!").toJsCmd.toString
			else if (user.balance_slc.is < 0.1)	Alert("Sorry, not enough funds!").toJsCmd.toString
			else Confirm("Really withdraw all your Solidcoins?", sendcoinsHandler("solidcoin")).toJsCmd.toString } +
			"; return false;") &
		".user_balance_btc *" #> "%.8f BTC".format(user.balance_btc.toFloat) &
		".user_balance_nmc *" #> "%.8f NMC".format(user.balance_nmc.toFloat) &
		".user_balance_slc *" #> "%.8f SLC".format(user.balance_slc.toFloat)

	def wallet = {
		val btcHandler = (SHtml.ajaxText(user.wallet_btc, updateBtcWallet(_), "style" -> "width:250px;")
			\\ "@onblur").toString.replaceAll("this.value", "\\$('.user_wallet_btc').val()")
		val nmcHandler = (SHtml.ajaxText(user.wallet_nmc, updateNmcWallet(_), "style" -> "width:250px;")
			\\ "@onblur").toString.replaceAll("this.value", "\\$('.user_wallet_nmc').val()")
		val slcHandler = (SHtml.ajaxText(user.wallet_slc, updateSlcWallet(_), "style" -> "width:250px;")
			\\ "@onblur").toString.replaceAll("this.value", "\\$('.user_wallet_slc').val()")

		".user_wallet_btc_btn [onclick]" #> "javascript:%s".format(btcHandler) &
		".user_wallet_nmc_btn [onclick]" #> "javascript:%s".format(nmcHandler) &
		".user_wallet_slc_btn [onclick]" #> "javascript:%s".format(slcHandler) &
		".user_wallet_btc [value]" #> user.wallet_btc.is &
		".user_wallet_nmc [value]" #> user.wallet_nmc.is &
		".user_wallet_slc [value]" #> user.wallet_slc.is
	}

	def details = {
		val nameHandler = (SHtml.ajaxText(user.email, updateName(_), "style" -> "width:250px;") \\ "@onblur").toString.replaceAll("this.value", "\\$('.user_details_name').val()")
		val emailHandler = (SHtml.ajaxText(user.email, updateEmail(_), "style" -> "width:250px;") \\ "@onblur").toString.replaceAll("this.value", "\\$('.user_details_email').val()")
		val donationHandler = (SHtml.ajaxText("%.2f".format(user.donatePercent.toFloat), updateDonation(_), "style" -> "width:30px;") \\ "@onblur").toString.replaceAll("this.value", "\\$('.user_details_donation').val()")
	
		".user_details_name_btn [onclick]" #> "javascript:%s".format(nameHandler) &
		".user_details_email_btn [onclick]" #> "javascript:%s".format(emailHandler) &
		".user_details_donation_btn [onclick]" #> "javascript:%s".format(donationHandler) &
		".user_details_payoutlock" #> SHtml.ajaxCheckbox(user.payoutlock, updatePayoutlock(_)) &
		".user_details_idlewarning" #> SHtml.ajaxCheckbox(user.idlewarning, updateIdlewarning(_)) &
		".user_details_email [value]" #> user.email.is &
		".user_details_name [value]" #> user.name.is &
		".user_details_donation [value]" #> "%.2f".format(user.donatePercent.is.toDouble)

	}

	def payments: CssSel = {
		val balances = AccountBalance.findAll(By(AccountBalance.user, user.id.is), OrderBy(AccountBalance.timestamp, Descending), MaxRows(50))
		if (balances.length == 0) return "*" #> ""

		".payment_row *" #> balances.map(b =>
			".payment_wallet *" #> b.transactionLink &
			".payment_date *" #> b.timestamp.toString &
			".payment_amount *" #> "%.8f %s".format(b.balance.is + 0.02, b.network.is match {
				case "bitcoin" => "BTC"
				case "namecoin" => "NMC"
				case "solidcoin" => "SLC"
			}))
	}

}

